package com.seventhre.questbii.client;

import com.seventhre.questbii.QuestBii;
import com.seventhre.questbii.plan.BackpackStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler;
import net.p3pp3rf1y.sophisticatedcore.compat.jei.SetGhostSlotMessage;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sophisticated Backpacks / Sophisticated Core 通信网关。
 * 全部为纯客户端操作：开包、读真实存储槽、ghost slot 发包写入、Shift+点击取出、关包。
 */
public final class BackpackGateway {

	private BackpackGateway() {
	}

	/** 发包请求服务端打开玩家身上的第一个 SPB 背包。 */
	public static void openBackpack() {
		try {
			SBPPacketHandler.INSTANCE.sendToServer(new BackpackOpenMessage());
		} catch (Throwable t) {
			QuestBii.LOGGER.error("Failed to send BackpackOpenMessage", t);
		}
	}

	/** 当前打开的容器菜单是否为 SC 存储容器（SPB 背包界面）。 */
	public static boolean isStorageMenuOpen() {
		return getStorageMenu() != null;
	}

	public static StorageContainerMenuBase<?> getStorageMenu() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.containerMenu instanceof StorageContainerMenuBase<?>) {
			return (StorageContainerMenuBase<?>) mc.player.containerMenu;
		}
		return null;
	}

	/** 读取背包真实存储槽中的现有物品（用于复用阶段）。 */
	public static List<BackpackStack> collectBackpackStacks() {
		StorageContainerMenuBase<?> menu = getStorageMenu();
		if (menu == null) {
			return Collections.emptyList();
		}
		List<BackpackStack> result = new ArrayList<>();
		for (Slot slot : menu.realInventorySlots) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				result.add(new BackpackStack(slot.index, stack.copy()));
			}
		}
		return result;
	}

	/** 背包真实存储槽中的空槽 menu slot id 列表。 */
	public static List<Integer> findEmptySlotIds() {
		StorageContainerMenuBase<?> menu = getStorageMenu();
		if (menu == null) {
			return Collections.emptyList();
		}
		List<Integer> result = new ArrayList<>();
		for (Slot slot : menu.realInventorySlots) {
			if (slot.getItem().isEmpty()) {
				result.add(slot.index);
			}
		}
		return result;
	}

	/** 通过 JEI ghost slot 通道向指定菜单槽位写入真实物品（服务端不校验槽位类型）。 */
	public static void sendSetGhostSlot(ItemStack stack, int slotNumber) {
		try {
			net.p3pp3rf1y.sophisticatedcore.network.PacketHandler.INSTANCE
				.sendToServer(new SetGhostSlotMessage(stack.copy(), slotNumber));
		} catch (Throwable t) {
			QuestBii.LOGGER.error("Failed to send SetGhostSlotMessage to slot {}", slotNumber, t);
		}
	}

	/** 客户端模拟 Shift+左键，把背包槽位物品快速移动到玩家背包。 */
	public static void quickMove(int slotIndex) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null) {
			return;
		}
		try {
			mc.gameMode.handleInventoryMouseClick(
				mc.player.containerMenu.containerId, slotIndex, 0, ClickType.QUICK_MOVE, mc.player);
		} catch (Throwable t) {
			QuestBii.LOGGER.error("Failed to quick-move slot {}", slotIndex, t);
		}
	}

	/** 关闭背包容器；仅在当前确实显示容器界面时才复位 screen，避免静默模式误关任务书。 */
	public static void closeContainer() {
		Minecraft mc = Minecraft.getInstance();
		try {
			if (mc.player != null && mc.player.containerMenu instanceof StorageContainerMenuBase<?>) {
				mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
			}
		} catch (Throwable t) {
			QuestBii.LOGGER.error("[questbii] closeContainer error", t);
		}
		if (mc.screen instanceof AbstractContainerScreen) {
			mc.setScreen(null);
		}
	}
}
