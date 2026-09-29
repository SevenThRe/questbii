package com.seventhre.questbii.mixin;

import com.seventhre.questbii.client.QuickCompleteButton;
import dev.ftb.mods.ftbquests.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在任务详情面板控件重建完成后追加"速达"标题栏按钮。
 * 仅注入 TAIL，不修改原版 addWidgets 的任何逻辑。
 */
// 目标为 FTBQ 模组自有类/方法（生产环境不参与 SRG 重映射），remap=false 避免 AP 查找 MC 映射
@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelMixin {

	@Inject(method = "addWidgets", at = @At("TAIL"), remap = false)
	private void questbii$addQuickCompleteButton(CallbackInfo ci) {
		ViewQuestPanel panel = (ViewQuestPanel) (Object) this;
		Quest quest = panel.getViewedQuest();
		if (quest != null) {
			panel.add(new QuickCompleteButton(panel, quest));
		}
	}
}
