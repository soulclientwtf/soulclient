package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import thunder.hack.ThunderHack;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

import java.util.Random;

public class TridentFly extends Module {
    public enum modeBypass {
        Vulcan, Grim, Both
    }

    private final Setting<modeBypass> bypassMode = new Setting<>("BypassMode", modeBypass.Grim);
    private final Setting<Float> speedMultiplier = new Setting<>("SpeedMultiplier", 1.05f, 1.0f, 1.2f);
    private final Setting<Float> timerSpeed = new Setting<>("TimerSpeed", 1.0f, 0.9f, 1.2f);

    private float currentSpeedMultiplier = 1.0f;
    private final Random random = new Random();

    public TridentFly() {
        super("TridentFly", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        ThunderHack.TICK_TIMER = timerSpeed.getValue();
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1.0f;
    }

    @EventHandler
    public void onTick(EventTick event) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (bypassMode.getValue() == modeBypass.Grim) {
            handleGrimMode(player);
        } else {
            handleVulcanMode(player);
        }
    }

    private void handleVulcanMode(ClientPlayerEntity player) {
        adjustVelocity(player, speedMultiplier.getValue());
    }

    private void handleGrimMode(ClientPlayerEntity player) {
        if (Math.abs(player.getVelocity().y) > 0.1) {
            float randomizedSpeed = speedMultiplier.getValue() + random.nextFloat() * 0.01f;
            adjustVelocity(player, MathHelper.clamp(currentSpeedMultiplier + 0.01f, 1.0f, randomizedSpeed));
            player.fallDistance = 0;

            if (player.horizontalCollision) {
                player.setVelocity(
                        player.getVelocity().x * 0.95,
                        player.getVelocity().y,
                        player.getVelocity().z * 0.95
                );
            }
        } else {
            currentSpeedMultiplier = 1.0f;
        }

        if (random.nextInt(100) > 95) {
            ThunderHack.TICK_TIMER = timerSpeed.getValue() + random.nextFloat() * 0.02f;
        }
    }

    private void adjustVelocity(ClientPlayerEntity player, float multiplier) {
        player.setVelocity(
                player.getVelocity().x * multiplier,
                player.getVelocity().y,
                player.getVelocity().z * multiplier
        );
    }
}
