package com.seventhre.questbii.client;

import dev.ftb.mods.ftbquests.quest.Quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Shift 框选的批量选择集：任务按可见顺序去重保存。
 * 纯客户端状态，切章节/打开详情/Esc/开始批量时清空。
 */
public final class QuestSelection {

	private static final LinkedHashSet<Quest> SELECTED = new LinkedHashSet<>();
	private static boolean selecting;
	private static int startX;
	private static int startY;

	private QuestSelection() {
	}

	public static void begin(int mouseX, int mouseY) {
		selecting = true;
		startX = mouseX;
		startY = mouseY;
	}

	public static void endDrag() {
		selecting = false;
	}

	public static boolean isSelecting() {
		return selecting;
	}

	public static int getStartX() {
		return startX;
	}

	public static int getStartY() {
		return startY;
	}

	public static boolean isEmpty() {
		return SELECTED.isEmpty();
	}

	public static int size() {
		return SELECTED.size();
	}

	public static boolean contains(Quest quest) {
		return quest != null && SELECTED.contains(quest);
	}

	public static void replaceAll(Collection<Quest> quests) {
		SELECTED.clear();
		SELECTED.addAll(quests);
	}

	public static void clear() {
		SELECTED.clear();
		selecting = false;
	}

	public static List<Quest> copyAndClear() {
		List<Quest> copy = new ArrayList<>(SELECTED);
		clear();
		return copy;
	}
}
