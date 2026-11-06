package com.ghostmod;

import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

public class GhostMod implements ClientModInitializer {
	public static final String MOD_ID = "ghostmod";

	/** A screen to open on the next tick (opening it straight from a command would be closed with the chat). */
	private static Supplier<Screen> pendingScreen;

	@Override
	public void onInitializeClient() {
		GhostConfig.load();
		GhostHud.register();

		// The line to the Ghost, drawn in the world.
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(LineRenderer::render);

		// Tracker update, and opening screens requested by commands.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			GhostTracker.INSTANCE.tick(client);
			if (pendingScreen != null) {
				Supplier<Screen> supplier = pendingScreen;
				pendingScreen = null;
				client.setScreen(supplier.get());
			}
		});

		// Left clicks, for counting misses.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (clickCount != 0) {
				GhostTracker.INSTANCE.onAttackClick(client);
			}
			return false; // never cancel the attack
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		// Commands are case sensitive, so register both spellings.
		for (String name : new String[] { "ghosttracker", "ghostTracker" }) {
			dispatcher.register(ClientCommands.literal(name)
					.executes(context -> {
						pendingScreen = SettingsScreen::new;
						return 1;
					})
					.then(ClientCommands.literal("move").executes(context -> {
						pendingScreen = MoveGuiScreen::new;
						return 1;
					}))
					.then(ClientCommands.literal("reset").executes(context -> {
						GhostTracker.INSTANCE.reset();
						context.getSource().sendFeedback(Component.literal("Ghost tracker reset."));
						return 1;
					})));
		}
	}
}
