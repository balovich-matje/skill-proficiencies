package com.specialities.api;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * A skill as the engine sees it. The fifteen built-ins implement this as the
 * {@link com.specialities.skills.Skill} enum; other mods contribute instances
 * through the {@code specialities:skills} entrypoint ({@link SkillsEntrypoint})
 * and the engine treats them identically: same XP curve, same persistence
 * (keyed by {@link #id()}), same HUD bar, toast and skills-screen row.
 *
 * <p>Specialities knows nothing about what an external skill means — it only
 * stores XP against the id and renders what the skill describes about itself.
 */
public interface SkillType {
	/** Stable lowercase identifier — the persistence and wire key. */
	String id();

	/** ARGB accent colour for the HUD bar, toast and screen row. */
	int color();

	/** Icon item, for item-rendered contexts (the level-up toast). */
	Item icon();

	/**
	 * A flat <em>item</em> texture present in the item atlas, e.g.
	 * {@code yourmod:item/your_item}. Item textures only: the HUD draws icons
	 * translucent, which 3D block icons cannot do.
	 */
	Identifier iconTexture();

	default Component displayName() {
		return Component.translatable("skill.specialities." + this.id());
	}

	/**
	 * Bonus lines for the skills screen's hover tooltip at {@code level}.
	 * Built-ins keep theirs inside the screen; external skills describe their
	 * own. The XP-source panel needs no method — define the lang key
	 * {@code screen.specialities.skills.source.<id>} instead.
	 */
	default List<Component> screenLines(final int level) {
		return List.of();
	}
}
