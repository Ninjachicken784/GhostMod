package com.ghostmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks Ghost kills, misses, longer paths and efficiency.
 *
 * Kills come from the "Kills: X" line (Book of Stats) on the item in your main hand.
 * Ghosts are Creeper entities.
 *
 * Efficiency: every time a Ghost becomes your target, the straight-line distance to it is the "ideal"
 * distance (minus your reach). The distance you actually walk until the kill is the "actual" distance.
 * Efficiency = sum(ideal) / sum(max(ideal, actual)). 100% means a perfect line to every target.
 */
public final class GhostTracker {
	public static final GhostTracker INSTANCE = new GhostTracker();

	private static final double MAX_RANGE = 48.0; // Ghosts further away than this are ignored
	private static final double REACH = 3.0; // melee reach, removed from the ideal distance
	private static final double MISS_RANGE = 8.0; // a swing only counts as a miss if a Ghost is this close
	private static final double KILL_MATCH_RANGE = 20.0; // a vanished Ghost must have been this close to count as the kill
	private static final long MATCH_WINDOW_MS = 3000;
	private static final double LONGER_PATH_MARGIN = 1.5; // blocks

	private static final Pattern KILLS = Pattern.compile("(?i)\\bkills:\\s*([\\d,]+)");
	private static final Pattern COLOR_CODES = Pattern.compile("§.");

	private static final class Segment {
		final Vec3 start;
		final double dist0; // distance to the target when it was locked
		final double ideal; // dist0 minus reach
		final int targetId;
		double actual; // distance walked since the target was locked

		Segment(Vec3 start, double dist0, int targetId) {
			this.start = start;
			this.dist0 = dist0;
			this.ideal = Math.max(0.0, dist0 - REACH);
			this.targetId = targetId;
		}
	}

	private record GoneGhost(int id, Vec3 pos, long at) { }

	// Stats
	private int kills;
	private int misses;
	private int longerPaths;
	private int segmentCount;
	private double idealSum;
	private double denominatorSum;
	private double activeMs;

	// Clock / pause
	private boolean clockRunning;
	private boolean paused;
	private long lastTickMs;
	private long lastActivityMs;

	// Ghosts and target
	private final Map<Integer, Vec3> lastGhostPos = new HashMap<>();
	private final List<GoneGhost> gone = new ArrayList<>();
	private Entity target;
	private Segment segment;
	private Segment pending; // segment of a target that just vanished, waiting for the kill to show up
	private long pendingAt;
	private double nearestDist = Double.MAX_VALUE;
	private Vec3 lastPlayerPos;

	// Kill detection
	private final Map<String, Integer> lastKills = new HashMap<>();
	private boolean weaponHeld;
	private boolean scoreWanted;
	private long scoreAt;

	private GhostTracker() { }

	// ---------------------------------------------------------------- public stats

	public int kills() {
		return kills;
	}

	public int misses() {
		return misses;
	}

	public int longerPaths() {
		return longerPaths;
	}

	public boolean isPaused() {
		return paused;
	}

	/** The Ghost the line points at, or null. */
	public Entity target() {
		return target;
	}

	/** 0 until the first Ghost has been killed, then 0-100. */
	public double efficiencyPercent() {
		if (segmentCount == 0) return 0.0;
		if (denominatorSum <= 0.0) return 100.0;
		return Math.min(100.0, 100.0 * idealSum / denominatorSum);
	}

	public double killsPerHour() {
		return perHour(kills);
	}

	public double missesPerHour() {
		return perHour(misses);
	}

	public double longerPathsPerHour() {
		return perHour(longerPaths);
	}

	private double perHour(int count) {
		double hours = activeMs / 3_600_000.0;
		if (hours < 1.0 / 3600.0) return 0.0; // under a second of data
		return count / hours;
	}

	public void reset() {
		kills = 0;
		misses = 0;
		longerPaths = 0;
		segmentCount = 0;
		idealSum = 0;
		denominatorSum = 0;
		activeMs = 0;
		clockRunning = false;
		paused = false;
		target = null;
		segment = null;
		pending = null;
		scoreWanted = false;
		gone.clear();
	}

	// ---------------------------------------------------------------- events

	/** Called when you left-click. Counts a miss when you swing at air with a Ghost close by. */
	public void onAttackClick(Minecraft mc) {
		if (!weaponHeld || paused || !clockRunning) return;
		if (nearestDist > MISS_RANGE) return;
		HitResult hit = mc.hitResult;
		if (hit == null || hit.getType() != HitResult.Type.MISS) return;
		misses++;
	}

