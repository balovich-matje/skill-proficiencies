package com.specialities.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.specialities.api.SkillType;
import com.specialities.skills.PlayerSkills;
import com.specialities.skills.SkillManager;
import com.specialities.skills.SkillTypes;
import com.specialities.skills.Tuning;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * Operator commands for setting up a test character without grinding:
 *
 * <pre>
 * /skillprof set &lt;skill&gt; &lt;level&gt; [&lt;targets&gt;]
 * /skillprof max [&lt;targets&gt;]
 * /skillprof reset [&lt;targets&gt;]
 * /skillprof show [&lt;targets&gt;]
 * </pre>
 *
 * <p>The root is named after the mod's published identity (Skill Proficiencies),
 * not the legacy mod id. Every mutation goes through
 * {@link SkillManager#setLevel} — the same path a normal level-up takes — so
 * attribute passives and the client HUD/toast stay consistent with the number.
 *
 * <p>Targets default to the caller; the explicit player argument exists so the
 * commands are usable from the server console and from command blocks, which
 * have no caller of their own.
 */
public final class SkillCommands {
	/** Level 2 ("gamemasters"), the vanilla cheat-command tier. */
	private static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

	private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggest(SkillTypes.all().stream().map(SkillType::id), builder);

	private static final DynamicCommandExceptionType ERROR_UNKNOWN_SKILL = new DynamicCommandExceptionType(
			id -> Component.translatableEscape("commands.specialities.unknown_skill", id));

	private SkillCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
	}

	private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("skillprof")
				.requires(Commands.hasPermission(PERMISSION_CHECK))
				.then(Commands.literal("set")
						.then(Commands.argument("skill", StringArgumentType.word())
								.suggests(SKILL_SUGGESTIONS)
								.then(Commands.argument("level", IntegerArgumentType.integer(0, Tuning.MAX_LEVEL))
										.executes(context -> setLevel(context, self(context)))
										.then(Commands.argument("targets", EntityArgument.players())
												.executes(context -> setLevel(context,
														EntityArgument.getPlayers(context, "targets")))))))
				.then(Commands.literal("max")
						.executes(context -> setAll(context, self(context), Tuning.MAX_LEVEL))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> setAll(context, EntityArgument.getPlayers(context, "targets"),
										Tuning.MAX_LEVEL))))
				.then(Commands.literal("reset")
						.executes(context -> setAll(context, self(context), 0))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> setAll(context, EntityArgument.getPlayers(context, "targets"), 0))))
				.then(Commands.literal("show")
						.executes(context -> show(context, self(context)))
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> show(context, EntityArgument.getPlayers(context, "targets"))))));
	}

	private static Collection<ServerPlayer> self(final CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		return List.of(context.getSource().getPlayerOrException());
	}

	private static int setLevel(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets)
			throws CommandSyntaxException {
		SkillType skill = skill(context);
		int level = IntegerArgumentType.getInteger(context, "level");

		for (ServerPlayer player : targets) {
			SkillManager.setLevel(player, skill, level);
			context.getSource().sendSuccess(() -> Component.translatable("commands.specialities.set",
					skill.displayName(), level, player.getDisplayName()), true);
		}

		return targets.size();
	}

	/** Backs both {@code max} (level = {@link Tuning#MAX_LEVEL}) and {@code reset} (level = 0). */
	private static int setAll(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets,
			final int level) {
		for (ServerPlayer player : targets) {
			for (SkillType skill : SkillTypes.all()) {
				SkillManager.setLevel(player, skill, level);
			}

			context.getSource().sendSuccess(() -> Component.translatable("commands.specialities.set_all",
					level, player.getDisplayName()), true);
		}

		return targets.size();
	}

	private static int show(final CommandContext<CommandSourceStack> context, final Collection<ServerPlayer> targets) {
		for (ServerPlayer player : targets) {
			PlayerSkills skills = SkillManager.get(player);
			context.getSource().sendSuccess(
					() -> Component.translatable("commands.specialities.show.header", player.getDisplayName()), false);

			for (SkillType skill : SkillTypes.all()) {
				context.getSource().sendSuccess(() -> Component.translatable("commands.specialities.show.entry",
						skill.displayName(), skills.level(skill), skills.totalXp(skill)), false);
			}
		}

		return targets.size();
	}

	/** Skills are looked up by id so external skills work exactly like built-ins. */
	private static SkillType skill(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String id = StringArgumentType.getString(context, "skill");
		SkillType skill = SkillTypes.byId(id);

		if (skill == null) {
			throw ERROR_UNKNOWN_SKILL.create(id);
		}

		return skill;
	}
}
