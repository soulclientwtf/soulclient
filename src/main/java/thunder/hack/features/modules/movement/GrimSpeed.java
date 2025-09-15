package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import thunder.hack.ThunderHack;
import thunder.hack.core.Managers;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.EventPlayerTravel;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.combat.Aura;
import thunder.hack.setting.Setting;
import thunder.hack.utility.player.MovementUtility;

import static thunder.hack.utility.player.MovementUtility.isMoving;

public class GrimSpeed extends Module {
    public GrimSpeed() {
        super("GrimSpeed", Category.MOVEMENT);
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.Distance);
    public Setting<Boolean> pauseInLiquids = new Setting<>("PauseInLiquids", false);
    public Setting<Boolean> pauseWhileSneaking = new Setting<>("PauseWhileSneaking", false);

    // Distance mode settings
    public final Setting<Boolean> onlyPlayers = new Setting<>("OnlyPlayers", false);
    public final Setting<Float> speedFactor = new Setting<>("Speed", 8.0f, 1.0f, 15.0f);
    public final Setting<Float> distance = new Setting<>("Distance", 3.0f, 0.5f, 5.0f);
    public final Setting<Boolean> predictMovement = new Setting<>("PredictMovement", true);
    public final Setting<Float> predictionFactor = new Setting<>("PredictionFactor", 2.0f, 1.0f, 5.0f);
    public final Setting<Boolean> smoothMovement = new Setting<>("SmoothMovement", true);

    // Distance mode variables
    private Entity targetEntity = null;
    private Vec3d lastTargetPos = null;
    private Vec3d predictedPos = null;
    private double[] lastMotion = new double[]{0.0, 0.0};

    public enum Mode {
        Distance
    }

    // Distance mode methods
    private void updateTarget() {
        if (Aura.target == null) {
            targetEntity = null;
            lastTargetPos = null;
            predictedPos = null;
            return;
        }

        targetEntity = Aura.target;
        if (targetEntity != null) {
            if (lastTargetPos == null) {
                predictedPos = lastTargetPos = targetEntity.getPos();
            } else {
                Vec3d currentPos = targetEntity.getPos();
                Vec3d velocity = new Vec3d(
                        currentPos.x - lastTargetPos.x,
                        currentPos.y - lastTargetPos.y,
                        currentPos.z - lastTargetPos.z
                );

                if (predictMovement.getValue()) {
                    predictedPos = currentPos.add(
                            velocity.x * predictionFactor.getValue(),
                            velocity.y * predictionFactor.getValue(),
                            velocity.z * predictionFactor.getValue()
                    );

                    if (!mc.world.isChunkLoaded((int)predictedPos.x >> 4, (int)predictedPos.z >> 4)) {
                        predictedPos = currentPos;
                    }
                } else {
                    predictedPos = currentPos;
                }
                lastTargetPos = currentPos;
            }
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (entity instanceof PlayerEntity && Managers.FRIEND.isFriend((PlayerEntity)entity)) return false;
        if (onlyPlayers.getValue() && !(entity instanceof PlayerEntity)) return false;
        return entity instanceof LivingEntity || entity instanceof BoatEntity;
    }

    private void handleDistanceMode() {
        if (targetEntity == null) return;
        if (mc.player.hurtTime > 0) return;
        if (!MovementUtility.isMoving()) return;

        Vec3d targetPos = (predictMovement.getValue() && predictedPos != null) ? predictedPos : targetEntity.getPos();
        double dist = mc.player.squaredDistanceTo(targetPos);

        if (dist <= (distance.getValue() * distance.getValue())) {
            float slipperiness = mc.world.getBlockState(mc.player.getVelocityAffectingPos()).getBlock().getSlipperiness();
            float horizontalFriction = mc.player.isOnGround() ? slipperiness * 0.91f : 0.91f;
            float verticalFriction = mc.player.isOnGround() ? slipperiness : 0.99f;

            double actualSpeed = speedFactor.getValue() * 0.01 * horizontalFriction * verticalFriction;
            double[] directionMotion = getDirectionToPoint(mc.player.getPos(), targetPos, actualSpeed);

            if (smoothMovement.getValue()) {
                double accelFactor = 0.6;
                directionMotion[0] = lastMotion[0] + (directionMotion[0] - lastMotion[0]) * accelFactor;
                directionMotion[1] = lastMotion[1] + (directionMotion[1] - lastMotion[1]) * accelFactor;
            }

            lastMotion[0] = directionMotion[0];
            lastMotion[1] = directionMotion[1];
            mc.player.addVelocity(directionMotion[0], 0.0, directionMotion[1]);
        }
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double speed) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len == 0.0) {
            return new double[]{0.0, 0.0};
        }

        return new double[]{dx / len * speed, dz / len * speed};
    }

    @Override
    public void onDisable() {
        ThunderHack.TICK_TIMER = 1f;
    }

    @Override
    public void onEnable() {
        targetEntity = null;
        lastTargetPos = null;
        predictedPos = null;
        lastMotion = new double[]{0.0, 0.0};
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mc.player.isInFluid() && pauseInLiquids.getValue() || mc.player.isSneaking() && pauseWhileSneaking.getValue()) {
            return;
        }
    }

    @EventHandler
    public void modifyVelocity(EventPlayerTravel e) {
        if (mode.getValue() == Mode.Distance && !e.isPre() && ThunderHack.core.getSetBackTime() > 1000) {
            updateTarget();
            handleDistanceMode();
        }
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (mc.player.isInFluid() && pauseInLiquids.getValue() || mc.player.isSneaking() && pauseWhileSneaking.getValue()) {
            return;
        }
    }
}