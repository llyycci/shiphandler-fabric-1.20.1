package com.llyycci.shiphandler;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShipHandlerMod implements ModInitializer {
	public static final String MOD_ID = "shiphandler";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		// 注册配置
		ForgeConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.COMMON, ShiphandlerConfig.GENERAL_SPEC);
		
		// 注册服务器启动事件
		// 注册命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ShiphandlerCommands.register(dispatcher);
		});
		// 注册服务器启动事件
		ServerLifecycleEvents.SERVER_STARTING.register(ShipHandlerMod::setServer);
		// 注册HandleShips所需各类事件
		HandleShips.init();
	}
	public static void warn(String format, Object... args) {
		LOGGER.warn(format, args);
	}
	
	public static ResourceLocation getResource(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
	
	public static void setServer(MinecraftServer server) {
		ShipHandlerMod.server = server;
	}
	
	public static MinecraftServer getServer() {
		return server;
	}
}