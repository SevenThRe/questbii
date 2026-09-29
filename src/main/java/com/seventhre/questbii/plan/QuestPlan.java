package com.seventhre.questbii.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次"任务速达"点击的完整规划：
 * 点击时按玩家背包存量抵扣；背包打开后再按背包存量抵扣并登记可复用槽位。
 */
public class QuestPlan {

	public final List<TargetItem> targets = new ArrayList<>();
	/** 需要发 SubmitTaskMessage 的消耗型任务 id */
	public final List<Long> consumeTaskIds = new ArrayList<>();
	/** 靠 FTBQ 背包变更检测自动完成的任务 id */
	public final List<Long> detectionTaskIds = new ArrayList<>();

	public int skippedNonItem;
	public int skippedCrafting;
	public int skippedScreenOnly;
	public int skippedInvalid;

	public int supportedTaskCount() {
		return consumeTaskIds.size() + detectionTaskIds.size();
	}

	public boolean hasSupportedTasks() {
		return supportedTaskCount() > 0;
	}

	/** 玩家背包存量抵扣后仍有缺口，需要打开 SPB 背包。 */
	public boolean needsBackpack() {
		for (TargetItem target : targets) {
			if (target.remaining > 0) {
				return true;
			}
		}
		return false;
	}

	/** 背包打开后：用背包内已有物品抵扣注入量，并记录需要 quickMove 的槽位。 */
	public void applyBackpackStacks(List<BackpackStack> stacks) {
		for (BackpackStack stack : stacks) {
			long available = stack.stack().getCount();
			for (TargetItem target : targets) {
				if (available <= 0) {
					break;
				}
				available -= target.consumeBackpack(stack, available);
			}
		}
	}

	/** 背包抵扣后仍无空槽注入或注入无法完成时，是否还有未满足缺口。 */
	public long totalInjectNeeded() {
		long sum = 0;
		for (TargetItem target : targets) {
			sum += Math.max(0L, target.injectNeeded);
		}
		return sum;
	}

	public long totalReuseSlots() {
		return targets.stream().mapToLong(t -> t.reuseSlots.size()).sum();
	}
}
