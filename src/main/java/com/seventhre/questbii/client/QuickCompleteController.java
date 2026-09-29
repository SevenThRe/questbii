package com.seventhre.questbii.client;

import com.seventhre.questbii.QuestBii;
import com.seventhre.questbii.plan.BackpackStack;
import com.seventhre.questbii.plan.QuestNeedPlanner;
import com.seventhre.questbii.plan.QuestPlan;
import com.seventhre.questbii.plan.TargetItem;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务速达控制器：单任务是批量（队列长度为 1）的特例。
 * 批量时按依赖拓扑排序后逐任务迭代 WAIT_UNLOCK→PLAN→OPEN→REUSE→…→VERIFY 状态机，
 * WAIT_UNLOCK 门等待链式任务的前置解锁与对象级完成同步后再构建计划发包；整个批次最多开一次背包；
 * 单任务失败记录原因并跳过继续，容器丢失/流程异常等致命错误中止整个批次。
 * 批次结束在聊天栏输出汇总，并刷新当前任务书界面（不重新打开）。
 */
public final class QuickCompleteController {

	public static final QuickCompleteController INSTANCE = new QuickCompleteController();

	// 时序参数：固定等待改为"槽位状态确认 + 短下限 + 有界超时"，避免无意义的长延迟
	private static final int OPEN_TIMEOUT = 60;            // 3s 未等到开包回包则失败
	private static final int OPEN_RESEND_INTERVAL = 12;   // 等待期间重发开包包，防丢包导致的"点了没反应"
	private static final int SETTLE_MIN_TICKS = 3;        // 复用移物后至少跨过 3 tick（含服务端回包）
	private static final int REUSE_SETTLE_TIMEOUT = 30;
	private static final int SYNC_MIN_TICKS = 2;
	private static final int SYNC_TIMEOUT = 30;
	private static final int MOVE_SETTLE_MIN_TICKS = 2;
	private static final int MOVE_SETTLE_TIMEOUT = 30;
	private static final int DETECT_WAIT_TICKS = 4;
	private static final int VERIFY_INTERVAL = 2;
	private static final int VERIFY_TIMEOUT = 120;
	private static final int QUEST_DEADLINE = 240;        // 单任务总时限 12s，防止任何环节永久卡住
	private static final int REUSE_PER_TICK = 6;
	private static final int INJECT_PER_TICK = 8;
	private static final int WAIT_UNLOCK_TIMEOUT = 60;   // 链式任务：等前置解锁+对象级同步最长 3s
	private static final int PREREQ_FASTFAIL_TICKS = 20; // 前置确定不可达时 1s 内快速失败，避免长时间沉默

	private boolean running;
	private Quest quest;
	private QuestPlan plan;
	private Phase phase = Phase.IDLE;
	private int phaseTick;
	private int questTick;
	// 刚被本批次验证完成的任务 id：其"任务对象完成"的服务端同步可能晚于任务级完成 1~2 tick
	private long lastCompletedQuestId = -1L;
	// 批次级菜单信任链：batchMenuId 记录本批次内验证过的新开菜单 containerId；
	// 批次开始前就已打开/来源不明的菜单，服务端可能早已不是同一容器（SetGhostSlot 被静默丢弃），
	// 必须强制重开换新菜单（新菜单 containerId 必然变化）再做全量内容同步
	private int batchMenuId = -1;
	private int staleMenuId = -1;
	private int openRetries;

	private final Set<Integer> reuseSlotIds = new HashSet<>();
	private final Deque<Integer> reuseQueue = new ArrayDeque<>();
	private final Deque<Injection> injectQueue = new ArrayDeque<>();
	private final List<Integer> injectedSlots = new ArrayList<>();
	private final List<Integer> moveOutSlots = new ArrayList<>();
	private final Deque<Long> submitQueue = new ArrayDeque<>();

	// 批次状态
	private final Deque<Quest> questQueue = new ArrayDeque<>();
	private int batchTotal;
	private int batchIndex;
	private int successCount;
	private final List<Component> failures = new ArrayList<>();
	private boolean backpackFailed;

