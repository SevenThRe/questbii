package com.seventhre.questbii.mixin;

import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuestPanel.class, remap = false)
public interface QuestPanelAccessor {

	@Accessor("questScreen")
	QuestScreen questbii$getQuestScreen();
}
