package com.ghostmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Drag the HUD elements where you want them. Positions are saved when you let go.
 */
public class MoveGuiScreen extends Screen {
	private static final int OUTLINE = 0xFFFFFFFF;
	private static final int OUTLINE_ACTIVE = 0xFF55FF55;

	private GhostHud.Element dragging;
	private int grabX;
	private int grabY;

	public MoveGuiScreen() {
		super(Component.literal("Move GUIs"));
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(Component.literal("Reset positions"), button -> {
			for (GhostHud.Element element : GhostHud.ELEMENTS) {
				element.clearSaved();
			}
			GhostConfig.save();
		}).pos(this.width / 2 - 105, this.height - 28).size(100, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
				.pos(this.width / 2 + 5, this.height - 28).size(100, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// A light dim instead of the blur, so you can see where things sit.
		graphics.fill(0, 0, this.width, this.height, 0x66000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);

		graphics.centeredText(this.font, "Drag the boxes to move them", this.width / 2, 10, 0xFFFFFFFF);

		for (GhostHud.Element element : GhostHud.ELEMENTS) {
			// Draw every element here, even disabled ones, so you can place them before turning them on.
			int x = element.x(this.minecraft, this.width, this.height);
			int y = element.y(this.minecraft, this.width, this.height);
			int w = element.width(this.minecraft);
			int h = element.height(this.minecraft);

			if (element.enabled()) {
				element.draw(graphics, this.minecraft, x, y);
			}
			outline(graphics, x - 2, y - 2, x + w + 2, y + h + 2, element == this.dragging ? OUTLINE_ACTIVE : OUTLINE);
			graphics.text(this.font, element.name(), x, y - 12, 0xFFAAAAAA);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			// Later elements are drawn on top, so check them first.
			for (int i = GhostHud.ELEMENTS.size() - 1; i >= 0; i--) {
				GhostHud.Element element = GhostHud.ELEMENTS.get(i);
				int x = element.x(this.minecraft, this.width, this.height);
				int y = element.y(this.minecraft, this.width, this.height);
				int w = element.width(this.minecraft);
				int h = element.height(this.minecraft);
				if (event.x() >= x - 2 && event.x() <= x + w + 2 && event.y() >= y - 2 && event.y() <= y + h + 2) {
					this.dragging = element;
					this.grabX = (int) event.x() - x;
					this.grabY = (int) event.y() - y;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.dragging != null) {
			int w = this.dragging.width(this.minecraft);
			int h = this.dragging.height(this.minecraft);
			int x = (int) event.x() - this.grabX;
			int y = (int) event.y() - this.grabY;
			x = Math.max(0, Math.min(x, Math.max(0, this.width - w)));
			y = Math.max(0, Math.min(y, Math.max(0, this.height - h)));
			this.dragging.setSaved(x, y);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging != null) {
			this.dragging = null;
			GhostConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		GhostConfig.save();
		super.onClose();
	}
}
