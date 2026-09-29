package com.seventhre.questbii.mixin;

import com.seventhre.questbii.client.QuestSelection;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shift+左键框选多个任务：HEAD 拦截按下/松开，TAIL 叠加绘制选区矩形与选中高亮。
 * 仅在非编辑、非查看详情状态生效，不影响原生平移与编辑框选。
 */
@Mixin(value = QuestPanel.class, remap = false)
public abstract class QuestPanelMixin {

	@Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
	private void questbii$beginBoxSelect(MouseButton button, CallbackInfoReturnable<Boolean> cir) {
		if (!button.isLeft() || !Screen.hasShiftDown()) {
			return;
		}
		QuestPanel self = (QuestPanel) (Object) this;
		QuestScreen screen = ((QuestPanelAccessor) self).questbii$getQuestScreen();
		if (screen == null) {
			return;
		}
		QuestScreenAccessor acc = (QuestScreenAccessor) screen;
		if (acc.questbii$getSelectedChapter() == null || screen.isViewingQuest() || acc.questbii$isMovingObjects()) {
			return;
		}
		if (acc.questbii$getChapterPanel().isMouseOver() || !self.isMouseOver()) {
			return;
		}
		QuestSelection.begin(self.getMouseX(), self.getMouseY());
		cir.setReturnValue(true);
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"))
	private void questbii$finishBoxSelect(MouseButton button, CallbackInfo ci) {
		if (!QuestSelection.isSelecting() || !button.isLeft()) {
			return;
		}
		QuestPanel self = (QuestPanel) (Object) this;
		int curX = self.getMouseX();
		int curY = self.getMouseY();
		QuestSelection.endDrag();
		int x1 = Math.min(QuestSelection.getStartX(), curX);
		int y1 = Math.min(QuestSelection.getStartY(), curY);
		Rect2i rect = new Rect2i(x1, y1, Math.max(x1, curX) - x1, Math.max(y1, curY) - y1);
		double sx = self.getScrollX();
		double sy = self.getScrollY();
		List<Quest> found = new ArrayList<>();
		for (Widget widget : self.getWidgets()) {
			if (widget instanceof QuestButtonAccessor qb) {
				int cx = (int) (widget.getX() - sx) + widget.width / 2;
				int cy = (int) (widget.getY() - sy) + widget.height / 2;
				if (rect.contains(cx, cy)) {
					Quest quest = qb.questbii$getQuest();
					if (quest != null) {
						found.add(quest);
					}
				}
			}
		}
		QuestSelection.replaceAll(found);
	}

	@Inject(method = "draw", at = @At("TAIL"))
	private void questbii$drawSelection(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
		QuestPanel self = (QuestPanel) (Object) this;
		if (QuestSelection.isSelecting()) {
			int x1 = Math.min(QuestSelection.getStartX(), self.getMouseX());
			int y1 = Math.min(QuestSelection.getStartY(), self.getMouseY());
			int bw = Math.abs(QuestSelection.getStartX() - self.getMouseX());
			int bh = Math.abs(QuestSelection.getStartY() - self.getMouseY());
			if (bw > 0 && bh > 0) {
				Color4I.WHITE.withAlpha(20).draw(graphics, x1, y1, bw, bh);
				GuiHelper.drawHollowRect(graphics, x1, y1, bw, bh, Color4I.WHITE.withAlpha(120), false);
			}
		}
		if (!QuestSelection.isEmpty()) {
			double sx = self.getScrollX();
			double sy = self.getScrollY();
			Color4I highlight = Color4I.rgb(0xFFCC33);
			for (Widget widget : self.getWidgets()) {
				if (widget instanceof QuestButtonAccessor qb && QuestSelection.contains(qb.questbii$getQuest())) {
					int bx = (int) (widget.getX() - sx) - 1;
					int by = (int) (widget.getY() - sy) - 1;
					GuiHelper.drawHollowRect(graphics, bx, by, widget.width + 2, widget.height + 2, highlight, false);
				}
			}
		}
	}
}
