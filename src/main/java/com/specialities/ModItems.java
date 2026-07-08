package com.specialities;

import com.specialities.items.SkillBookItem;
import com.specialities.skills.Skill;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item MINING_KNOWLEDGE_25 = registerBook(Skill.MINING, 25);
	public static final Item MINING_KNOWLEDGE_100 = registerBook(Skill.MINING, 100);
	public static final Item WOODCUTTING_KNOWLEDGE_25 = registerBook(Skill.WOODCUTTING, 25);
	public static final Item WOODCUTTING_KNOWLEDGE_100 = registerBook(Skill.WOODCUTTING, 100);
	public static final Item COMBAT_KNOWLEDGE_25 = registerBook(Skill.COMBAT, 25);
	public static final Item COMBAT_KNOWLEDGE_100 = registerBook(Skill.COMBAT, 100);
	public static final Item ARMS_MASTERY_KNOWLEDGE_25 = registerBook(Skill.ARMS_MASTERY, 25);
	public static final Item ARMS_MASTERY_KNOWLEDGE_100 = registerBook(Skill.ARMS_MASTERY, 100);
	public static final Item ARCHERY_KNOWLEDGE_25 = registerBook(Skill.ARCHERY, 25);
	public static final Item ARCHERY_KNOWLEDGE_100 = registerBook(Skill.ARCHERY, 100);
	public static final Item HARVESTING_KNOWLEDGE_25 = registerBook(Skill.HARVESTING, 25);
	public static final Item HARVESTING_KNOWLEDGE_100 = registerBook(Skill.HARVESTING, 100);
	public static final Item EXCAVATION_KNOWLEDGE_25 = registerBook(Skill.EXCAVATION, 25);
	public static final Item EXCAVATION_KNOWLEDGE_100 = registerBook(Skill.EXCAVATION, 100);
	public static final Item FISHING_KNOWLEDGE_25 = registerBook(Skill.FISHING, 25);
	public static final Item FISHING_KNOWLEDGE_100 = registerBook(Skill.FISHING, 100);
	public static final Item DEFENCE_KNOWLEDGE_25 = registerBook(Skill.DEFENCE, 25);
	public static final Item DEFENCE_KNOWLEDGE_100 = registerBook(Skill.DEFENCE, 100);
	public static final Item ACROBATICS_KNOWLEDGE_25 = registerBook(Skill.ACROBATICS, 25);
	public static final Item ACROBATICS_KNOWLEDGE_100 = registerBook(Skill.ACROBATICS, 100);
	public static final Item ATHLETICS_KNOWLEDGE_25 = registerBook(Skill.ATHLETICS, 25);
	public static final Item ATHLETICS_KNOWLEDGE_100 = registerBook(Skill.ATHLETICS, 100);
	public static final Item SNEAKING_KNOWLEDGE_25 = registerBook(Skill.SNEAKING, 25);
	public static final Item SNEAKING_KNOWLEDGE_100 = registerBook(Skill.SNEAKING, 100);

	private ModItems() {
	}

	private static Item registerBook(final Skill skill, final int levels) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Specialities.id(skill.id().replace('_', '-') + "-knowledge-" + levels));
		Item item = new SkillBookItem(skill, levels, new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(MINING_KNOWLEDGE_25);
			output.accept(MINING_KNOWLEDGE_100);
			output.accept(WOODCUTTING_KNOWLEDGE_25);
			output.accept(WOODCUTTING_KNOWLEDGE_100);
			output.accept(COMBAT_KNOWLEDGE_25);
			output.accept(COMBAT_KNOWLEDGE_100);
			output.accept(ARMS_MASTERY_KNOWLEDGE_25);
			output.accept(ARMS_MASTERY_KNOWLEDGE_100);
			output.accept(ARCHERY_KNOWLEDGE_25);
			output.accept(ARCHERY_KNOWLEDGE_100);
			output.accept(HARVESTING_KNOWLEDGE_25);
			output.accept(HARVESTING_KNOWLEDGE_100);
			output.accept(EXCAVATION_KNOWLEDGE_25);
			output.accept(EXCAVATION_KNOWLEDGE_100);
			output.accept(FISHING_KNOWLEDGE_25);
			output.accept(FISHING_KNOWLEDGE_100);
			output.accept(DEFENCE_KNOWLEDGE_25);
			output.accept(DEFENCE_KNOWLEDGE_100);
			output.accept(ACROBATICS_KNOWLEDGE_25);
			output.accept(ACROBATICS_KNOWLEDGE_100);
			output.accept(ATHLETICS_KNOWLEDGE_25);
			output.accept(ATHLETICS_KNOWLEDGE_100);
			output.accept(SNEAKING_KNOWLEDGE_25);
			output.accept(SNEAKING_KNOWLEDGE_100);
		});
	}
}
