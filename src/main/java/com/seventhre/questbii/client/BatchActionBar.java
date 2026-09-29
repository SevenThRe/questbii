package com.seventhre.questbii.client;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 任务屏幕底部的批量操作栏：选择集非空时显示"开始批量速达 (N)"与"清除选择"。
 * 由 QuestScreenMixin 在 addWidgets/alignWidgets 时挂载与布局。
 */
public final class BatchActionBar {

	private static SimpleButton startButton;
	private static SimpleButton clearButton;

	private BatchActionBar() {
	}

	public static void attach(QuestScreen screen) {
		startButton = new SimpleButton(screen,
				Component.translatable("text.questbii.batch.start", QuestSelection.size()),
				QuickCompleteButton.backpackIcon(),
				(widget, button) -> {
					List<Quest> selected = QuestSelection.copyAndClear();
					if (!selected.isEmpty()) {
						QuickCompleteController.startBatch(selected);
					}
				}) {
			@Override
			public boolean shouldDraw() {
				return !QuestSelection.isEmpty();
			}
		};
		clearButton = new SimpleButton(screen,
				Component.translatable("text.questbii.batch.clear"),
				dev.ftb.mods.ftblibrary.icon.Color4I.empty(),
				(widget, button) -> QuestSelection.clear()) {
			@Override
			public boolean shouldDraw() {
				return !QuestSelection.isEmpty();
			}
		};
		screen.add(startButton);
		screen.add(clearButton);
	}

	public static void layout(QuestScreen screen) {
		if (startButton == null || clearButton == null) {
			return;
		}
		startButton.setTitle(Component.translatable("text.questbii.batch.start", QuestSelection.size()));
		Font font = Minecraft.getInstance().font;
		int w1 = Math.max(70, font.width(startButton.getTitle()) + 26);
		int w2 = Math.max(50, font.width(clearButton.getTitle()) + 16);
		int totalWidth = w1 + 6 + w2;
		int x = (screen.width - totalWidth) / 2;
		int y = screen.height - 26;
		startButton.setPosAndSize(x, y, w1, 20);
		clearButton.setPosAndSize(x + w1 + 6, y, w2, 20);
	}
}
