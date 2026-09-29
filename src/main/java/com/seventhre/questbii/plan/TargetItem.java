package com.seventhre.questbii.plan;

import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 同原型（ItemStack 完全相同）物品任务的聚合需求。
 * remaining：扣除玩家 36 格背包存量后的净需求。
 * injectNeeded：扣除 SPB 背包存量后仍需发包注入的数量；reuseSlots 为背包中可直接取出的槽位。
 * 本类只做计数，不修改任何传入的实时 ItemStack。
 */
public class TargetItem {

	public final ItemStack prototype;
	public final List<ItemTask> tasks = new ArrayList<>();
	public long remaining;
	public long injectNeeded;
	public final List<Integer> reuseSlots = new ArrayList<>();

	public TargetItem(ItemStack prototype, long initialRemaining) {
		this.prototype = prototype;
		this.remaining = initialRemaining;
		this.injectNeeded = initialRemaining;
	}

	public boolean matches(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		for (ItemTask task : tasks) {
			if (task.test(stack)) {
				return true;
			}
		}
		return false;
	}

	/** 返回从该栈可抵扣的数量（不修改原栈），available 为该栈尚未被其他目标抵扣的数量。 */
	public long consumePlayerInventory(ItemStack stack, long available) {
		if (remaining <= 0 || available <= 0 || !matches(stack)) {
			return 0;
		}
		long take = Math.min(available, remaining);
		remaining -= take;
		injectNeeded -= take;
		return take;
	}

	/** 返回从该背包槽可抵扣的数量（不修改原栈）；发生抵扣时登记槽位用于后续 quickMove。 */
	public long consumeBackpack(BackpackStack backpackStack, long available) {
		if (injectNeeded <= 0 || available <= 0 || !matches(backpackStack.stack())) {
			return 0;
		}
		long take = Math.min(available, injectNeeded);
		injectNeeded -= take;
		if (!reuseSlots.contains(backpackStack.slotIndex())) {
			reuseSlots.add(backpackStack.slotIndex());
		}
		return take;
	}
}
