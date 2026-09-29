package com.seventhre.questbii.mixin;

import com.seventhre.questbii.client.BatchActionBar;
import com.seventhre.questbii.client.QuestSelection;
import dev.ftb.mods.ftbquests.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 挂载批量操作栏（addWidgets/alignWidgets），并在切章节、打开任务详情时清空选择集。
 */
@Mixin(value = QuestScreen.class, remap = false)
public abstract class QuestScreenMixin {

	@Inject(method = "addWidgets", at = @At("TAIL"), remap = false)
	private void questbii$addBatchBar(CallbackInfo ci) {
		BatchActionBar.attach((QuestScreen) (Object) this);
	}

	@Inject(method = "alignWidgets", at = @At("TAIL"), remap = false)
	private void questbii$layoutBatchBar(CallbackInfo ci) {
		BatchActionBar.layout((QuestScreen) (Object) this);
	}

	@Inject(method = "selectChapter", at = @At("TAIL"), remap = false)
	private void questbii$clearOnChapterChange(Chapter chapter, CallbackInfo ci) {
		QuestSelection.clear();
	}

	@Inject(method = "viewQuest", at = @At("TAIL"), remap = false)
	private void questbii$clearOnViewQuest(Quest quest, CallbackInfo ci) {
		QuestSelection.clear();
	}
}
