package com.ghostmod;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * The two movable HUD elements: the efficiency block and the player + inventory box.
 */
public final class GhostHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int MARGIN = 4;

	public static final Element EFFICIENCY = new EfficiencyElement();
	public static final Element PLAYER_BOX = new PlayerBoxElement();
	public static final List<Element> ELEMENTS = List.of(EFFICIENCY, PLAYER_BOX);

	private GhostHud() { }

	public static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.SUBTITLES,
				Identifier.fromNamespaceAndPath("ghostmod", "hud"),
				GhostHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen instanceof MoveGuiScreen) return; // the move screen draws them itself
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		for (Element element : ELEMENTS) {
			if (element.enabled()) {
				element.draw(graphics, mc, element.x(mc, sw, sh), element.y(mc, sw, sh));
			}
		}
	}

	// ------------------------------------------------------------------ element base

	public abstract static class Element {
		public abstract String name();

		public abstract boolean enabled();

		public abstract int width(Minecraft mc);

		public abstract int height(Minecraft mc);

		/** Where the element sits until you move it. */
		protected abstract int defaultX(int sw, int sh, int w, int h);

		protected abstract int defaultY(int sw, int sh, int w, int h);

		protected abstract int savedX();

		protected abstract int savedY();

		public abstract void setSaved(int x, int y);

		public abstract void clearSaved();

		public abstract void draw(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y);

		public final int x(Minecraft mc, int sw, int sh) {
			int w = width(mc);
			int x = savedX() >= 0 ? savedX() : defaultX(sw, sh, w, height(mc));
			return Math.max(0, Math.min(x, Math.max(0, sw - w)));
		}

		public final int y(Minecraft mc, int sw, int sh) {
			int h = height(mc);
			int y = savedY() >= 0 ? savedY() : defaultY(sw, sh, width(mc), h);
			return Math.max(0, Math.min(y, Math.max(0, sh - h)));
		}
	}

	// ------------------------------------------------------------------ efficiency block

	private static final class EfficiencyElement extends Element {
		private static final float BIG = 1.4f;
		private static final float SMALL = 0.95f;
		private static final int LINE_GAP = 2;

		@Override
		public String name() {
			return "Efficiency";
		}

		@Override
		public boolean enabled() {
			return GhostConfig.get().showEfficiency;
		}

		private String[] lines() {
			GhostTracker t = GhostTracker.INSTANCE;
			return new String[] {
				"Ghost Effiency " + Math.round(t.efficiencyPercent()) + "%",
				"Kills/h: " + Math.round(t.killsPerHour()),
				"Miss per Hour: " + Math.round(t.missesPerHour()),
				"Longer Path Per hour: " + Math.round(t.longerPathsPerHour())
			};
		}

		private static float scaleOf(int line) {
			return line < 2 ? BIG : SMALL;
		}

		@Override
		public int width(Minecraft mc) {
			Font font = mc.font;
			String[] lines = lines();
			float widest = 0;
			for (int i = 0; i < lines.length; i++) {
				float w = font.width(lines[i]) * scaleOf(i);
				if (i == 1 && GhostTracker.INSTANCE.isPaused()) {
					w += font.width(" (paused)") * scaleOf(i);
				}
				widest = Math.max(widest, w);
			}
			return (int) Math.ceil(widest);
		}

		@Override
		public int height(Minecraft mc) {
			String[] lines = lines();
			float total = 0;
			for (int i = 0; i < lines.length; i++) {
				total += mc.font.lineHeight * scaleOf(i) + LINE_GAP;
			}
			return (int) Math.ceil(total);
		}

		@Override
		protected int defaultX(int sw, int sh, int w, int h) {
			return (sw - w) / 2;
		}

		@Override
		protected int defaultY(int sw, int sh, int w, int h) {
			return MARGIN;
		}

		@Override
		protected int savedX() {
			return GhostConfig.get().efficiencyX;
		}

		@Override
		protected int savedY() {
			return GhostConfig.get().efficiencyY;
		}

		@Override
		public void setSaved(int x, int y) {
			GhostConfig.get().efficiencyX = x;
			GhostConfig.get().efficiencyY = y;
		}

		@Override
		public void clearSaved() {
			setSaved(-1, -1);
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y) {
			Font font = mc.font;
			String[] lines = lines();
			float cursorY = y;
			for (int i = 0; i < lines.length; i++) {
				float scale = scaleOf(i);
				graphics.pose().pushMatrix();
				graphics.pose().translate((float) x, cursorY);
				graphics.pose().scale(scale);
				graphics.text(font, lines[i], 0, 0, WHITE);
				if (i == 1 && GhostTracker.INSTANCE.isPaused()) {
					graphics.text(font, " (paused)", font.width(lines[i]), 0, GRAY);
				}
				graphics.pose().popMatrix();
				cursorY += font.lineHeight * scale + LINE_GAP;
			}
		}
	}

	// ------------------------------------------------------------------ player + inventory box

	private static final class PlayerBoxElement extends Element {
		private static final int SLOT = 18;
		private static final int MODEL_W = 49;
		private static final int MODEL_H = 70;
		private static final int GAP = 6;

		@Override
		public String name() {
			return "Player + inventory";
		}

		@Override
		public boolean enabled() {
			return GhostConfig.get().showPlayerBox;
		}

		private static int rows() {
			return switch (GhostConfig.get().inventoryMode) {
				case HOTBAR -> 1;
				case FULL -> 4;
				case OFF -> 0;
			};
		}

		@Override
		public int width(Minecraft mc) {
			int rows = rows();
			return (rows > 0 ? 9 * SLOT + GAP : 0) + MODEL_W;
		}

		@Override
		public int height(Minecraft mc) {
			return Math.max(rows() * SLOT, MODEL_H);
		}

		@Override
		protected int defaultX(int sw, int sh, int w, int h) {
			return sw - w - MARGIN;
		}

		@Override
		protected int defaultY(int sw, int sh, int w, int h) {
			return sh - h - MARGIN;
		}

		@Override
		protected int savedX() {
			return GhostConfig.get().playerBoxX;
		}

		@Override
		protected int savedY() {
			return GhostConfig.get().playerBoxY;
		}

		@Override
		public void setSaved(int x, int y) {
			GhostConfig.get().playerBoxX = x;
			GhostConfig.get().playerBoxY = y;
		}

		@Override
		public void clearSaved() {
			setSaved(-1, -1);
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Minecraft mc, int x, int y) {
			LocalPlayer player = mc.player;
			if (player == null) return;

			int rows = rows();
			int modelX = x;

			if (rows > 0) {
				int gridH = rows * SLOT;
				int gridY = y + (height(mc) - gridH) / 2;
				for (int row = 0; row < rows; row++) {
					for (int col = 0; col < 9; col++) {
						int slot;
						if (rows == 1) {
							slot = col; // hotbar
						} else {
							slot = row < 3 ? 9 + row * 9 + col : col; // main inventory, then hotbar at the bottom
						}
						ItemStack stack = player.getInventory().getItem(slot);
						if (stack.isEmpty()) continue;
						int ix = x + col * SLOT + 1;
						int iy = gridY + row * SLOT + 1;
						graphics.fakeItem(stack, ix, iy);
						graphics.itemDecorations(mc.font, stack, ix, iy, null);
					}
				}
				modelX = x + 9 * SLOT + GAP;
			}

			int modelY = y + (height(mc) - MODEL_H) / 2;
			int lookX = modelX + MODEL_W / 2;
			int lookY = modelY + 20;
			InventoryScreen.extractEntityInInventoryFollowsMouse(
					graphics, modelX, modelY, modelX + MODEL_W, modelY + MODEL_H,
					30, 0.0625F, lookX, lookY, player);
		}
	}
}
