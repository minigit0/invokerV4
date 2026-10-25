package com.invokermod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Вся серверная логика: сферы, кулдауны, заклинания. */
public final class InvokerManager {
    private InvokerManager() {}

    static final class State {
        final int[] orbs = {-1, -1};          // [0] = первый (новый) слот, [1] = второй
        final long[] cdUntil = new long[6];
        long noFallUntil = 0;
    }

    interface Effect {
        /** @return true, если эффект закончился */
        boolean tick(long now);
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static long now = 0;

    private static State state(UUID id) {
        return STATES.computeIfAbsent(id, k -> new State());
    }

    // ------------------------------------------------------------------ жизненный цикл

    public static void clearAll() {
        STATES.clear();
        EFFECTS.clear();
    }

    public static void remove(ServerPlayerEntity p) {
        STATES.remove(p.getUuid());
    }

    public static void reset(ServerPlayerEntity p) {
        STATES.remove(p.getUuid());
        sync(p);
    }

    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof ServerPlayerEntity p && source.isOf(DamageTypes.FALL)) {
            State s = STATES.get(p.getUuid());
            if (s != null && now < s.noFallUntil) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ сферы

    private static boolean hasBook(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(InvokerMod.SPELLBOOK) || st.isOf(InvokerMod.SUPER_SPELLBOOK)) return true;
        }
        return false;
    }

    public static void addOrb(ServerPlayerEntity p, int type) {
        if (!hasBook(p)) {
            p.sendMessage(Text.literal("Нужна книга инвокера в инвентаре"), true);
            return;
        }
        State s = state(p.getUuid());
        s.orbs[1] = s.orbs[0];
        s.orbs[0] = type;
        sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        State s = state(p.getUuid());
        List<Integer> d = new ArrayList<>();
        d.add(s.orbs[0]);
        d.add(s.orbs[1]);
        for (int i = 0; i < 6; i++) d.add((int) Math.max(0, s.cdUntil[i] - now));
        ServerPlayNetworking.send(p, new SyncPayload(d));
    }

    // ------------------------------------------------------------------ каст

    public static boolean cast(ServerPlayerEntity p, ServerWorld w) {
        now = w.getServer().getTicks();
        State s = state(p.getUuid());
        int idx = Spells.index(s.orbs[0], s.orbs[1]);
        if (idx < 0) {
            p.sendMessage(Text.literal("Нужно две сферы"), true);
            return false;
        }
        if (now < s.cdUntil[idx]) {
            long sec = (s.cdUntil[idx] - now + 19) / 20;
            p.sendMessage(Text.literal(Spells.NAMES[idx] + ": перезарядка " + sec + " с"), true);
            return false;
        }
        switch (idx) {
            case Spells.FIREBALL -> castFireball(p, w);
            case Spells.WIND -> castWind(p, w);
            case Spells.STEAM -> castSteam(p, w);
            case Spells.WALL -> castWall(p, w);
            case Spells.RAIN -> castRain(p, w);
            case Spells.DASH -> castDash(p, w, s);
            default -> {
                return false;
            }
        }
        s.cdUntil[idx] = now + Spells.COOLDOWNS[idx];
        p.sendMessage(Text.literal(Spells.NAMES[idx]), true);
        sync(p);
        return true;
    }

    // ------------------------------------------------------------------ тик

    public static void tick(MinecraftServer server) {
        now = server.getTicks();

        Iterator<Effect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            if (it.next().tick(now)) it.remove();
        }

        if (now % 2 == 0) {
            double t = now * 0.3;
            for (ServerWorld w : server.getWorlds()) {
                for (ServerPlayerEntity p : w.getPlayers()) {
                    State s = STATES.get(p.getUuid());
                    if (s == null || !p.isAlive()) continue;
                    for (int i = 0; i < 2; i++) {
                        int orb = s.orbs[i];
                        if (orb < 0) continue;
                        double a = t + i * Math.PI;
                        double x = p.getX() + Math.cos(a) * 0.6;
                        double y = p.getEyeY() + 0.35;
                        double z = p.getZ() + Math.sin(a) * 0.6;
                        ParticleEffect fx = orb == Spells.FIRE ? ParticleTypes.FLAME
                                : orb == Spells.WATER ? ParticleTypes.SPLASH : ParticleTypes.CLOUD;
                        w.spawnParticles(fx, x, y, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ помощники

    private static Vec3d look(ServerPlayerEntity p) {
        return p.getRotationVec(1.0F).normalize();
    }

    private static Vec3d horizontalLook(ServerPlayerEntity p) {
        Vec3d l = p.getRotationVec(1.0F);
        Vec3d h = new Vec3d(l.x, 0, l.z);
        if (h.lengthSquared() < 1.0E-4) {
            float yaw = p.getYaw() * ((float) Math.PI / 180F);
            return new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return h.normalize();
    }

    private static void sound(ServerWorld w, double x, double y, double z, SoundEvent s, float pitch) {
        w.playSound((PlayerEntity) null, x, y, z, s, SoundCategory.PLAYERS, 1.0F, pitch);
    }

    private static void push(Entity e, Vec3d v) {
        e.addVelocity(v.x, v.y, v.z);
        if (e instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
        }
    }

    private static void setVel(ServerPlayerEntity p, double x, double y, double z) {
        p.setVelocity(x, y, z);
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    private static void hurt(ServerWorld w, LivingEntity e, ServerPlayerEntity src, float amount) {
        e.damage(w, w.getDamageSources().playerAttack(src), amount);
    }

    private static List<LivingEntity> cone(ServerWorld w, ServerPlayerEntity p, Vec3d dir, double range, double minDot) {
        double ex = p.getX(), ey = p.getEyeY(), ez = p.getZ();
        Box box = new Box(ex - range, ey - range, ez - range, ex + range, ey + range, ez + range);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> x != p && x.isAlive())) {
            Vec3d to = new Vec3d(e.getX() - ex, e.getBodyY(0.5) - ey, e.getZ() - ez);
            double d = to.length();
            if (d < 0.001 || d > range) continue;
            if (to.multiply(1.0 / d).dotProduct(dir) >= minDot) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ огонь + огонь: фаербол

    private static final double FIREBALL_SPEED = 1.6;

    private static void castFireball(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = look(p);
        Vec3d vel = dir.multiply(FIREBALL_SPEED);
        FireballEntity fb = new FireballEntity(w, p, vel, 1);
        fb.setPosition(p.getX() + dir.x * 1.2, p.getEyeY() - 0.1 + dir.y * 1.2, p.getZ() + dir.z * 1.2);
        w.spawnEntity(fb);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, 0.8F);

        final long end = now + 80;
        EFFECTS.add(t -> {
            if (fb.isRemoved()) return true;
            if (t >= end) {
                fb.discard();
                return true;
            }
            // "нельзя отбить": каждый тик возвращаем исходную скорость и владельца
            fb.setVelocity(vel);
            fb.setOwner(p);
            return false;
        });
    }

    // ------------------------------------------------------------------ огонь + воздух: заряд ветра

    private static void castWind(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = look(p);
        for (LivingEntity e : cone(w, p, dir, 7.0, 0.8)) {
            hurt(w, e, p, 3.0F);
            e.setOnFireFor(4);
            push(e, dir.multiply(1.4).add(0, 0.35, 0));
        }
        // отдача по кастеру: без урона и без поджога
        push(p, dir.multiply(-1.0).add(0, 0.25, 0));

        for (int i = 1; i <= 7; i++) {
            w.spawnParticles(ParticleTypes.CLOUD, p.getX() + dir.x * i, p.getEyeY() + dir.y * i, p.getZ() + dir.z * i,
                    3, 0.2, 0.2, 0.2, 0.02);
            w.spawnParticles(ParticleTypes.FLAME, p.getX() + dir.x * i, p.getEyeY() + dir.y * i, p.getZ() + dir.z * i,
                    2, 0.2, 0.2, 0.2, 0.01);
        }
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BREEZE_SHOOT, 1.0F);
    }

    // ------------------------------------------------------------------ огонь + вода: паровой залп

    private static void castSteam(ServerPlayerEntity p, ServerWorld w) {
        final long end = now + 100;
        final float[] lastHealth = {p.getHealth()};
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8F);
        EFFECTS.add(t -> {
            if (!p.isAlive() || p.isRemoved()) return true;
            if (p.getHealth() < lastHealth[0] - 0.001F) {
                p.sendMessage(Text.literal("Паровой залп прерван"), true);
                return true;
            }
            lastHealth[0] = p.getHealth();
            if (t >= end) return true;

            Vec3d dir = look(p);
            for (double d = 1.0; d <= 6.0; d += 1.5) {
                w.spawnParticles(ParticleTypes.CLOUD, p.getX() + dir.x * d, p.getEyeY() - 0.2 + dir.y * d,
                        p.getZ() + dir.z * d, 3, 0.3, 0.3, 0.3, 0.02);
            }
            if (t % 5 == 0) {
                for (LivingEntity e : cone(w, p, dir, 6.0, 0.85)) {
                    hurt(w, e, p, 2.0F);
                    push(e, dir.multiply(0.6).add(0, 0.1, 0));
                }
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ зоны замедления

    private abstract static class Zone implements Effect {
        final ServerWorld w;
        final long end;
        final Identifier modId;
        final double slow;
        final Entity exclude;
        final Set<UUID> affected = new HashSet<>();

        Zone(ServerWorld w, long end, Identifier modId, double slow, Entity exclude) {
            this.w = w;
            this.end = end;
            this.modId = modId;
            this.slow = slow;
            this.exclude = exclude;
        }

        abstract boolean inside(Entity e);

        abstract Box bounds();

        abstract void particles(long t);

        boolean alive() {
            return true;
        }

        @Override
        public boolean tick(long t) {
            boolean done = t >= end || !alive();
            Set<UUID> current = new HashSet<>();
            if (!done) {
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, bounds(),
                        x -> x != exclude && x.isAlive())) {
                    if (!inside(e)) continue;
                    EntityAttributeInstance inst = e.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                    if (inst != null && inst.getModifier(modId) == null) {
                        inst.addTemporaryModifier(new EntityAttributeModifier(modId, -slow,
                                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                    current.add(e.getUuid());
                }
                particles(t);
            }
            for (UUID id : affected) {
                if (current.contains(id)) continue;
                Entity e = w.getEntity(id);
                if (e instanceof LivingEntity le) {
                    EntityAttributeInstance inst = le.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                    if (inst != null) inst.removeModifier(modId);
                }
            }
            affected.clear();
            affected.addAll(current);
            return done;
        }
    }

    // ------------------------------------------------------------------ вода + вода: стена

    private static final Identifier WALL_MOD = Identifier.of(InvokerMod.MOD_ID, "wall_slow");

    private static void castWall(ServerPlayerEntity p, ServerWorld w) {
        Vec3d fwd = horizontalLook(p);
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
        final double cx = p.getX() + fwd.x * 3.0, cy = p.getY(), cz = p.getZ() + fwd.z * 3.0;
        final double halfW = 3.5, height = 3.0, halfT = 0.75;
        sound(w, cx, cy, cz, SoundEvents.ENTITY_PLAYER_SPLASH, 0.8F);

        EFFECTS.add(new Zone(w, now + 100, WALL_MOD, 0.75, p) {
            @Override
            boolean inside(Entity e) {
                double dx = e.getX() - cx, dz = e.getZ() - cz, dy = e.getY() - cy;
                double along = dx * right.x + dz * right.z;
                double thick = dx * fwd.x + dz * fwd.z;
                return Math.abs(along) <= halfW + 0.3 && Math.abs(thick) <= halfT + 0.3 && dy >= -1.0 && dy <= height;
            }

            @Override
            Box bounds() {
                return new Box(cx - 5, cy - 1, cz - 5, cx + 5, cy + 4, cz + 5);
            }

            @Override
            void particles(long t) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 14; i++) {
                    double along = (r.nextDouble() * 2 - 1) * halfW;
                    double thick = (r.nextDouble() * 2 - 1) * halfT;
                    double y = cy + r.nextDouble() * height;
                    double x = cx + right.x * along + fwd.x * thick;
                    double z = cz + right.z * along + fwd.z * thick;
                    w.spawnParticles(i % 2 == 0 ? ParticleTypes.SPLASH : ParticleTypes.FALLING_WATER,
                            x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        });
    }

    // ------------------------------------------------------------------ вода + воздух: дождь

    private static final Identifier RAIN_MOD = Identifier.of(InvokerMod.MOD_ID, "rain_slow");

    private static void castRain(ServerPlayerEntity p, ServerWorld w) {
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.WEATHER_RAIN, 1.0F);
        EFFECTS.add(new Zone(w, now + 100, RAIN_MOD, 0.70, p) {
            double cx = p.getX(), cy = p.getY(), cz = p.getZ();

            @Override
            boolean alive() {
                if (!p.isAlive() || p.isRemoved()) return false;
                cx = p.getX();
                cy = p.getY();
                cz = p.getZ();
                return true;
            }

            @Override
            boolean inside(Entity e) {
                double dx = Math.abs(e.getX() - cx), dz = Math.abs(e.getZ() - cz), dy = e.getY() - cy;
                return dx <= 4.5 && dz <= 4.5 && dy >= -1.5 && dy <= 6.0;
            }

            @Override
            Box bounds() {
                return new Box(cx - 5, cy - 2, cz - 5, cx + 5, cy + 7, cz + 5);
            }

            @Override
            void particles(long t) {
                w.spawnParticles(ParticleTypes.RAIN, cx, cy + 4.5, cz, 40, 2.2, 0.4, 2.2, 0.0);
                if (t % 4 == 0) {
                    w.spawnParticles(ParticleTypes.SPLASH, cx, cy + 0.1, cz, 10, 2.2, 0.0, 2.2, 0.0);
                }
            }
        });
    }

    // ------------------------------------------------------------------ воздух + воздух: рывок

    private static void castDash(ServerPlayerEntity p, ServerWorld w, State s) {
        final Vec3d f = horizontalLook(p);
        final long start = now;
        s.noFallUntil = Long.MAX_VALUE;
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.2F);

        EFFECTS.add(t -> {
            if (!p.isAlive() || p.isRemoved()) {
                s.noFallUntil = t + 10;
                return true;
            }
            long k = t - start;
            if (k < 5) {
                // 5 тиков по 1.4 блока = ~7 блоков вперёд
                setVel(p, f.x * 1.4, 0.0, f.z * 1.4);
                w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            } else if (k == 5) {
                // прыжок примерно на 4 блока
                setVel(p, f.x * 0.5, 0.805, f.z * 0.5);
            } else if (k >= 8 && p.isOnGround() || k > 120) {
                s.noFallUntil = t + 10;
                return true;
            }
            return false;
        });
    }
}
