package com.seventhre.questbii.mixin;

import dev.ftb.mods.ftbquests.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.quest.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuestButton.class, remap = false)
public interface QuestButtonAccessor {

	@Accessor("quest")
	Quest questbii$getQuest();
}
