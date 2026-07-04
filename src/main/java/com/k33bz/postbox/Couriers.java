package com.k33bz.postbox;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The express-courier scene: when an express letter ARRIVES at a mailbox in a loaded chunk, an
 * "Express Courier" wandering trader with 1-2 trader llamas appears 10-12 blocks out and WALKS
 * to the box (navigation re-issued until close), pauses for the deliver moment (chime + flag
 * refresh), then walks off and vanishes in a poof. All courier entities are invulnerable,
 * persistent, and interaction-blocked (the {@code postbox_courier} tag is consumed by a
 * UseEntityCallback). Pure theater — delivery itself already happened; a timeout or failed
 * path just ends the show early.
 */
public final class Couriers {
    private Couriers() {
    }

    private static final List<Run> RUNS = new ArrayList<>();
    private static int nextRunId = 0;

    private static final int WALK_TIMEOUT_TICKS = 600;   // 30 s each leg
    private static final int PAUSE_TICKS = 30;

    private static final class Run {
        ServerLevel level;
        String runTag;
        Vec3 target;
        Vec3 exit;
        Mob trader;
        List<Mob> llamas = new ArrayList<>();
        int phase; // 0 = inbound walk, 1 = deliver pause, 2 = outbound walk
        int tick;
        Mail.Box box;
    }

    /** Stage the scene at a box (already-verified loaded chunk). */
    public static void start(ServerLevel level, Mail.Box box) {
        Run run = new Run();
        run.level = level;
        run.box = box;
        run.runTag = Mail.COURIER_TAG + "_r" + (nextRunId++);
        run.target = new Vec3(box.x + 0.5, box.y, box.z + 0.5);

        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        double dist = 10.0 + level.getRandom().nextDouble() * 2.0; // ~10-12 blocks out
        double sx = box.x + 0.5 + Math.cos(angle) * dist;
        double sz = box.z + 0.5 + Math.sin(angle) * dist;
        int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(sx), (int) Math.floor(sz));
        run.exit = exitPoint(level, box, angle);

        // Invulnerable, persistent, silent-running theater troupe. DespawnDelay:0 disables the
        // wandering trader's vanilla despawn timer; trades are blocked by the tag callback.
        Mail.run(level, String.format(Locale.ROOT,
                "summon minecraft:wandering_trader %.2f %d %.2f {Tags:[\"%s\",\"%s\"],Invulnerable:1b,"
                        + "PersistenceRequired:1b,DespawnDelay:0,"
                        + "CustomName:{text:\"Express Courier\",color:\"gold\"},CustomNameVisible:1b}",
                sx, sy, sz, Mail.COURIER_TAG, run.runTag));
        int llamas = 1 + level.getRandom().nextInt(2);
        for (int i = 0; i < llamas; i++) {
            Mail.run(level, String.format(Locale.ROOT,
                    "summon minecraft:trader_llama %.2f %d %.2f {Tags:[\"%s\",\"%s\"],Invulnerable:1b,"
                            + "PersistenceRequired:1b}",
                    sx + 1.2 * (i + 1), sy, sz + 0.6 * (i + 1), Mail.COURIER_TAG, run.runTag));
        }

        // Grab live Mob references back by tag (sanctuary's spawnCourier idiom).
        List<Mob> found = level.getEntitiesOfClass(Mob.class,
                new AABB(BlockPos.containing(sx, sy, sz)).inflate(6),
                m -> m.entityTags().contains(run.runTag));
        for (Mob mob : found) {
            if (mob instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader) {
                run.trader = mob;
            } else {
                run.llamas.add(mob);
            }
        }
        if (run.trader == null) {
            cleanup(run); // summon failed somehow — no scene, mail already delivered anyway
            return;
        }
        RUNS.add(run);
    }

    private static Vec3 exitPoint(ServerLevel level, Mail.Box box, double inboundAngle) {
        // Leave roughly the way we came, drifting a bit — ~12 blocks out.
        double angle = inboundAngle + (level.getRandom().nextDouble() - 0.5);
        double ex = box.x + 0.5 + Math.cos(angle) * 12.0;
        double ez = box.z + 0.5 + Math.sin(angle) * 12.0;
        int ey = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ex), (int) Math.floor(ez));
        return new Vec3(ex, ey, ez);
    }

    /** Per-tick animation driver. Cheap: only active runs are touched. */
    public static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) {
            return;
        }
        Iterator<Run> it = RUNS.iterator();
        while (it.hasNext()) {
            Run run = it.next();
            run.tick++;
            if (run.trader == null || run.trader.isRemoved()) {
                cleanup(run);
                it.remove();
                continue;
            }
            switch (run.phase) {
                case 0 -> { // walk to the mailbox
                    if (run.tick % 20 == 1) {
                        run.trader.getNavigation().moveTo(run.target.x, run.target.y, run.target.z, 0.6);
                        for (Mob llama : run.llamas) {
                            if (!llama.isRemoved()) {
                                llama.getNavigation().moveTo(run.trader, 0.7);
                            }
                        }
                    }
                    boolean arrived = run.trader.distanceToSqr(run.target.x, run.trader.getY(), run.target.z) < 4.0;
                    if (arrived || run.tick >= WALK_TIMEOUT_TICKS) {
                        run.phase = 1;
                        run.tick = 0;
                        if (arrived) { // the deliver moment
                            Mailboxes.chime(run.level, run.box);
                            Mailboxes.updateFlag(run.level, run.box);
                        }
                    }
                }
                case 1 -> { // pause at the box
                    if (run.tick >= PAUSE_TICKS) {
                        run.phase = 2;
                        run.tick = 0;
                    }
                }
                case 2 -> { // walk away, then vanish
                    if (run.tick % 20 == 1) {
                        run.trader.getNavigation().moveTo(run.exit.x, run.exit.y, run.exit.z, 0.7);
                        for (Mob llama : run.llamas) {
                            if (!llama.isRemoved()) {
                                llama.getNavigation().moveTo(run.trader, 0.8);
                            }
                        }
                    }
                    boolean away = run.trader.distanceToSqr(run.exit.x, run.trader.getY(), run.exit.z) < 6.25;
                    if (away || run.tick >= WALK_TIMEOUT_TICKS) {
                        cleanup(run);
                        it.remove();
                    }
                }
                default -> {
                    cleanup(run);
                    it.remove();
                }
            }
        }
    }

    /** Poof away every entity of a run (kill by tag catches stragglers). */
    private static void cleanup(Run run) {
        if (run.trader != null && !run.trader.isRemoved()) {
            Mail.run(run.level, String.format(Locale.ROOT,
                    "particle minecraft:poof %.1f %.1f %.1f 0.3 0.5 0.3 0.02 20",
                    run.trader.getX(), run.trader.getY() + 1.0, run.trader.getZ()));
        }
        for (Mob llama : run.llamas) {
            if (!llama.isRemoved()) {
                Mail.run(run.level, String.format(Locale.ROOT,
                        "particle minecraft:poof %.1f %.1f %.1f 0.3 0.5 0.3 0.02 12",
                        llama.getX(), llama.getY() + 0.8, llama.getZ()));
            }
        }
        Mail.run(run.level, "kill @e[tag=" + run.runTag + "]");
    }
}
