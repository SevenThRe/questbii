package com.seventhre.questbii.plan;

import net.minecraft.world.item.ItemStack;

/** SPB 背包真实存储槽中一个已有物品（槽号用 menu slot index）。 */
public record BackpackStack(int slotIndex, ItemStack stack) {
}
