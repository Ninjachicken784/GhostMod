package com.ghostmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Draws the line from you to the Ghost you should go to next.
 * The line is a flat ribbon that always faces the camera, so its thickness stays roughly the same on screen.
 */
public final class LineRenderer {
	private static final float ALPHA = 0.9f;

	private LineRenderer() { }

	public static void render(LevelRenderContext context) {
		GhostConfig cfg = GhostConfig.get();
		if (!cfg.lineEnabled) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Entity target = GhostTracker.INSTANCE.target();
		if (player == null || target == null || !target.isAlive()) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 from = anchor(player, cfg.lineStart, partial);
		Vec3 to = anchor(target, cfg.lineEnd, partial);

		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 a = from.subtract(camera); // positions relative to the camera
		Vec3 b = to.subtract(camera);
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6) return;

		// A direction sideways to the line and to the view, used to give the line its width.
		Vec3 side = dir.cross(a.add(b).scale(0.5));
		if (side.lengthSqr() < 1.0E-8) {
			side = dir.cross(new Vec3(0, 1, 0));
		}
		if (side.lengthSqr() < 1.0E-8) {
			side = dir.cross(new Vec3(1, 0, 0));
		}
		side = side.normalize();

		// One screen pixel is about (1.4 / window height) blocks per block of distance (70 degree FOV).
		double blocksPerPixel = 1.4 / Math.max(1, mc.getWindow().getHeight());
		double halfA = Math.max(0.002, a.length() * blocksPerPixel * cfg.lineThickness * 0.5);
		double halfB = Math.max(0.002, b.length() * blocksPerPixel * cfg.lineThickness * 0.5);

		Vec3 a1 = from.add(side.scale(halfA));
		Vec3 a2 = from.subtract(side.scale(halfA));
		Vec3 b1 = to.add(side.scale(halfB));
		Vec3 b2 = to.subtract(side.scale(halfB));

		int rgb = cfg.lineColor;
		int color = ARGB.colorFromFloat(ALPHA, ((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		Matrix4f matrix = poseStack.last().pose();
		VertexConsumer buffer = context.bufferSource().getBuffer(RenderTypes.debugFilledBox());

		// Both windings so the ribbon is visible whichever side the game culls.
		quad(buffer, matrix, color, a1, b1, b2, a2);
		quad(buffer, matrix, color, a2, b2, b1, a1);

		poseStack.popPose();
	}

	private static Vec3 anchor(Entity entity, GhostConfig.Anchor anchor, float partial) {
		Vec3 base = entity.getPosition(partial);
		return base.add(0, entity.getBbHeight() * anchor.fraction(), 0);
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix, int color, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4) {
		vertex(buffer, matrix, color, p1);
		vertex(buffer, matrix, color, p2);
		vertex(buffer, matrix, color, p3);
		vertex(buffer, matrix, color, p4);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix, int color, Vec3 p) {
		buffer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(color);
	}
}
