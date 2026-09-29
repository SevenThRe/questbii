package com.seventhre.questbii;

import com.seventhre.questbii.client.QuickCompleteController;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(QuestBii.MOD_ID)
public class QuestBii {

	public static final String MOD_ID = "questbii";
	public static final Logger LOGGER = LogManager.getLogger("questbii");

	public QuestBii() {
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		MinecraftForge.EVENT_BUS.register(QuickCompleteController.INSTANCE);
		LOGGER.info("[questbii] FTBQ quest quick-complete loaded");
	}
}