	// ---------------------------------------------------------------- tick

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			leaveWorld();
			return;
		}

		long now = System.currentTimeMillis();
		long dt = lastTickMs == 0 ? 0 : Math.min(now - lastTickMs, 250);
		lastTickMs = now;
		Vec3 pos = player.position();

		// 1. Find all Ghosts around you.
		List<Entity> ghosts = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getType() != EntityType.CREEPER || !e.isAlive()) continue;
			if (e.position().distanceTo(pos) > MAX_RANGE) continue;
			ghosts.add(e);
			ids.add(e.getId());
		}

		// 2. Remember Ghosts that just vanished (probably killed).
		Iterator<Map.Entry<Integer, Vec3>> it = lastGhostPos.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Vec3> entry = it.next();
			if (!ids.contains(entry.getKey())) {
				gone.add(new GoneGhost(entry.getKey(), entry.getValue(), now));
				it.remove();
			}
		}
		for (Entity g : ghosts) {
			lastGhostPos.put(g.getId(), g.position());
		}
		gone.removeIf(g -> now - g.at() > MATCH_WINDOW_MS);

		// 3. Your target vanished: keep its segment until the kill shows up in the weapon lore.
		if (target != null && !ids.contains(target.getId())) {
			if (segment != null && !paused) {
				pending = segment;
				pendingAt = now;
			}
			target = null;
			segment = null;
		}
		if (pending != null && now - pendingAt > MATCH_WINDOW_MS) {
			pending = null;
		}

		// 4. Distance you walked towards the current target.
		if (lastPlayerPos != null && segment != null && !paused) {
			double d = pos.distanceTo(lastPlayerPos);
			if (d < 10.0) { // ignore teleports
				segment.actual += d;
			}
		}
		lastPlayerPos = pos;

		// 5. Lock onto the nearest Ghost.
		Entity nearest = null;
		double nearestD = Double.MAX_VALUE;
		for (Entity g : ghosts) {
			double d = g.position().distanceTo(pos);
			if (d < nearestD) {
				nearestD = d;
				nearest = g;
			}
		}
		nearestDist = nearest == null ? Double.MAX_VALUE : nearestD;
		if (target == null && nearest != null) {
			target = nearest;
			segment = new Segment(pos, nearestD, nearest.getId());
			if (!clockRunning) {
				clockRunning = true;
				lastActivityMs = now;
			}
		}

		// 6. Read kills from the weapon in your hand.
		ItemStack held = player.getMainHandItem();
		int killCount = readKills(held);
		weaponHeld = killCount >= 0;
		if (weaponHeld) {
			String key = held.getHoverName().getString();
			Integer previous = lastKills.put(key, killCount);
			if (previous != null && killCount > previous && killCount - previous <= 50) {
				registerKills(killCount - previous, now);
			}
		}
		if (scoreWanted) {
			if (tryScore(pos) || now - scoreAt > MATCH_WINDOW_MS) {
				scoreWanted = false;
			}
		}

		// 7. Clock and idle pause.
		if (clockRunning && !paused) {
			activeMs += dt;
			long pauseMs = GhostConfig.get().pauseSeconds * 1000L;
			if (now - lastActivityMs > pauseMs) {
				paused = true;
				activeMs = Math.max(0.0, activeMs - pauseMs); // the idle time does not count
			}
		}
	}

	private void leaveWorld() {
		lastTickMs = 0;
		lastPlayerPos = null;
		target = null;
		segment = null;
		pending = null;
		scoreWanted = false;
		lastGhostPos.clear();
		gone.clear();
		lastKills.clear();
		weaponHeld = false;
		nearestDist = Double.MAX_VALUE;
	}

	private void registerKills(int count, long now) {
		kills += count;
		clockRunning = true;
		lastActivityMs = now;
		if (paused) {
			// First kill after an idle pause resumes the tracker. The idle stretch is not scored.
			paused = false;
			target = null;
			segment = null;
			pending = null;
			scoreWanted = false;
		} else {
			scoreWanted = true;
			scoreAt = now;
		}
	}

	/** Matches the kill to the Ghost that vanished and scores the segment. Returns true when handled. */
	private boolean tryScore(Vec3 playerPos) {
		GoneGhost killed = null;
		for (GoneGhost g : gone) {
			if (g.pos().distanceTo(playerPos) > KILL_MATCH_RANGE) continue;
			if (killed == null || g.at() > killed.at()) {
				killed = g;
			}
		}
		if (killed == null) return false;
		gone.remove(killed);

		Segment scored;
		if (pending != null && pending.targetId == killed.id()) {
			// You killed the Ghost you were walking to.
			scored = pending;
			pending = null;
		} else if (segment != null) {
			// You killed a different Ghost than the one the line pointed at.
			scored = segment;
			if (scored.start.distanceTo(killed.pos()) > scored.dist0 + LONGER_PATH_MARGIN) {
				longerPaths++;
			}
			target = null;
			segment = null;
		} else {
			return true;
		}

		idealSum += scored.ideal;
		denominatorSum += Math.max(scored.ideal, scored.actual);
		segmentCount++;
		return true;
	}

	private static int readKills(ItemStack stack) {
		if (stack.isEmpty()) return -1;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return -1;
		for (Component line : lore.lines()) {
			String text = COLOR_CODES.matcher(line.getString()).replaceAll("");
			Matcher m = KILLS.matcher(text);
			if (m.find()) {
				try {
					return Integer.parseInt(m.group(1).replace(",", ""));
				} catch (NumberFormatException ignored) {
					// keep looking
				}
			}
		}
		return -1;
	}
}
