package com.seventhre.questbii.plan;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 汇总当前任务中所有未完成、可支持的 ItemTask：
 * 跳过非物品任务 / onlyFromCrafting / taskScreenOnly / 已完成 / 任务链未解锁；
 * 同原型物品合并需求，并用玩家 36 格背包存量做第一次抵扣（与 FTBQ 检测范围一致，不含盔甲/副手）。
 */
public final class QuestNeedPlanner {

	private QuestNeedPlanner() {
	}

	public static QuestPlan plan(Quest quest, TeamData teamData, Inventory playerInventory) {
		QuestPlan plan = new QuestPlan();
		if (quest == null || teamData == null || !teamData.canStartTasks(quest)) {
			return plan;
		}

		// 顺序任务（非编辑模式）：与原生 ViewQuestPanel 一致，处理到第一个未完成任务即截断
		boolean sequential = quest.getRequireSequentialTasks() && !isEditMode();
		for (Task task : quest.getTasksAsList()) {
			boolean completed = teamData.isCompleted(task);
			collectTask(plan, teamData, task);
			if (sequential && !completed) {
				break;
			}
		}

		if (playerInventory != null) {
			List<ItemStack> items = playerInventory.items;
			for (ItemStack stack : items) {
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				long available = stack.getCount();
				for (TargetItem target : plan.targets) {
					if (available <= 0) {
						break;
					}
					available -= target.consumePlayerInventory(stack, available);
				}
			}
		}

		return plan;
	}

	private static void collectTask(QuestPlan plan, TeamData teamData, Task task) {
		if (teamData.isCompleted(task)) {
			return;
		}
		if (!(task instanceof ItemTask itemTask)) {
			plan.skippedNonItem++;
			return;
		}
		if (itemTask.isTaskScreenOnly()) {
			plan.skippedScreenOnly++;
			return;
		}
		if (itemTask.isOnlyFromCrafting()) {
			plan.skippedCrafting++;
			return;
		}
		ItemStack prototype = itemTask.getItemStack();
		if (prototype == null || prototype.isEmpty()) {
			plan.skippedInvalid++;
			return;
		}
		long need = Math.max(0L, itemTask.getMaxProgress() - teamData.getProgress(task));
		if (need <= 0) {
			return;
		}

		TargetItem target = findOrCreateTarget(plan, prototype);
		target.tasks.add(itemTask);
		target.remaining += need;
		target.injectNeeded += need;
		if (itemTask.consumesResources()) {
			plan.consumeTaskIds.add(itemTask.id);
		} else {
			plan.detectionTaskIds.add(itemTask.id);
		}
	}

	private static boolean isEditMode() {
		return dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE != null
			&& dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE.canEdit();
	}

	private static TargetItem findOrCreateTarget(QuestPlan plan, ItemStack prototype) {
		for (TargetItem target : plan.targets) {
			if (ItemStack.isSameItemSameTags(target.prototype, prototype)) {
				return target;
			}
		}
		TargetItem target = new TargetItem(prototype.copy(), 0L);
		plan.targets.add(target);
		return target;
	}
}
