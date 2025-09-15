package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.combat.Aura;
import thunder.hack.setting.Setting;

import java.lang.reflect.Field;
import java.util.Random;

public class Newlocity extends Module {
    public Setting<Boolean> onlyAura = new Setting<>("OnlyDuringAura", false);
    public Setting<Boolean> pauseInWater = new Setting<>("PauseInLiquids", false);
    public Setting<Boolean> explosions = new Setting<>("Explosions", true);
    public Setting<Boolean> cc = new Setting<>("PauseOnFlag", false);
    public Setting<Boolean> fire = new Setting<>("PauseOnFire", false);
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Cancel);

    private final Random random = new Random();
    private int ccCooldown;
    private long freezeStartTime;
    private boolean isFrozen;
    private int duelCounter = 0;
    private long lastDuelTime = 0;

    public Newlocity() {
        super("GrimVelocity", Module.Category.MOVEMENT);
    }

    // Метод для изменения velocity через рефлексию
    private void setVelocityMotion(EntityVelocityUpdateS2CPacket packet, int motionX, int motionY, int motionZ) {
        try {
            Field velocityX = packet.getClass().getDeclaredField("velocityX");
            Field velocityY = packet.getClass().getDeclaredField("velocityY");
            Field velocityZ = packet.getClass().getDeclaredField("velocityZ");
            velocityX.setAccessible(true);
            velocityY.setAccessible(true);
            velocityZ.setAccessible(true);
            velocityX.setInt(packet, motionX);
            velocityY.setInt(packet, motionY);
            velocityZ.setInt(packet, motionZ);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Метод для изменения explosion motion через рефлексию
    private void setExplosionMotion(ExplosionS2CPacket explosion, float motionX, float motionY, float motionZ) {
        try {
            Field fieldX = explosion.getClass().getDeclaredField("playerVelocityX");
            Field fieldY = explosion.getClass().getDeclaredField("playerVelocityY");
            Field fieldZ = explosion.getClass().getDeclaredField("playerVelocityZ");
            fieldX.setAccessible(true);
            fieldY.setAccessible(true);
            fieldZ.setAccessible(true);
            fieldX.setFloat(explosion, motionX);
            fieldY.setFloat(explosion, motionY);
            fieldZ.setFloat(explosion, motionZ);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive e) {
        if (fullNullCheck()) return;
        if (shouldPause()) return;

        if (System.currentTimeMillis() - lastDuelTime > 10000) {
            duelCounter = 0;
        }
        lastDuelTime = System.currentTimeMillis();

        if (ccCooldown > 0) {
            ccCooldown--;
            return;
        }

        if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket pac && pac.getId() == mc.player.getId()) {
            handleVelocityPacket(e, pac);
        } else if (e.getPacket() instanceof ExplosionS2CPacket explosion) {
            handleExplosionPacket(e, explosion);
        }

        if (mode.getValue() == Mode.GrimFreeze && isFrozen && e.getPacket() instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
            if (duelCounter < 2 || random.nextFloat() > 0.1f) {
                e.cancel();
            }
        }

        if (e.getPacket() instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) {
            duelCounter++;
            ccCooldown = 2 + random.nextInt(3);
        }
    }

    private void handleVelocityPacket(PacketEvent.Receive e, EntityVelocityUpdateS2CPacket pac) {
        if (!onlyAura.getValue() || ModuleManager.aura.isEnabled()) {
            switch (mode.getValue()) {
                case Cancel -> {
                    float variationX = 0.8f + random.nextFloat() * 0.4f;
                    float variationY = 0.7f + random.nextFloat() * 0.6f;
                    float variationZ = 0.8f + random.nextFloat() * 0.4f;

                    setVelocityMotion(pac,
                            (int) (pac.getVelocityX() * variationX),
                            (int) (pac.getVelocityY() * variationY),
                            (int) (pac.getVelocityZ() * variationZ));
                }
                case GrimFreeze -> {
                    if (duelCounter < 2 || random.nextFloat() > 0.15f) {
                        e.cancel();
                        startFreeze();
                    }
                }
            }
        }
    }

    private void handleExplosionPacket(PacketEvent.Receive e, ExplosionS2CPacket explosion) {
        if (explosions.getValue()) {
            switch (mode.getValue()) {
                case Cancel -> {
                    float variation = 0.05f + random.nextFloat() * 0.2f;
                    setExplosionMotion(explosion,
                            explosion.getPlayerVelocityX() * variation,
                            explosion.getPlayerVelocityY() * variation,
                            explosion.getPlayerVelocityZ() * variation);
                }
                case GrimFreeze -> {
                    setExplosionMotion(explosion, 0, 0, 0);
                    if (duelCounter < 2 || random.nextFloat() > 0.2f) {
                        startFreeze();
                    }
                }
            }
        }
    }

    private void startFreeze() {
        isFrozen = true;
        freezeStartTime = System.currentTimeMillis() - random.nextInt(500);
    }

    @Override
    public void onUpdate() {
        if (shouldPause()) return;

        if (mode.getValue() == Mode.GrimFreeze) {
            if (isFrozen && System.currentTimeMillis() - freezeStartTime >= (300 + random.nextInt(500))) {
                isFrozen = false;
            }
        }
    }

    @EventHandler
    public void onPlayerUpdate(PlayerUpdateEvent e) {
        if (mode.getValue() == Mode.GrimFreeze && ModuleManager.aura.isEnabled()) {
            if (Aura.target != null && mc.player.hurtTime > 0) {
                if (duelCounter < 2 || random.nextFloat() > 0.25f) {
                    startFreeze();
                }
            }
        }
    }

    private boolean shouldPause() {
        return (mc.player.isTouchingWater() || mc.player.isInLava()) && pauseInWater.getValue() ||
                mc.player.isOnFire() && fire.getValue() && mc.player.getFireTicks() > 0;
    }

    public enum Mode {
        Cancel,
        GrimFreeze
    }
}