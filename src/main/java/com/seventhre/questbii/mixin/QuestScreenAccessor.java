package com.seventhre.questbii.mixin;

import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuestScreen.class, remap = false)
public interface QuestScreenAccessor {

	@Accessor("selectedChapter")
	Chapter questbii$getSelectedChapter();

	@Accessor("movingObjects")
	boolean questbii$isMovingObjects();

	@Accessor("chapterPanel")
	ChapterPanel questbii$getChapterPanel();
}
