package com.ghostmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings menu, opened with /ghosttracker.
 * Left column: the line. Right column: HUD, tracker and buttons.
 */
public class SettingsScreen extends Screen {
	private static final int COLUMN_W = 150;
	private static final int ROW = 24;
	private static final int TOP = 40;

	private final GhostConfig cfg = GhostConfig.get();
	private EditBox colorBox;
	private int swatchX;
	private int swatchY;

	public SettingsScreen() {
		super(Component.literal("Ghost Mod"));
	}

	private static Component onOff(String label, boolean value) {
		return Component.literal(label + ": " + (value ? "ON" : "OFF"));
	}

	private static Component text(String value) {
		return Component.literal(value);
	}

	@Override
	protected void init() {
		int left = this.width / 2 - COLUMN_W - 5;
		int right = this.width / 2 + 5;

		// ---- left column: the line
		this.addRenderableWidget(Button.builder(onOff("Line", cfg.lineEnabled), b -> {
			cfg.lineEnabled = !cfg.lineEnabled;
			b.setMessage(onOff("Line", cfg.lineEnabled));
		}).pos(left, TOP).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Line from you: " + cfg.lineStart), b -> {
			cfg.lineStart = cfg.lineStart.next();
			b.setMessage(text("Line from you: " + cfg.lineStart));
		}).pos(left, TOP + ROW).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Line to Ghost: " + cfg.lineEnd), b -> {
			cfg.lineEnd = cfg.lineEnd.next();
			b.setMessage(text("Line to Ghost: " + cfg.lineEnd));
		}).pos(left, TOP + ROW * 2).size(COLUMN_W, 20).build());

		// Thickness: [-] label [+]
		Button thicknessLabel = this.addRenderableWidget(Button.builder(thicknessText(), b -> { })
				.pos(left + 24, TOP + ROW * 3).size(COLUMN_W - 48, 20).build());
		thicknessLabel.active = false;
		this.addRenderableWidget(Button.builder(text("-"), b -> {
			cfg.lineThickness = Math.max(0.5f, cfg.lineThickness - 0.5f);
			thicknessLabel.setMessage(thicknessText());
		}).pos(left, TOP + ROW * 3).size(20, 20).build());
		this.addRenderableWidget(Button.builder(text("+"), b -> {
			cfg.lineThickness = Math.min(10.0f, cfg.lineThickness + 0.5f);
			thicknessLabel.setMessage(thicknessText());
		}).pos(left + COLUMN_W - 20, TOP + ROW * 3).size(20, 20).build());

		// Color: hex box and a swatch
		this.colorBox = this.addRenderableWidget(new EditBox(this.font, left + 46, TOP + ROW * 4, 62, 20, text("Color")));
		this.colorBox.setMaxLength(6);
		this.colorBox.setValue(String.format("%06X", cfg.lineColor));
		this.colorBox.setResponder(value -> {
			if (value.length() == 6) {
				try {
					cfg.lineColor = Integer.parseInt(value, 16) & 0xFFFFFF;
				} catch (NumberFormatException ignored) {
					// not a valid color yet
				}
			}
		});
		this.swatchX = left + 114;
		this.swatchY = TOP + ROW * 4;

		// ---- right column: HUD and tracker
		this.addRenderableWidget(Button.builder(onOff("Efficiency", cfg.showEfficiency), b -> {
			cfg.showEfficiency = !cfg.showEfficiency;
			b.setMessage(onOff("Efficiency", cfg.showEfficiency));
		}).pos(right, TOP).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(onOff("Player", cfg.showPlayerBox), b -> {
			cfg.showPlayerBox = !cfg.showPlayerBox;
			b.setMessage(onOff("Player", cfg.showPlayerBox));
		}).pos(right, TOP + ROW).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Inventory: " + cfg.inventoryMode), b -> {
			cfg.inventoryMode = cfg.inventoryMode.next();
			b.setMessage(text("Inventory: " + cfg.inventoryMode));
		}).pos(right, TOP + ROW * 2).size(COLUMN_W, 20).build());

		// Idle pause: [-] label [+]
		Button pauseLabel = this.addRenderableWidget(Button.builder(pauseText(), b -> { })
				.pos(right + 24, TOP + ROW * 3).size(COLUMN_W - 48, 20).build());
		pauseLabel.active = false;
		this.addRenderableWidget(Button.builder(text("-"), b -> {
			cfg.pauseSeconds = Math.max(5, cfg.pauseSeconds - 5);
			pauseLabel.setMessage(pauseText());
		}).pos(right, TOP + ROW * 3).size(20, 20).build());
		this.addRenderableWidget(Button.builder(text("+"), b -> {
			cfg.pauseSeconds = Math.min(120, cfg.pauseSeconds + 5);
			pauseLabel.setMessage(pauseText());
		}).pos(right + COLUMN_W - 20, TOP + ROW * 3).size(20, 20).build());

		this.addRenderableWidget(Button.builder(text("Move GUIs"), b -> {
			GhostConfig.save();
			this.minecraft.setScreen(new MoveGuiScreen());
		}).pos(right, TOP + ROW * 4).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Reset stats"), b -> GhostTracker.INSTANCE.reset())
				.pos(right, TOP + ROW * 5).size(COLUMN_W, 20).build());

		// ---- bottom
		this.addRenderableWidget(Button.builder(text("Done"), b -> this.onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	private Component thicknessText() {
		return text("Thickness: " + String.format("%.1f", cfg.lineThickness));
	}

	private Component pauseText() {
		return text("Pause after: " + cfg.pauseSeconds + "s");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);

		graphics.centeredText(this.font, "Ghost Mod", this.width / 2, 12, 0xFFFFFFFF);
		graphics.text(this.font, "Line", this.width / 2 - COLUMN_W - 5, 28, 0xFFAAAAAA);
		graphics.text(this.font, "HUD and tracker", this.width / 2 + 5, 28, 0xFFAAAAAA);

		// label and swatch for the color row
		graphics.text(this.font, "Color #", this.width / 2 - COLUMN_W - 5, TOP + ROW * 4 + 6, 0xFFFFFFFF);
		graphics.fill(this.swatchX, this.swatchY, this.swatchX + 36, this.swatchY + 20, 0xFFFFFFFF);
		graphics.fill(this.swatchX + 1, this.swatchY + 1, this.swatchX + 35, this.swatchY + 19, 0xFF000000 | cfg.lineColor);

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		GhostConfig.save();
		super.onClose();
	}
}
