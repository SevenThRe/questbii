package com.seventhre.questbii.mixin;

import com.seventhre.questbii.client.QuestSelection;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 选择集非空时按 Esc 只清空选择，不关闭任务书界面。
 */
@Mixin(value = BaseScreen.class, remap = false)
public abstract class BaseScreenMixin {

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = false)
	private void questbii$escClearsSelection(Key key, CallbackInfoReturnable<Boolean> cir) {
		if (!QuestSelection.isEmpty() && key.is(GLFW.GLFW_KEY_ESCAPE)) {
			QuestSelection.clear();
			cir.setReturnValue(true);
		}
	}
}
