package com.seventhre.questbii.mixin;

import com.seventhre.questbii.client.QuickCompleteController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 速达流程运行期间抑制 Sophisticated 背包/设置界面的打开。
 * 容器菜单由服务端 open 包正常建立（handleOpenScreen 先赋 containerMenu 再 setScreen），
 * 取消 setScreen 不影响槽位同步与 quickMove 发包。
 * 本工程未挂 Mixin AP 生成 refmap，故 method 同时列出官方名与 SRG 名，
 * 生产环境命中 m_91152_，开发环境命中 setScreen，require=1 由命中者满足。
 */
@Mixin(Minecraft.class)
public abstract class ScreenSuppressMixin {

	@Inject(method = {
			"setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
			"m_91152_(Lnet/minecraft/client/gui/screens/Screen;)V"
	}, at = @At("HEAD"), cancellable = true)
	private void questbii$suppressStorageScreen(Screen screen, CallbackInfo ci) {
		if (screen instanceof StorageScreenBase && QuickCompleteController.isRunning()) {
			ci.cancel();
		}
	}
}
