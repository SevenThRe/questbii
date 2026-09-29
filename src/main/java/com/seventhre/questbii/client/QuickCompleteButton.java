package com.seventhre.questbii.client;

import com.seventhre.questbii.QuestBii;
import com.seventhre.questbii.mixin.ViewQuestPanelAccessor;
import com.seventhre.questbii.plan.QuestNeedPlanner;
import com.seventhre.questbii.plan.QuestPlan;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 任务详情标题栏"一键完成"按钮：图标为 Sophisticated 背包物品图标，
 * 与关闭/图钉/链接按钮同行对齐（iconSize = min(16, titleField.height + 2)，x = w - iconSize*4 - 6）。
 */
public class QuickCompleteButton extends SimpleTextButton {

	private static Icon cachedBackpackIcon;

	private final Quest quest;
	@Nullable
	private final QuestPlan plan;

	public QuickCompleteButton(Panel panel, Quest quest) {
		super(panel, Component.translatable("text.questbii.quick_complete"), backpackIcon());
		this.quest = quest;

		QuestPlan p = null;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE != null) {
				p = QuestNeedPlanner.plan(quest,
						dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE.selfTeamData,
						mc.player.getInventory());
			}
		} catch (Throwable t) {
			QuestBii.LOGGER.error("[questbii] failed to plan quest {}", quest.id, t);
		}
		this.plan = p;

		int titleHeight = 14;
		if (panel instanceof ViewQuestPanelAccessor accessor && accessor.questbii$getTitleField() != null) {
			titleHeight = accessor.questbii$getTitleField().height;
		}
		int iconSize = Math.min(16, Math.max(12, titleHeight + 2));
		setPosAndSize(panel.width - iconSize * 4 - 6, 4, iconSize, iconSize);
	}

	/** 背包物品图标（缓存）；注册表缺失时回退到自带 PNG。 */
	static Icon backpackIcon() {
		if (cachedBackpackIcon == null) {
			Icon icon = null;
			try {
				Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sophisticatedbackpacks", "backpack"));
				if (item != null && item != Items.AIR) {
					icon = ItemIcon.getItemIcon(new ItemStack(item));
				}
			} catch (Throwable ignored) {
			}
			cachedBackpackIcon = icon != null ? icon : Icon.getIcon("questbii:textures/gui/quick_complete.png");
		}
		return cachedBackpackIcon;
	}

	@Override
	public boolean shouldDraw() {
		return plan != null && plan.hasSupportedTasks();
	}

	@Override
	public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
		Icon bg = isMouseOver()
				? dev.ftb.mods.ftbquests.quest.theme.property.ThemeProperties.WIDGET_BACKGROUND.get()
				: dev.ftb.mods.ftbquests.quest.theme.property.ThemeProperties.WIDGET_BACKGROUND.get().withAlpha(180);
		bg.draw(graphics, x, y, w, h);
	}

	@Override
	public void drawIcon(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
		icon.draw(graphics, x + 1, y + 1, w - 2, h - 2);
	}

	@Override
	public void addMouseOverText(TooltipList list) {
		list.add(Component.translatable("text.questbii.quick_complete").withStyle(ChatFormatting.GOLD));
		list.add(Component.translatable("text.questbii.quick_complete.hint").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public void onClicked(MouseButton button) {
		if (button.isLeft()) {
			playClickSound();
			QuickCompleteController.start(quest);
		}
	}
}
