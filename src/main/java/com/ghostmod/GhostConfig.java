package com.ghostmod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * All saved settings. Stored as JSON in the Minecraft config folder (ghostmod.json).
 */
public final class GhostConfig {
	public enum Anchor {
		HEAD, CHEST, FEET;

		public Anchor next() {
			return values()[(ordinal() + 1) % values().length];
		}

		/** Height of this anchor as a fraction of the entity's bounding box height. */
		public double fraction() {
			return switch (this) {
				case HEAD -> 0.92;
				case CHEST -> 0.6;
				case FEET -> 0.04;
			};
		}
	}

	public enum InventoryMode {
		HOTBAR, FULL, OFF;

		public InventoryMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	// Line to the nearest Ghost
	public boolean lineEnabled = true;
	public Anchor lineStart = Anchor.FEET; // point on you
	public Anchor lineEnd = Anchor.FEET; // point on the Ghost
	public float lineThickness = 3.0f; // roughly in screen pixels
	public int lineColor = 0x55FF55; // RGB

	// HUD
	public boolean showEfficiency = true;
	public boolean showPlayerBox = true;
	public InventoryMode inventoryMode = InventoryMode.HOTBAR;

	// Tracker: seconds without a kill before the tracker pauses
	public int pauseSeconds = 15;

	// HUD positions in GUI pixels. -1 means "use the default position".
	public int efficiencyX = -1;
	public int efficiencyY = -1;
	public int playerBoxX = -1;
	public int playerBoxY = -1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static GhostConfig instance = new GhostConfig();

	public static GhostConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ghostmod.json");
	}

	public static void load() {
		Path path = file();
		if (Files.exists(path)) {
			try {
				GhostConfig loaded = GSON.fromJson(Files.readString(path), GhostConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				System.err.println("[ghostmod] Could not read config, using defaults: " + e);
			}
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(instance));
		} catch (IOException e) {
			System.err.println("[ghostmod] Could not save config: " + e);
		}
	}

	private void sanitize() {
		if (lineStart == null) lineStart = Anchor.FEET;
		if (lineEnd == null) lineEnd = Anchor.FEET;
		if (inventoryMode == null) inventoryMode = InventoryMode.HOTBAR;
		lineThickness = Math.max(0.5f, Math.min(10.0f, lineThickness));
		pauseSeconds = Math.max(5, Math.min(120, pauseSeconds));
		lineColor &= 0xFFFFFF;
	}
}
