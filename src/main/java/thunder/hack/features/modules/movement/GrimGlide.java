package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.MovementUtility;

import static thunder.hack.utility.math.MathUtility.random;

public class GrimGlide extends Module {
    public final Setting<Float> speed = new Setting<>("Speed", 0.174f, 0.01f, 0.4f);
    public final Setting<Float> motionThreshold = new Setting<>("MotionThreshold", 96f, 20f, 200f);
    public final Setting<Float> yawMultiplier = new Setting<>("YawMultiplier", 2.2f, 0.5f, 4.0f);
    public final Setting<Float> yawRandom = new Setting<>("YawRandom", 0.22f, 0.01f, 1.0f);
    public final Setting<Float> yVelocity = new Setting<>("YVelocity", -0.04f, -0.1f, 0.1f);
    public final Setting<Float> yVelocityAdd = new Setting<>("YVelocityAdd", 0.032f, 0.001f, 0.2f);
    public final Setting<Integer> teleportDelay = new Setting<>("TeleportDelay", 25, 5, 100);
    public final Setting<Boolean> randomize = new Setting<>("Randomize", true);
    public final Setting<Boolean> serverCheck = new Setting<>("ServerCheck", true);

    private final Timer teleportTimer = new Timer();
    private int ticks = 0;

    public GrimGlide() {
        super("GrimGlide", Category.MOVEMENT);
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent event) {
        if (fullNullCheck()) return;
        if (!mc.player.isFallFlying()) return;

        ticks++;
        Vec3d pos = mc.player.getPos();

        float yaw = mc.player.getYaw();
        double forward = speed.getValue();
        double motion = MovementUtility.getSpeed();

        // Проверка скорости для остановки
        float threshold = serverCheck.getValue() ? 96f : motionThreshold.getValue();
        if (motion >= threshold) {
            forward = 0f;
            motion = 0;
        }

        // Вычисление направления движения
        double dx = -Math.sin(Math.toRadians(yaw)) * forward;
        double dz = Math.cos(Math.toRadians(yaw)) * forward;

        // Применение рандомизации
        double multiplier = yawMultiplier.getValue();
        if (randomize.getValue()) {
            multiplier += random(-yawRandom.getValue(), yawRandom.getValue());
        }

        // Установка скорости
        mc.player.setVelocity(
            dx * multiplier,
            mc.player.getVelocity().y + yVelocity.getValue(),
            dz * multiplier
        );

        // Телепортация с задержкой
        if (teleportTimer.passedMs(teleportDelay.getValue())) {
            mc.player.setPosition(
                pos.getX() + dx,
                pos.getY(),
                pos.getZ() + dz
            );
            teleportTimer.reset();
        }

        // Дополнительная установка скорости
        mc.player.setVelocity(
            dx * multiplier,
            mc.player.getVelocity().y + yVelocityAdd.getValue(),
            dz * multiplier
        );
    }

    @Override
    public void onEnable() {
        teleportTimer.reset();
        ticks = 0;
    }

    @Override
    public void onDisable() {
        ticks = 0;
    }
}