	private enum Phase {
		IDLE, WAIT_UNLOCK, OPEN, REUSE, REUSE_SETTLE, INJECT_PREP, INJECT, WAIT_SYNC, MOVE_OUT, MOVE_SETTLE, DETECT_WAIT, SUBMIT, VERIFY
	}

	private record Injection(ItemStack stack, int slotId) {
	}

	private static final class FlowAbortedException extends RuntimeException {
	}

	private QuickCompleteController() {
	}

	public static boolean isRunning() {
		return INSTANCE.running;
	}

	public static void start(Quest quest) {
		INSTANCE.startBatchFlow(List.of(quest));
	}

	public static void startBatch(List<Quest> quests) {
		INSTANCE.startBatchFlow(quests);
	}

	private void startBatchFlow(List<Quest> quests) {
		Minecraft mc = Minecraft.getInstance();
		if (running) {
			action(mc, "text.questbii.msg.running");
			return;
		}
		if (mc.player == null || ClientQuestFile.INSTANCE == null || quests == null || quests.isEmpty()) {
			return;
		}
		try {
			running = true;
			questQueue.clear();
			failures.clear();
			List<Quest> ordered = orderTopologically(quests);
			questQueue.addAll(ordered);
			batchTotal = ordered.size();
			batchIndex = 0;
			successCount = 0;
			backpackFailed = false;
			phase = Phase.IDLE;
			phaseTick = 0;
			questTick = 0;
			QuestBii.LOGGER.info("[questbii] batch started: {} quest(s)", batchTotal);
			startNextQuest(mc);
		} catch (Throwable t) {
			QuestBii.LOGGER.error("[questbii] failed to start batch", t);
			reset();
			action(mc, "text.questbii.msg.flow_error");
		}
	}

	/**
	 * 推进到队列中的下一个任务：只做状态复位并进入 WAIT_UNLOCK 门。
	 * 链式任务的前一个任务刚完成时，"任务对象完成"的服务端同步可能晚 1~2 tick，
	 * 必须等 teamData.canStartTasks 确认解锁后再构建计划、发送任务包。
	 */
	private void startNextQuest(Minecraft mc) {
		if (questQueue.isEmpty()) {
			finishBatch(mc);
			return;
		}
		Quest next = questQueue.pollFirst();
		batchIndex++;
		this.quest = next;
		this.plan = null;
		this.phaseTick = 0;
		this.questTick = 0;
		openRetries = 0;
		clearPerQuestState();
		action(mc, "text.questbii.msg.batch_progress", batchIndex, batchTotal, next.getTitle());
		phase = Phase.WAIT_UNLOCK;
	}

