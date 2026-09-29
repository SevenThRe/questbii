package com.seventhre.questbii.mixin;

import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ViewQuestPanel.class, remap = false)
public interface ViewQuestPanelAccessor {

	@Accessor("titleField")
	TextField questbii$getTitleField();
}