	/** WAIT_UNLOCK 通过后：构建计划并启动执行流（需要背包走 OPEN，纯检测走 DETECT_WAIT）。 */
	private void beginQuestFlow(Minecraft mc) {
		TeamData teamData = ClientQuestFile.INSTANCE == null ? null : ClientQuestFile.INSTANCE.selfTeamData;
		QuestPlan freshPlan = QuestNeedPlanner.plan(quest, teamData, mc.player.getInventory());
		if (!freshPlan.hasSupportedTasks()) {
			QuestBii.LOGGER.info("[questbii] quest {}/{} '{}' no supported tasks: nonItem={} screenOnly={} craftingOnly={} invalid={}",
					batchIndex, batchTotal, quest.getTitle(), freshPlan.skippedNonItem, freshPlan.skippedScreenOnly,
					freshPlan.skippedCrafting, freshPlan.skippedInvalid);
			failQuest(mc, "text.questbii.msg.no_tasks", "text.questbii.reason.no_tasks");
			return;
		}
		if (freshPlan.needsBackpack() && backpackFailed && !BackpackGateway.isStorageMenuOpen()) {
			failQuest(mc, "text.questbii.msg.no_backpack", "text.questbii.reason.no_backpack");
			return;
		}
		this.plan = freshPlan;
		QuestBii.LOGGER.info("[questbii] quest {}/{}: {} supported task(s), {} target item type(s), needsBackpack={}",
				batchIndex, batchTotal, freshPlan.supportedTaskCount(), freshPlan.targets.size(), freshPlan.needsBackpack());
		if (freshPlan.needsBackpack()) {
			if (BackpackGateway.isStorageMenuOpen()) {
				StorageContainerMenuBase<?> menu = BackpackGateway.getStorageMenu();
				if (menu != null && menu.containerId == batchMenuId) {
					prepareReuse();
				} else {
					staleMenuId = menu == null ? -2 : menu.containerId;
					QuestBii.LOGGER.info("[questbii] pre-opened menu (containerId={}) untrusted, reopening for fresh sync", staleMenuId);
					nextPhase(Phase.OPEN);
					BackpackGateway.openBackpack();
				}
			} else {
				nextPhase(Phase.OPEN);
				BackpackGateway.openBackpack();
			}
		} else {
			nextPhase(Phase.DETECT_WAIT);
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !running) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			reset();
			return;
		}
		try {
			tick(mc);
		} catch (FlowAbortedException ignored) {
		} catch (Throwable t) {
			QuestBii.LOGGER.error("[questbii] flow error in phase {}", phase, t);
			try {
				fatalAbort(mc, "text.questbii.msg.flow_error", "text.questbii.reason.flow_error");
			} catch (Throwable t2) {
				// 最终兜底：任何异常都不能让 running 永久卡在 true（否则之后点击只会提示"进行中"）
				QuestBii.LOGGER.error("[questbii] fatalAbort itself failed, force reset", t2);
				reset();
			}
		}
	}

	private void tick(Minecraft mc) {
		phaseTick++;
		questTick++;
		// 单任务总时限：任何环节异常卡住都不会让"速达"永久无响应
		if (questTick > QUEST_DEADLINE && phase != Phase.VERIFY) {
			failQuest(mc, "text.questbii.msg.sync_timeout", "text.questbii.reason.sync_timeout");
			return;
		}
		switch (phase) {
			case WAIT_UNLOCK -> {
				TeamData teamData = ClientQuestFile.INSTANCE == null ? null : ClientQuestFile.INSTANCE.selfTeamData;
				if (teamData != null && teamData.canStartTasks(quest)) {
					QuestBii.LOGGER.info("[questbii] quest {}/{} unlocked after {} tick(s)", batchIndex, batchTotal, phaseTick);
					beginQuestFlow(mc);
				} else if (phaseTick >= WAIT_UNLOCK_TIMEOUT
						|| (phaseTick >= PREREQ_FASTFAIL_TICKS && !prereqsReachable())) {
					failQuest(mc, "text.questbii.msg.prereq_locked", "text.questbii.reason.prereq_locked");
				}
			}
			case OPEN -> {
				if (BackpackGateway.isStorageMenuOpen()) {
					int menuId = BackpackGateway.getStorageMenu().containerId;
					if (staleMenuId != -1 && menuId == staleMenuId) {
						// 服务器尚未关旧开新：周期重发开包请求
						if (phaseTick % OPEN_RESEND_INTERVAL == 1) {
							BackpackGateway.openBackpack();
						}
					} else {
						batchMenuId = menuId;
						staleMenuId = -1;
						QuestBii.LOGGER.info("[questbii] backpack open (containerId={}) after {} tick(s)", menuId, phaseTick);
						prepareReuse();
					}
				} else if (phaseTick % OPEN_RESEND_INTERVAL == 1) {
					BackpackGateway.openBackpack();
				}
				if (phase == Phase.OPEN && phaseTick >= OPEN_TIMEOUT) {
					backpackFailed = true;
					failQuest(mc, "text.questbii.msg.no_backpack", "text.questbii.reason.no_backpack");
				}
			}
			case REUSE -> {
				requireMenuOrAbort(mc);
				int budget = REUSE_PER_TICK;
				while (budget-- > 0 && !reuseQueue.isEmpty()) {
					BackpackGateway.quickMove(reuseQueue.pollFirst());
				}
				if (reuseQueue.isEmpty()) {
					nextPhase(Phase.REUSE_SETTLE);
				}
			}
			case REUSE_SETTLE -> {
				requireMenuOrAbort(mc);
				// 状态确认：被 shift 取出的背包槽已空（客户端模拟即时生效，≥3 tick 后服务端回包已到）
				if (phaseTick >= SETTLE_MIN_TICKS
						&& (slotsAllEmpty(reuseSlotIds) || phaseTick >= REUSE_SETTLE_TIMEOUT)) {
					nextPhase(Phase.INJECT_PREP);
					tick(mc);
				}
			}
			case INJECT_PREP -> {
				requireMenuOrAbort(mc);
				prepareInjection(mc);
			}
			case INJECT -> {
				requireMenuOrAbort(mc);
				int budget = INJECT_PER_TICK;
				while (budget-- > 0 && !injectQueue.isEmpty()) {
					Injection inj = injectQueue.pollFirst();
					BackpackGateway.sendSetGhostSlot(inj.stack(), inj.slotId());
				}
				if (injectQueue.isEmpty()) {
					nextPhase(Phase.WAIT_SYNC);
				}
			}
			case WAIT_SYNC -> {
				requireMenuOrAbort(mc);
				if (phaseTick >= SYNC_MIN_TICKS && allInjectedSlotsPopulated()) {
					nextPhase(Phase.MOVE_OUT);
				} else if (phaseTick >= SYNC_TIMEOUT) {
					// 注入包未同步回来：容器状态可疑（服务端可能早已换了菜单，注入被静默丢弃），
					// 强制重开换新菜单后整个任务重跑一次（重开后会看到已注入的物品并复用）
					if (openRetries == 0) {
						openRetries++;
						StorageContainerMenuBase<?> menu = BackpackGateway.getStorageMenu();
						staleMenuId = menu == null ? -2 : menu.containerId;
						QuestBii.LOGGER.warn("[questbii] inject not synced after {} tick(s), reopening backpack (was containerId={})", phaseTick, staleMenuId);
						clearPerQuestState();
						nextPhase(Phase.OPEN);
						BackpackGateway.openBackpack();
					} else {
						failQuest(mc, "text.questbii.msg.sync_timeout", "text.questbii.reason.sync_timeout");
					}
				}
			}
			case MOVE_OUT -> {
				requireMenuOrAbort(mc);
				int budget = REUSE_PER_TICK;
				while (budget-- > 0 && !injectedSlots.isEmpty()) {
					BackpackGateway.quickMove(injectedSlots.remove(0));
				}
				if (injectedSlots.isEmpty()) {
					nextPhase(Phase.MOVE_SETTLE);
				}
			}
			case MOVE_SETTLE -> {
				if (!BackpackGateway.isStorageMenuOpen()) {
					nextPhase(Phase.SUBMIT);
					queueSubmits();
					return;
				}
				// 状态确认：注入槽全部空（物品已移到身上背包），不再固定死等
				if (phaseTick >= MOVE_SETTLE_MIN_TICKS
						&& (slotsAllEmpty(moveOutSlots) || phaseTick >= MOVE_SETTLE_TIMEOUT)) {
					if (hasLeftoverInjectedItems()) {
						action(mc, "text.questbii.msg.inv_full");
					}
					nextPhase(Phase.SUBMIT);
					queueSubmits();
				}
			}
			case DETECT_WAIT -> {
				if (phaseTick >= DETECT_WAIT_TICKS) {
					nextPhase(Phase.SUBMIT);
					queueSubmits();
				}
			}
			case SUBMIT -> {
				if (!submitQueue.isEmpty()) {
					long taskId = submitQueue.pollFirst();
					try {
						new SubmitTaskMessage(taskId).sendToServer();
					} catch (Throwable t) {
						QuestBii.LOGGER.error("[questbii] failed to send SubmitTaskMessage for task {}", taskId, t);
					}
				} else {
					nextPhase(Phase.VERIFY);
				}
			}
			case VERIFY -> {
				if (phaseTick % VERIFY_INTERVAL == 0) {
					int[] done = countCompleted(mc);
					if (done[1] > 0 && done[0] == done[1]) {
						successCount++;
						lastCompletedQuestId = quest.id;
						QuestBii.LOGGER.info("[questbii] quest {}/{} verified after {} tick(s)", batchIndex, batchTotal, questTick);
						startNextQuest(mc);
					} else if (phaseTick >= VERIFY_TIMEOUT) {
						QuestBii.LOGGER.warn("[questbii] verify timeout: {}/{} task(s) completed", done[0], done[1]);
						failQuest(mc, "text.questbii.msg.incomplete", "text.questbii.reason.incomplete");
					}
				}
			}
			default -> {
			}
		}
	}

	/** 给定槽位集合在当前容器中是否全部为空（用于等待移物/取出被服务端确认）。 */
	private static boolean slotsAllEmpty(java.util.Collection<Integer> slotIds) {
		StorageContainerMenuBase<?> menu = BackpackGateway.getStorageMenu();
		if (menu == null || slotIds.isEmpty()) {
			return menu != null;
		}
		for (int slotId : slotIds) {
			try {
				if (!menu.getSlot(slotId).getItem().isEmpty()) {
					return false;
				}
			} catch (Throwable t) {
				return false;
			}
		}
		return true;
	}

	/** 非致命失败：记录原因并继续下一任务。 */
	private void failQuest(Minecraft mc, String msgKey, String reasonKey) {
		QuestBii.LOGGER.info("[questbii] quest failed in phase {}: {}", phase, msgKey);
		if (quest != null) {
			failures.add(failLine(quest, reasonKey));
		}
		action(mc, msgKey);
		startNextQuest(mc);
	}

	/** 致命失败（容器丢失/流程异常）：当前与剩余任务全部记失败，关包并收尾。 */
	private void fatalAbort(Minecraft mc, String msgKey, String reasonKey) {
		QuestBii.LOGGER.info("[questbii] batch aborted in phase {}: {}", phase, msgKey);
		if (quest != null) {
			failures.add(failLine(quest, reasonKey));
		}
		while (!questQueue.isEmpty()) {
			failures.add(failLine(questQueue.pollFirst(), reasonKey));
		}
		action(mc, msgKey);
		finishBatch(mc);
	}

	private void requireMenuOrAbort(Minecraft mc) {
		if (!BackpackGateway.isStorageMenuOpen()) {
			fatalAbort(mc, "text.questbii.msg.container_closed", "text.questbii.reason.container_closed");
			throw new FlowAbortedException();
		}
	}

	/** 单任务级的槽位/队列状态复位（任务推进与 retry 重跑共用）。 */
	private void clearPerQuestState() {
		reuseSlotIds.clear();
		reuseQueue.clear();
		injectQueue.clear();
		injectedSlots.clear();
		moveOutSlots.clear();
		submitQueue.clear();
	}

	private void prepareReuse() {
		// 重跑场景下可能残留上次的登记，先清空保证幂等
		reuseSlotIds.clear();
		reuseQueue.clear();
		plan.applyBackpackStacks(BackpackGateway.collectBackpackStacks());
		for (TargetItem target : plan.targets) {
			reuseSlotIds.addAll(target.reuseSlots);
		}
		reuseQueue.addAll(reuseSlotIds);
		QuestBii.LOGGER.info("[questbii] reuse {} backpack slot(s), total inject needed {}", reuseSlotIds.size(), plan.totalInjectNeeded());
		nextPhase(Phase.REUSE);
	}

	private void prepareInjection(Minecraft mc) {
		for (BackpackStack stack : BackpackGateway.collectBackpackStacks()) {
			if (reuseSlotIds.contains(stack.slotIndex()) && matchesAnyTarget(stack.stack())) {
				action(mc, "text.questbii.msg.inv_full");
				break;
			}
		}
		List<Integer> emptySlots = BackpackGateway.findEmptySlotIds();
		int slotCursor = 0;
		for (TargetItem target : plan.targets) {
			for (long amount = Math.max(0L, target.injectNeeded); amount > 0L; amount -= Math.min(amount, Math.max(1, target.prototype.getMaxStackSize()))) {
				if (slotCursor >= emptySlots.size()) {
					QuestBii.LOGGER.warn("[questbii] not enough empty backpack slots: have {} used", slotCursor);
					failQuest(mc, "text.questbii.msg.no_space", "text.questbii.reason.no_space");
					return;
				}
				int slotId = emptySlots.get(slotCursor++);
				int batch = (int) Math.min(amount, Math.max(1, target.prototype.getMaxStackSize()));
				ItemStack stack = target.prototype.copy();
				stack.setCount(batch);
				injectQueue.add(new Injection(stack, slotId));
				injectedSlots.add(slotId);
				if (!moveOutSlots.contains(slotId)) {
					moveOutSlots.add(slotId);
				}
			}
		}
		QuestBii.LOGGER.info("[questbii] injecting {} packet(s) into {} slot(s)", injectQueue.size(), injectedSlots.size());
		if (injectQueue.isEmpty()) {
			nextPhase(Phase.SUBMIT);
			queueSubmits();
		} else {
			nextPhase(Phase.INJECT);
		}
	}

	private boolean allInjectedSlotsPopulated() {
		StorageContainerMenuBase<?> menu = BackpackGateway.getStorageMenu();
		if (menu == null) {
			return false;
		}
		for (int slotId : injectedSlots) {
			try {
				if (menu.getSlot(slotId).getItem().isEmpty()) {
					return false;
				}
			} catch (Throwable ignored) {
				return false;
			}
		}
		return true;
	}

	private boolean hasLeftoverInjectedItems() {
		StorageContainerMenuBase<?> menu = BackpackGateway.getStorageMenu();
		if (menu == null) {
			return false;
		}
		for (int slotId : injectedSlots) {
			try {
				if (!menu.getSlot(slotId).getItem().isEmpty()) {
					return true;
				}
			} catch (Throwable ignored) {
				return true;
			}
		}
		return false;
	}

	private void queueSubmits() {
		submitQueue.addAll(plan.consumeTaskIds);
		QuestBii.LOGGER.info("[questbii] submitting {} consume task(s), {} detection task(s)", plan.consumeTaskIds.size(), plan.detectionTaskIds.size());
	}

	private boolean matchesAnyTarget(ItemStack stack) {
		for (TargetItem target : plan.targets) {
			if (target.matches(stack)) {
				return true;
			}
		}
		return false;
	}

	private int[] countCompleted(Minecraft mc) {
		TeamData teamData = ClientQuestFile.INSTANCE == null ? null : ClientQuestFile.INSTANCE.selfTeamData;
		Set<Long> targetIds = new HashSet<>(plan.consumeTaskIds);
		targetIds.addAll(plan.detectionTaskIds);
		int completed = 0;
		for (Task task : quest.getTasks()) {
			if (targetIds.contains(task.id) && teamData != null && teamData.isCompleted(task)) {
				completed++;
			}
		}
		return new int[]{completed, targetIds.size()};
	}

	/** 判断未完成的前置是否可达：在本批次剩余队列中，或刚完成任务的对象级同步仍在途；否则再等也不会解锁。 */
	private boolean prereqsReachable() {
		TeamData teamData = ClientQuestFile.INSTANCE == null ? null : ClientQuestFile.INSTANCE.selfTeamData;
		if (teamData == null || quest == null) {
			return true;
		}
		for (QuestObject dep : quest.streamDependencies().toList()) {
			if (!teamData.isCompleted(dep)
					&& dep.id != lastCompletedQuestId
					&& questQueue.stream().noneMatch(q -> q.id == dep.id)) {
				return false;
			}
		}
		return true;
	}

	/** 选区内部按依赖拓扑排序（Kahn）：前置在前、后置在后；出现环等异常时回退原始顺序。 */
	private static List<Quest> orderTopologically(List<Quest> selected) {
		Map<Long, Quest> byId = new LinkedHashMap<>();
		for (Quest q : selected) {
			byId.putIfAbsent(q.id, q);
		}
		List<Quest> deduped = new ArrayList<>(byId.values());
		Map<Long, Integer> inDegree = new HashMap<>();
		Map<Long, List<Long>> edges = new HashMap<>();
		for (Quest q : deduped) {
			int degree = 0;
			for (QuestObject dep : q.streamDependencies().toList()) {
				if (dep.id != q.id && byId.containsKey(dep.id)) {
					degree++;
					edges.computeIfAbsent(dep.id, k -> new ArrayList<>()).add(q.id);
				}
			}
			inDegree.put(q.id, degree);
		}
		Deque<Long> ready = new ArrayDeque<>();
		for (Quest q : deduped) {
			if (inDegree.get(q.id) == 0) {
				ready.addLast(q.id);
			}
		}
		List<Quest> ordered = new ArrayList<>(deduped.size());
		while (!ready.isEmpty()) {
			Quest q = byId.get(ready.pollFirst());
			ordered.add(q);
			for (Long dependentId : edges.getOrDefault(q.id, List.of())) {
				if (inDegree.merge(dependentId, -1, Integer::sum) == 0) {
					ready.addLast(dependentId);
				}
			}
		}
		if (ordered.size() != deduped.size()) {
			QuestBii.LOGGER.warn("[questbii] dependency cycle among selected quests, falling back to original order");
			return deduped;
		}
		return ordered;
	}

	/** 批次收尾：关包、刷新任务书界面、聊天栏输出汇总。 */
	private void finishBatch(Minecraft mc) {
		if (BackpackGateway.isStorageMenuOpen()) {
			BackpackGateway.closeContainer();
		}
		QuestBii.LOGGER.info("[questbii] batch finished: {}/{} succeeded, {} failed", successCount, batchTotal, failures.size());
		refreshQuestScreen(mc);
		if (mc.player != null) {
			if (batchTotal <= 1) {
				if (failures.isEmpty()) {
					action(mc, "text.questbii.msg.success");
				}
				// 单任务失败原因已在 failQuest/fatalAbort 用 actionbar 提示
			} else if (failures.isEmpty()) {
				chat(mc, Component.translatable("text.questbii.msg.batch_success", successCount, batchTotal).withStyle(ChatFormatting.GREEN));
			} else {
				chat(mc, Component.translatable("text.questbii.msg.batch_summary", successCount, failures.size()).withStyle(ChatFormatting.GOLD));
				for (Component failure : failures) {
					chat(mc, failure.copy().withStyle(ChatFormatting.GRAY));
				}
			}
		}
		reset();
	}

	/** 静默模式下界面从未离开，仅就地刷新；不在任务书界面时不做任何事。 */
	private static void refreshQuestScreen(Minecraft mc) {
		try {
			if (mc.screen instanceof IScreenWrapper wrapper && wrapper.getGui() instanceof QuestScreen questScreen) {
				questScreen.refreshViewQuestPanel();
				questScreen.refreshQuestPanel();
			}
		} catch (Throwable t) {
			QuestBii.LOGGER.error("[questbii] failed to refresh quest screen", t);
		}
	}

	private static Component failLine(Quest quest, String reasonKey) {
		return Component.translatable("text.questbii.msg.batch_fail_line", quest.getTitle(), Component.translatable(reasonKey));
	}

	private void nextPhase(Phase newPhase) {
		phase = newPhase;
		phaseTick = 0;
	}

	private void reset() {
		running = false;
		quest = null;
		plan = null;
		phase = Phase.IDLE;
		phaseTick = 0;
		questTick = 0;
		lastCompletedQuestId = -1L;
		batchMenuId = -1;
		staleMenuId = -1;
		openRetries = 0;
		reuseSlotIds.clear();
		reuseQueue.clear();
		injectQueue.clear();
		injectedSlots.clear();
		moveOutSlots.clear();
		submitQueue.clear();
		questQueue.clear();
		batchTotal = 0;
		batchIndex = 0;
		successCount = 0;
		failures.clear();
		backpackFailed = false;
	}

	private static void action(Minecraft mc, String key, Object... args) {
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static void chat(Minecraft mc, Component message) {
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}
}
