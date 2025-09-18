package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import thunder.hack.ThunderHack;
import thunder.hack.core.Core;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.gui.notification.Notification;
import thunder.hack.injection.accesors.ILivingEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.interfaces.IOtherClientPlayerEntity;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;
import thunder.hack.utility.render.animation.CaptureMark;
import thunder.hack.utility.render.animation.PhasmoMark;
import thunder.hack.utility.render.animation.SkullMark;
import thunder.hack.utility.render.animation.RoundedMark;
import thunder.hack.features.modules.combat.Criticals;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.util.UseAction.BLOCK;
import static net.minecraft.util.math.MathHelper.wrapDegrees;
import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.utility.math.MathUtility.random;

public class AuraAI extends Module {
    // Static target for Target ESP and Target HUD
    public static Entity auraAITarget;
    
    public final Setting<Float> attackRange = new Setting<>("Range", 3.0f, 1f, 6.0f);
    public final Setting<Float> wallRange = new Setting<>("ThroughWallsRange", 3.0f, 0f, 6.0f);
    public final Setting<Boolean> randomizeRange = new Setting<>("RandomizeRange", true);
    public final Setting<Float> rangeVariation = new Setting<>("RangeVariation", 0.2f, 0.0f, 0.5f, v -> randomizeRange.getValue());
    public final Setting<Boolean> elytra = new Setting<>("ElytraOverride",false);
    public final Setting<Float> elytraAttackRange = new Setting<>("ElytraRange", 3.1f, 1f, 6.0f, v -> elytra.getValue());
    public final Setting<Float> elytraWallRange = new Setting<>("ElytraThroughWallsRange", 3.1f, 0f, 6.0f, v -> elytra.getValue());
    public final Setting<WallsBypass> wallsBypass = new Setting<>("WallsBypass", WallsBypass.Off, v -> getWallRange() > 0f);
    public final Setting<Integer> fov = new Setting<>("FOV", 180, 1, 180);
    
    // Настройки для SmoothAI
    public final Setting<Float> smoothAISpeed = new Setting<>("SmoothAISpeed", 0.3f, 0.1f, 1.0f);
    public final Setting<Float> smoothAIJitter = new Setting<>("SmoothAIJitter", 0.2f, 0.0f, 1.0f);
    public final Setting<Float> smoothAIMicroJitter = new Setting<>("SmoothAIMicroJitter", 0.05f, 0.0f, 0.3f);
    public final Setting<Boolean> smoothAIPrediction = new Setting<>("SmoothAIPrediction", true);
    public final Setting<Float> smoothAIPredictionFactor = new Setting<>("SmoothAIPredictionFactor", 0.5f, 0.1f, 1.0f, v -> smoothAIPrediction.getValue());
    public final Setting<Boolean> smoothAIHumanLike = new Setting<>("SmoothAIHumanLike", true);
    public final Setting<Float> smoothAIVelocityFactor = new Setting<>("SmoothAIVelocityFactor", 0.8f, 0.1f, 2.0f);
    public final Setting<Boolean> smoothAIOvershoot = new Setting<>("SmoothAIOvershoot", true);
    public final Setting<Float> smoothAIOvershootAmount = new Setting<>("SmoothAIOvershootAmount", 0.1f, 0.0f, 0.5f, v -> smoothAIOvershoot.getValue());
    
    
    
    public final Setting<Switch> switchMode = new Setting<>("AutoWeapon", Switch.None);
    public final Setting<Boolean> onlyWeapon = new Setting<>("OnlyWeapon", false, v -> switchMode.getValue() != Switch.Silent);
    public final Setting<BooleanSettingGroup> smartCrit = new Setting<>("SmartCrit", new BooleanSettingGroup(true));
    public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false).addToGroup(smartCrit);
    public final Setting<Boolean> autoJump = new Setting<>("AutoJump", false).addToGroup(smartCrit);
    public final Setting<Boolean> shieldBreaker = new Setting<>("ShieldBreaker", true);
    public final Setting<Boolean> pauseWhileEating = new Setting<>("PauseWhileEating", false);
    public final Setting<Boolean> tpsSync = new Setting<>("TPSSync", false);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);
    public final Setting<Boolean> pauseBaritone = new Setting<>("PauseBaritone", false);
    
    public final Setting<BooleanSettingGroup> oldDelay = new Setting<>("OldDelay", new BooleanSettingGroup(false));
    public final Setting<Integer> minCPS = new Setting<>("MinCPS", 7, 1, 20).addToGroup(oldDelay);
    public final Setting<Integer> maxCPS = new Setting<>("MaxCPS", 12, 1, 20).addToGroup(oldDelay);

    public final Setting<ESP> esp = new Setting<>("ESP", ESP.ThunderHack);
    public final Setting<SettingGroup> espGroup = new Setting<>("ESPSettings", new SettingGroup(false, 0), v -> esp.is(ESP.ThunderHackV2));
    public final Setting<Integer> espLength = new Setting<>("ESPLength", 14, 1, 100, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Integer> espFactor = new Setting<>("ESPFactor", 8, 1, 100, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Float> espShaking = new Setting<>("ESPShaking", 1.8f, 1.5f, 100f, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Float> espAmplitude = new Setting<>("ESPAmplitude", 3f, 0.1f, 100f, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);

    public final Setting<Sort> sort = new Setting<>("Sort", Sort.LowestDistance);
    public final Setting<Boolean> lockTarget = new Setting<>("LockTarget", true);
    public final Setting<Boolean> elytraTarget = new Setting<>("ElytraTarget", true);

    /*   ADVANCED   */
    public final Setting<SettingGroup> advanced = new Setting<>("Advanced", new SettingGroup(false, 0));
    public final Setting<Float> aimRange = new Setting<>("AimRange", 3.1f, 0f, 6.0f).addToGroup(advanced);
    public final Setting<Boolean> randomHitDelay = new Setting<>("RandomHitDelay", false).addToGroup(advanced);
    public final Setting<Boolean> pauseInInventory = new Setting<>("PauseInInventory", true).addToGroup(advanced);
    public final Setting<Boolean> dropSprint = new Setting<>("DropSprint", true).addToGroup(advanced);
    public final Setting<Boolean> returnSprint = new Setting<>("ReturnSprint", true, v -> dropSprint.getValue()).addToGroup(advanced);
    public final Setting<RayTrace> rayTrace = new Setting<>("RayTrace", RayTrace.OnlyTarget).addToGroup(advanced);
    public final Setting<Boolean> grimRayTrace = new Setting<>("GrimRayTrace", true).addToGroup(advanced);
    public final Setting<Boolean> unpressShield = new Setting<>("UnpressShield", true).addToGroup(advanced);
    public final Setting<Boolean> deathDisable = new Setting<>("DisableOnDeath", true).addToGroup(advanced);
    public final Setting<Boolean> tpDisable = new Setting<>("TPDisable", false).addToGroup(advanced);
    public final Setting<Boolean> pullDown = new Setting<>("FastFall", false).addToGroup(advanced);
    public final Setting<Boolean> onlyJumpBoost = new Setting<>("OnlyJumpBoost", false, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<Float> pullValue = new Setting<>("PullValue", 3f, 0f, 20f, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<AttackHand> attackHand = new Setting<>("AttackHand", AttackHand.MainHand).addToGroup(advanced);
    public final Setting<Resolver> resolver = new Setting<>("Resolver", Resolver.Advantage).addToGroup(advanced);
    public final Setting<Integer> backTicks = new Setting<>("BackTicks", 4, 1, 20, v -> resolver.is(Resolver.BackTrack)).addToGroup(advanced);
    public final Setting<Boolean> resolverVisualisation = new Setting<>("ResolverVisualisation", false, v -> !resolver.is(Resolver.Off)).addToGroup(advanced);
    public final Setting<AccelerateOnHit> accelerateOnHit = new Setting<>("AccelerateOnHit", AccelerateOnHit.Off).addToGroup(advanced);
    public final Setting<Integer> minYawStep = new Setting<>("MinYawStep", 65, 1, 180).addToGroup(advanced);
    public final Setting<Integer> maxYawStep = new Setting<>("MaxYawStep", 75, 1, 180).addToGroup(advanced);
    public final Setting<Float> aimedPitchStep = new Setting<>("AimedPitchStep", 1f, 0f, 90f).addToGroup(advanced);
    public final Setting<Float> maxPitchStep = new Setting<>("MaxPitchStep", 8f, 1f, 90f).addToGroup(advanced);
    public final Setting<Float> pitchAccelerate = new Setting<>("PitchAccelerate", 1.65f, 1f, 10f).addToGroup(advanced);
    public final Setting<Float> attackCooldown = new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f).addToGroup(advanced);
    public final Setting<Float> attackBaseTime = new Setting<>("AttackBaseTime", 0.5f, 0f, 2f).addToGroup(advanced);
    public final Setting<Integer> attackTickLimit = new Setting<>("AttackTickLimit", 11, 0, 20).addToGroup(advanced);
    public final Setting<Float> critFallDistance = new Setting<>("CritFallDistance", 0f, 0f, 1f).addToGroup(advanced);

    /*   TARGETS   */
    public final Setting<SettingGroup> targets = new Setting<>("Targets", new SettingGroup(false, 0));
    public final Setting<Boolean> Players = new Setting<>("Players", true).addToGroup(targets);
    public final Setting<Boolean> Mobs = new Setting<>("Mobs", true).addToGroup(targets);
    public final Setting<Boolean> Animals = new Setting<>("Animals", true).addToGroup(targets);
    public final Setting<Boolean> Villagers = new Setting<>("Villagers", true).addToGroup(targets);
    public final Setting<Boolean> Slimes = new Setting<>("Slimes", true).addToGroup(targets);
    public final Setting<Boolean> hostiles = new Setting<>("Hostiles", true).addToGroup(targets);
    public final Setting<Boolean> onlyAngry = new Setting<>("OnlyAngryHostiles", true, v -> hostiles.getValue()).addToGroup(targets);
    public final Setting<Boolean> Projectiles = new Setting<>("Projectiles", true).addToGroup(targets);
    public final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisibleEntities", false).addToGroup(targets);
    public final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false).addToGroup(targets);
    public final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false).addToGroup(targets);
    public final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", true).addToGroup(targets);
    public final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false).addToGroup(targets);
    public final Setting<Boolean> ignoreShield = new Setting<>("AttackShieldingEntities", true).addToGroup(targets);

    /*   AUTOMACE   */
    public final Setting<SettingGroup> autoMace = new Setting<>("AutoMace", new SettingGroup(false, 0));
    public final Setting<Boolean> enableAutoMace = new Setting<>("EnableAutoMace", false).addToGroup(autoMace);
    public final Setting<AutoMaceMode> autoMaceMode = new Setting<>("AutoMaceMode", AutoMaceMode.LITE, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    public final Setting<Float> minHeight = new Setting<>("MinHeight", 1.5f, 0.5f, 10.0f, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    public final Setting<Float> maxDistance = new Setting<>("MaxDistance", 5.0f, 1.0f, 8.0f, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    public final Setting<Boolean> onlyWhenFalling = new Setting<>("OnlyWhenFalling", false, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    public final Setting<Boolean> returnToSword = new Setting<>("ReturnToSword", true, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    public final Setting<Integer> maceHoldTime = new Setting<>("MaceHoldTime", 1000, 100, 5000, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    
    // Настройки для режима Strong
    public final Setting<Float> strongMinHeight = new Setting<>("StrongMinHeight", 2.0f, 1.0f, 10.0f, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Float> strongMaxDistance = new Setting<>("StrongMaxDistance", 4.0f, 1.0f, 8.0f, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Integer> strongSwitchDelay = new Setting<>("StrongSwitchDelay", 200, 50, 1000, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Integer> strongAttackDelay = new Setting<>("StrongAttackDelay", 100, 50, 500, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Boolean> strongRandomizeTiming = new Setting<>("StrongRandomizeTiming", true, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    
    // Дополнительные настройки для максимальной легитности
    public final Setting<Boolean> strongHumanBehavior = new Setting<>("StrongHumanBehavior", true, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Float> strongMissChance = new Setting<>("StrongMissChance", 0.20f, 0.0f, 0.5f, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG && strongHumanBehavior.getValue()).addToGroup(autoMace);
    public final Setting<Integer> strongMaxAttacksPerSession = new Setting<>("StrongMaxAttacks", 1, 1, 3, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Boolean> strongLookAtTarget = new Setting<>("StrongLookAtTarget", true, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Float> strongLookSpeed = new Setting<>("StrongLookSpeed", 0.2f, 0.1f, 0.5f, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG && strongLookAtTarget.getValue()).addToGroup(autoMace);
    public final Setting<Boolean> strongPauseOnMovement = new Setting<>("StrongPauseOnMovement", true, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    
    // Дополнительные настройки для обхода античитов
    public final Setting<Boolean> strongRandomizeAim = new Setting<>("StrongRandomizeAim", true, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Float> strongAimError = new Setting<>("StrongAimError", 1.0f, 0.0f, 3.0f, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG && strongRandomizeAim.getValue()).addToGroup(autoMace);
    public final Setting<Boolean> strongAddDelays = new Setting<>("StrongAddDelays", true, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG).addToGroup(autoMace);
    public final Setting<Integer> strongMinDelay = new Setting<>("StrongMinDelay", 200, 100, 1000, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG && strongAddDelays.getValue()).addToGroup(autoMace);
    public final Setting<Integer> strongMaxDelay = new Setting<>("StrongMaxDelay", 500, 200, 2000, v -> enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG && strongAddDelays.getValue()).addToGroup(autoMace);
    
    // Дополнительные настройки плавности наводки
    public final Setting<Boolean> smoothAiming = new Setting<>("SmoothAiming", true, v -> enableAutoMace.getValue() && strongLookAtTarget.getValue()).addToGroup(autoMace);
    public final Setting<Float> aimSmoothness = new Setting<>("AimSmoothness", 0.8f, 0.1f, 2.0f, v -> enableAutoMace.getValue() && strongLookAtTarget.getValue() && smoothAiming.getValue()).addToGroup(autoMace);
    public final Setting<Boolean> adaptiveSpeed = new Setting<>("AdaptiveSpeed", true, v -> enableAutoMace.getValue() && strongLookAtTarget.getValue() && smoothAiming.getValue()).addToGroup(autoMace);
    public final Setting<Float> jitterIntensity = new Setting<>("JitterIntensity", 0.3f, 0.0f, 1.0f, v -> enableAutoMace.getValue() && strongLookAtTarget.getValue() && smoothAiming.getValue()).addToGroup(autoMace);
    
    // Настройки безопасности для предотвращения badpackets
    public final Setting<Boolean> safeMode = new Setting<>("SafeMode", true, v -> enableAutoMace.getValue()).addToGroup(autoMace);
    public final Setting<Integer> minSwitchDelay = new Setting<>("MinSwitchDelay", 100, 50, 500, v -> enableAutoMace.getValue() && safeMode.getValue()).addToGroup(autoMace);
    public final Setting<Boolean> validatePackets = new Setting<>("ValidatePackets", true, v -> enableAutoMace.getValue() && safeMode.getValue()).addToGroup(autoMace);
    
    // Настройки плавной ротации
    public final Setting<Boolean> ultraSmoothRotation = new Setting<>("UltraSmoothRotation", true);
    public final Setting<Float> rotationSmoothness = new Setting<>("RotationSmoothness", 0.15f, 0.01f, 1.0f, v -> ultraSmoothRotation.getValue());
    public final Setting<Boolean> useMoveFix = new Setting<>("UseMoveFix", true);
    
    // Дополнительные настройки безопасности
    public final Setting<Boolean> antiKick = new Setting<>("AntiKick", true);
    public final Setting<Boolean> validateTargets = new Setting<>("ValidateTargets", true);
    public final Setting<Float> maxAttackRange = new Setting<>("MaxAttackRange", 3.0f, 1.0f, 6.0f);
    public final Setting<Boolean> strictRange = new Setting<>("StrictRange", true);
    public final Setting<Float> rangeTolerance = new Setting<>("RangeTolerance", 0.1f, 0.0f, 0.5f, v -> strictRange.getValue());
    public final Setting<Boolean> humanizeAim = new Setting<>("HumanizeAim", true);
    public final Setting<Float> aimError = new Setting<>("AimError", 0.5f, 0.0f, 2.0f, v -> humanizeAim.getValue());
    public final Setting<Boolean> randomizeTiming = new Setting<>("RandomizeTiming", true);
    public final Setting<Integer> minDelay = new Setting<>("MinDelay", 50, 0, 200, v -> randomizeTiming.getValue());
    public final Setting<Integer> maxDelay = new Setting<>("MaxDelay", 150, 50, 500, v -> randomizeTiming.getValue());
    
    // Настройки точного наведения по хитбоксу
    public final Setting<Boolean> preciseHitbox = new Setting<>("PreciseHitbox", true);
    public final Setting<HitboxMode> hitboxMode = new Setting<>("HitboxMode", HitboxMode.Center, v -> preciseHitbox.getValue());
    public final Setting<Float> hitboxOffset = new Setting<>("HitboxOffset", 0.0f, -1.0f, 1.0f, v -> preciseHitbox.getValue());
    public final Setting<Boolean> predictMovement = new Setting<>("PredictMovement", true, v -> preciseHitbox.getValue());
    public final Setting<Float> predictionFactor = new Setting<>("PredictionFactor", 0.5f, 0.0f, 1.0f, v -> predictMovement.getValue());

    public float rotationYaw;
    public float rotationPitch;
    public float pitchAcceleration = 1f;
    
    // Поля для плавной ротации
    private float targetYaw;
    private float targetPitch;
    private float currentYaw;
    private float currentPitch;
    private long lastRotationUpdate = 0;
    private float rotationSpeed = 0.1f;
    
    // Поля для SmoothAI
    private Vec3d lastTargetPos = Vec3d.ZERO;
    private Vec3d targetVelocity = Vec3d.ZERO;
    private float smoothAITargetYaw = 0f;
    private float smoothAITargetPitch = 0f;
    private float smoothAICurrentYaw = 0f;
    private float smoothAICurrentPitch = 0f;
    private float smoothAIVelocityYaw = 0f;
    private float smoothAIVelocityPitch = 0f;
    private long smoothAILastUpdate = 0;
    private boolean smoothAIOvershooting = false;
    private float smoothAIOvershootYaw = 0f;
    private float smoothAIOvershootPitch = 0f;

    private Vec3d rotationPoint = Vec3d.ZERO;
    private Vec3d rotationMotion = Vec3d.ZERO;

    private int hitTicks;
    private int trackticks;
    private boolean lookingAtHitbox;
    
    // AutoMace поля
    private int previousSlot = -1;
    private boolean wasUsingMace = false;
    private long maceSwitchTime = 0;
    private boolean maceAttackDone = false;
    private long lastSlotSwitchTime = 0;
    private boolean isSwitchingSlot = false;
    
    // Поля для режима Strong
    private long lastSwitchTime = 0;
    private long lastAttackTime = 0;
    private boolean strongModeReady = false;
    private int strongAttackCount = 0;
    
    // Поля для человеческого поведения
    private boolean isLookingAtTarget = false;
    private float targetLookYaw = 0f;
    private float targetLookPitch = 0f;
    private long lastMovementTime = 0;
    private boolean wasMoving = false;
    private int consecutiveMisses = 0;
    private long lastMissTime = 0;
    private boolean shouldMissNext = false;

    private final Timer delayTimer = new Timer();
    private final Timer pauseTimer = new Timer();

    public Box resolvedBox;
    static boolean wasTargeted = false;
    

    public AuraAI() {
        super("AuraAI", Category.COMBAT);
    }

    private float getRange(){
        // Строго фиксированная дистанция без рандомизации для предотвращения кика
        float baseRange = elytra.getValue() && mc.player.isFallFlying() ? elytraAttackRange.getValue() : attackRange.getValue();
        
        // Строгий контроль дистанции
        if (strictRange.getValue()) {
            // Убираем рандомизацию полностью
            baseRange = attackRange.getValue();
            
            // Ограничиваем максимальную дистанцию для безопасности
            if (antiKick.getValue()) {
                baseRange = Math.min(baseRange, maxAttackRange.getValue());
            }
            
            // Применяем толерантность для более строгого контроля
            baseRange -= rangeTolerance.getValue();
        } else {
            // Ограничиваем максимальную дистанцию для безопасности
            if (antiKick.getValue()) {
                baseRange = Math.min(baseRange, maxAttackRange.getValue());
            }
        }
        
        return Math.max(1.0f, baseRange);
    }
    
    private float getWallRange(){
        // Строго фиксированная дистанция через стены
        float baseRange = elytra.getValue() && mc.player != null && mc.player.isFallFlying() ? elytraWallRange.getValue() : wallRange.getValue();
        
        // Строгий контроль дистанции через стены
        if (strictRange.getValue()) {
            // Убираем рандомизацию полностью
            baseRange = wallRange.getValue();
            
            // Ограничиваем максимальную дистанцию для безопасности
            if (antiKick.getValue()) {
                baseRange = Math.min(baseRange, maxAttackRange.getValue());
            }
            
            // Применяем толерантность для более строгого контроля
            baseRange -= rangeTolerance.getValue();
        } else {
            // Ограничиваем максимальную дистанцию для безопасности
            if (antiKick.getValue()) {
                baseRange = Math.min(baseRange, maxAttackRange.getValue());
            }
        }
        
        return Math.max(0.0f, baseRange);
    }

    public void auraLogic() {
        if (!haveWeapon()) {
            auraAITarget = null;
            return;
        }

        handleKill();
        updateTarget();

        if (auraAITarget == null) {
            return;
        }

        // Интеграция с Target ESP и Target HUD
        // Target ESP и Target HUD будут автоматически получать цель через общую систему

        if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround() && autoJump.getValue())
            mc.player.jump();

        boolean readyForAttack;

        if (grimRayTrace.getValue()) {
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
            calcRotations(autoCrit());
        } else {
            calcRotations(autoCrit());
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
        }

        if (readyForAttack) {
            if (shieldBreaker(false))
                return;

            boolean[] playerState = preAttack();
            if (!(auraAITarget instanceof PlayerEntity pl) || !(pl.isUsingItem() && pl.getOffHandStack().getItem() == Items.SHIELD) || ignoreShield.getValue())
                attack();

            postAttack(playerState[0], playerState[1]);
        }
        
        // Обработка AutoMace
        handleAutoMace();
    }

    private boolean haveWeapon() {
        Item handItem = mc.player.getMainHandStack().getItem();
        if (onlyWeapon.getValue()) {
            boolean hasValidWeapon = handItem instanceof SwordItem || handItem instanceof AxeItem || handItem instanceof TridentItem || handItem instanceof MaceItem;
            
            if (switchMode.getValue() == Switch.None) {
                return hasValidWeapon;
            } else {
                return hasValidWeapon || (InventoryUtility.getSwordHotBar().found() || InventoryUtility.getAxeHotBar().found() || InventoryUtility.getMaceHotBar().found());
            }
        }
        return true;
    }

    private boolean skipRayTraceCheck() {
        return rayTrace.getValue() == RayTrace.OFF;
    }

    public void attack() {
        // Дополнительная проверка валидности цели для предотвращения кика
        if (auraAITarget == null || !auraAITarget.isAlive()) {
            auraAITarget = null;
            return;
        }
        
        // Проверяем, что цель не является недопустимой сущностью
        if (skipEntity(auraAITarget)) {
            auraAITarget = null;
            return;
        }
        
        // Дополнительные проверки безопасности
        if (validateTargets.getValue() && !isValidTarget(auraAITarget)) {
            auraAITarget = null;
            return;
        }
        
        // Проверяем, что игрок не в недопустимом состоянии
        if (!isPlayerInValidState()) {
            return;
        }
        
        // Проверяем расстояние с дополнительной валидацией
        if (!isTargetInValidRange(auraAITarget)) {
            auraAITarget = null;
            return;
        }
        
        // Строгая проверка дальности для предотвращения кика за Reach
        double distance = mc.player.distanceTo(auraAITarget);
        float maxAllowedRange = getRange();
        
        // Дополнительная проверка для через стены
        if (wallRange.getValue() > 0) {
            // Проверяем, есть ли препятствия между игроком и целью
            HitResult hitResult = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                auraAITarget.getBoundingBox().getCenter(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                // Если есть препятствие, используем только дистанцию через стены
                maxAllowedRange = getWallRange();
            }
        }
        
        // Строгая проверка дистанции
        if (distance > maxAllowedRange) {
            auraAITarget = null;
            return;
        }
        
        // Дополнительная проверка для предотвращения кика
        if (antiKick.getValue() && distance > maxAttackRange.getValue()) {
            auraAITarget = null;
            return;
        }
        
        // Добавляем случайную задержку для обхода детекции
        if (randomizeTiming.getValue()) {
            int delay = (int) (Math.random() * (maxDelay.getValue() - minDelay.getValue())) + minDelay.getValue();
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        Criticals.cancelCrit = true;
        ModuleManager.criticals.doCrit();
        int prevSlot = switchMethod();
        mc.interactionManager.attackEntity(mc.player, auraAITarget);
        Criticals.cancelCrit = false;
        swingHand();
        hitTicks = getHitTicks();
        if (prevSlot != -1)
            InventoryUtility.switchTo(prevSlot);
    }

    private boolean @NotNull [] preAttack() {
        boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == BLOCK;
        if (blocking && unpressShield.getValue())
            sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));

        boolean sprint = Core.serverSprint;
        if (sprint && dropSprint.getValue())
            disableSprint();

        return new boolean[]{blocking, sprint};
    }

    public void postAttack(boolean block, boolean sprint) {
        if (sprint && returnSprint.getValue() && dropSprint.getValue())
            enableSprint();

        if (block && unpressShield.getValue())
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, rotationYaw, rotationPitch));
    }

    private void disableSprint() {
        mc.player.setSprinting(false);
        mc.options.sprintKey.setPressed(false);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
    }

    private void enableSprint() {
        mc.player.setSprinting(true);
        mc.options.sprintKey.setPressed(true);
        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
    }

    public void resolvePlayers() {
        if (resolver.not(Resolver.Off))
            for (PlayerEntity player : mc.world.getPlayers())
                if (player instanceof OtherClientPlayerEntity)
                    ((IOtherClientPlayerEntity) player).resolve(Aura.Resolver.valueOf(resolver.getValue().name()));
    }

    public void restorePlayers() {
        if (resolver.not(Resolver.Off))
            for (PlayerEntity player : mc.world.getPlayers())
                if (player instanceof OtherClientPlayerEntity)
                    ((IOtherClientPlayerEntity) player).releaseResolver();
    }

    public void handleKill() {
        if (auraAITarget instanceof LivingEntity && (((LivingEntity) auraAITarget).getHealth() <= 0 || !((LivingEntity) auraAITarget).isAlive()))
            Managers.NOTIFICATION.publicity("AuraAI", isRu() ? "Цель успешно нейтрализована!" : "Target successfully neutralized!", 3, Notification.Type.SUCCESS);
    }

    private int switchMethod() {
        int prevSlot = -1;
        SearchInvResult swordResult = InventoryUtility.getSwordHotBar();
        if (swordResult.found() && switchMode.getValue() != Switch.None) {
            if (switchMode.getValue() == Switch.Silent)
                prevSlot = mc.player.getInventory().selectedSlot;
            swordResult.switchTo();
        }
        return prevSlot;
    }

    private int getHitTicks() {
        return oldDelay.getValue().isEnabled() ? 1 + (int) (20f / random(minCPS.getValue(), maxCPS.getValue())) : (shouldRandomizeDelay() ? (int) MathUtility.random(11, 13) : attackTickLimit.getValue());
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem() && pauseWhileEating.getValue())
            return;
        if(pauseBaritone.getValue() && ThunderHack.baritone){
            boolean isTargeted = (auraAITarget != null);
            if (isTargeted && !wasTargeted) {
                // Baritone pause logic
                wasTargeted = true;
            } else if (!isTargeted && wasTargeted) {
                // Baritone resume logic
                wasTargeted = false;
            }
        }

        resolvePlayers();
        auraLogic();
        restorePlayers();
        hitTicks--;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.player.isUsingItem() && pauseWhileEating.getValue())
            return;

        if (!haveWeapon())
            return;

        if (auraAITarget != null) {
            mc.player.setYaw(rotationYaw);
            mc.player.setPitch(rotationPitch);
        } else {
            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
        }

        if (oldDelay.getValue().isEnabled())
            if (minCPS.getValue() > maxCPS.getValue())
                minCPS.setValue(maxCPS.getValue());

        if (auraAITarget != null && pullDown.getValue() && (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) || !onlyJumpBoost.getValue()))
            mc.player.addVelocity(0f, -pullValue.getValue() / 1000f, 0f);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send e) {
        if (e.getPacket() instanceof PlayerInteractEntityC2SPacket pie && Criticals.getInteractType(pie) != Criticals.InteractType.ATTACK && auraAITarget != null)
            e.cancel();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof EntityStatusS2CPacket status)
            if (status.getStatus() == 30 && status.getEntity(mc.world) != null && auraAITarget != null && status.getEntity(mc.world) == auraAITarget)
                Managers.NOTIFICATION.publicity("AuraAI", isRu() ? ("Успешно сломали щит игроку " + auraAITarget.getName().getString()) : ("Succesfully destroyed " + auraAITarget.getName().getString() + "'s shield"), 2, Notification.Type.SUCCESS);

        if (e.getPacket() instanceof PlayerPositionLookS2CPacket && tpDisable.getValue())
            disable(isRu() ? "Отключаю из-за телепортации!" : "Disabling due to tp!");

        if (e.getPacket() instanceof EntityStatusS2CPacket pac && pac.getStatus() == 3 && pac.getEntity(mc.world) == mc.player && deathDisable.getValue())
            disable(isRu() ? "Отключаю из-за смерти!" : "Disabling due to death!");
    }

    private boolean shouldUseMace() {
        if (!enableAutoMace.getValue() || auraAITarget == null) return false;
        
        // Выбираем параметры в зависимости от режима
        float currentMinHeight = autoMaceMode.getValue() == AutoMaceMode.STRONG ? strongMinHeight.getValue() : minHeight.getValue();
        float currentMaxDistance = autoMaceMode.getValue() == AutoMaceMode.STRONG ? strongMaxDistance.getValue() : maxDistance.getValue();
        
        // Проверяем, что игрок выше цели на минимальную высоту
        double heightDifference = mc.player.getY() - auraAITarget.getY();
        if (heightDifference < currentMinHeight) return false;
        
        // СТРОГАЯ проверка расстояния до цели с использованием ползунков
        double distance = mc.player.distanceTo(auraAITarget);
        float maxAllowedDistance = getRange(); // Используем дистанцию из ползунка Range
        
        // Дополнительная проверка для через стены
        if (wallRange.getValue() > 0) {
            HitResult hitResult = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                auraAITarget.getBoundingBox().getCenter(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                // Если есть препятствие, используем дистанцию через стены
                maxAllowedDistance = getWallRange();
            }
        }
        
        // Строгая проверка дистанции
        if (distance > maxAllowedDistance) return false;
        
        // Дополнительная проверка для предотвращения кика
        if (antiKick.getValue() && distance > maxAttackRange.getValue()) return false;
        
        // Проверяем, что игрок падает (если включено)
        if (onlyWhenFalling.getValue() && mc.player.getVelocity().y > 0.1) return false;
        
        // Дополнительные проверки для режима Strong
        if (autoMaceMode.getValue() == AutoMaceMode.STRONG) {
            // Проверяем задержки для более легитного поведения
            long currentTime = System.currentTimeMillis();
            
            // Минимальная задержка между переключениями
            if (currentTime - lastSwitchTime < strongSwitchDelay.getValue()) return false;
            
            // Минимальная задержка между атаками
            if (currentTime - lastAttackTime < strongAttackDelay.getValue()) return false;
            
            // Рандомизация для обхода детекции
            if (strongRandomizeTiming.getValue()) {
                int randomDelay = (int) (strongSwitchDelay.getValue() * (0.8 + Math.random() * 0.4)); // ±20% рандомизация
                if (currentTime - lastSwitchTime < randomDelay) return false;
            }
        }
        
        return true;
    }
    
    private boolean isPlayerMoving() {
        if (mc.player == null) return false;
        return mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0 || 
               mc.player.input.jumping || mc.player.input.sneaking;
    }
    
    private void updateHumanBehavior() {
        if (!strongHumanBehavior.getValue() || autoMaceMode.getValue() != AutoMaceMode.STRONG) return;
        
        // Отслеживаем движение игрока
        boolean currentlyMoving = isPlayerMoving();
        if (currentlyMoving && !wasMoving) {
            lastMovementTime = System.currentTimeMillis();
        }
        wasMoving = currentlyMoving;
        
        // Обновляем взгляд на цель
        if (strongLookAtTarget.getValue() && auraAITarget != null && wasUsingMace) {
            updateLookAtTarget();
        }
        
        // Определяем, нужно ли промахнуться
        if (shouldMissNext && System.currentTimeMillis() - lastMissTime > 2000) {
            shouldMissNext = false;
        }
    }
    
    private void updateLookAtTarget() {
        if (auraAITarget == null) return;
        
        // Плавно поворачиваем голову к цели
        float[] targetRotation = Managers.PLAYER.calcAngle(auraAITarget.getPos().add(0, auraAITarget.getEyeHeight(auraAITarget.getPose()) / 2f, 0));
        
        float deltaYaw = wrapDegrees(targetRotation[0] - mc.player.getYaw());
        float deltaPitch = targetRotation[1] - mc.player.getPitch();
        
        // Базовая скорость поворота
        float lookSpeed = strongLookSpeed.getValue();
        
        if (smoothAiming.getValue()) {
            // Адаптивная скорость в зависимости от расстояния до цели
            float distance = (float) mc.player.distanceTo(auraAITarget);
            float currentSpeed = lookSpeed;
            
            if (adaptiveSpeed.getValue()) {
                currentSpeed = Math.min(lookSpeed, Math.max(0.1f, lookSpeed * (1.0f - distance / 10.0f)));
            }
            
            // Интерполяция с учетом FPS и настройки плавности
            float deltaTime = 1.0f / 20.0f; // Фиксированная дельта времени для стабильности
            float smoothness = aimSmoothness.getValue();
            float smoothFactor = Math.min(1.0f, currentSpeed * deltaTime * 20.0f * smoothness);
            
            // Применяем плавную интерполяцию
            float newYaw = mc.player.getYaw() + deltaYaw * smoothFactor;
            float newPitch = mc.player.getPitch() + deltaPitch * smoothFactor;
            
            // Добавляем джиттер для реалистичности с настраиваемой интенсивностью
            if (distance < 3.0f) {
                float jitter = jitterIntensity.getValue();
                newYaw += random(-0.3f * jitter, 0.3f * jitter);
                newPitch += random(-0.2f * jitter, 0.2f * jitter);
            }
            
            // Плавное ограничение pitch
            newPitch = MathHelper.clamp(newPitch, -90f, 90f);
            
            mc.player.setYaw(newYaw);
            mc.player.setPitch(newPitch);
        } else {
            // Простая наводка без дополнительной плавности
            float newYaw = mc.player.getYaw() + deltaYaw * lookSpeed;
            float newPitch = mc.player.getPitch() + deltaPitch * lookSpeed;
            
            // Добавляем небольшой джиттер для реалистичности
            newYaw += random(-0.5f, 0.5f);
            newPitch += random(-0.3f, 0.3f);
            
            mc.player.setYaw(newYaw);
            mc.player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));
        }
    }
    
    private boolean shouldMissAttack() {
        if (!strongHumanBehavior.getValue()) return false;
        
        // Проверяем, что прошло достаточно времени с последнего промаха
        long timeSinceLastMiss = System.currentTimeMillis() - lastMissTime;
        if (timeSinceLastMiss < 1000) return false; // Минимум 1 секунда между промахами
        
        // Базовая вероятность промаха (увеличена для легитности)
        float missChance = strongMissChance.getValue();
        
        // Дополнительные факторы, увеличивающие вероятность промаха
        if (consecutiveMisses > 2) {
            missChance *= 0.5f; // Уменьшаем шанс промаха после нескольких подряд
        }
        
        // Если цель движется быстро, увеличиваем шанс промаха
        if (auraAITarget != null && auraAITarget.getVelocity().length() > 0.1) {
            missChance *= 1.5f;
        }
        
        // Если игрок движется, увеличиваем шанс промаха
        if (isPlayerMoving()) {
            missChance *= 1.3f;
        }
        
        // Если цель далеко, увеличиваем шанс промаха
        if (auraAITarget != null && mc.player.distanceTo(auraAITarget) > 3.0) {
            missChance *= 1.2f;
        }
        
        // Ограничиваем максимальную вероятность промаха
        missChance = Math.min(missChance, 0.4f);
        
        if (Math.random() < missChance) {
            shouldMissNext = true;
            lastMissTime = System.currentTimeMillis();
            consecutiveMisses++;
            return true;
        }
        
        // Сбрасываем счетчик промахов при успешной атаке
        if (consecutiveMisses > 0 && Math.random() < 0.1) {
            consecutiveMisses = 0;
        }
        
        return false;
    }
    
    private boolean isHoldingMace() {
        if (mc.player == null || mc.player.getMainHandStack().isEmpty()) return false;
        return mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.MaceItem;
    }
    
    private void forceSwitchToMace(SearchInvResult maceResult) {
        if (maceResult.found()) {
            long currentTime = System.currentTimeMillis();
            
            // Проверяем, что слот валиден
            if (maceResult.slot() < 0 || maceResult.slot() > 8) {
                return;
            }
            
            // Проверяем, что предмет действительно булава
            if (!(mc.player.getInventory().getStack(maceResult.slot()).getItem() instanceof MaceItem)) {
                return;
            }
            
            // Используем настройки безопасности
            int minDelay = safeMode.getValue() ? minSwitchDelay.getValue() : 50;
            
            // Проверяем, что мы не переключаемся слишком часто
            if (currentTime - lastSlotSwitchTime < minDelay) {
                return;
            }
            
            // Проверяем, что мы не в процессе переключения
            if (isSwitchingSlot) {
                return;
            }
            
            // Проверяем, что мы не переключаемся на тот же слот
            if (mc.player.getInventory().selectedSlot == maceResult.slot()) {
                return;
            }
            
            // Дополнительная валидация пакетов
            if (validatePackets.getValue()) {
                // Проверяем, что игрок не мертв
                if (mc.player.getHealth() <= 0) {
                    return;
                }
                
                // Проверяем, что игрок не в меню
                if (mc.currentScreen != null) {
                    return;
                }
                
                // Проверяем, что игрок не телепортируется
                if (mc.player.getVelocity().length() > 10) {
                    return;
                }
            }
            
            // Устанавливаем флаг переключения
            isSwitchingSlot = true;
            lastSlotSwitchTime = currentTime;
            
            // Обновляем локальное состояние
            mc.player.getInventory().selectedSlot = maceResult.slot();
            
            // Отправляем только один пакет для предотвращения badpackets
            sendPacket(new UpdateSelectedSlotC2SPacket(maceResult.slot()));
            
            // Сбрасываем флаг через небольшую задержку
            int sleepTime = safeMode.getValue() ? minSwitchDelay.getValue() : 100;
            new Thread(() -> {
                try {
                    Thread.sleep(sleepTime);
                    isSwitchingSlot = false;
                } catch (InterruptedException e) {
                    isSwitchingSlot = false;
                }
            }).start();
        }
    }

    private void handleAutoMace() {
        if (!enableAutoMace.getValue()) return;

        boolean shouldUse = shouldUseMace();
        long currentTime = System.currentTimeMillis();

        if (autoMaceMode.getValue() == AutoMaceMode.LITE) {
            handleLiteMode(shouldUse, currentTime);
        } else {
            handleStrongMode(shouldUse, currentTime);
        }
    }
    
    private void handleLiteMode(boolean shouldUse, long currentTime) {
        if (shouldUse && !wasUsingMace) {
            // Переключаемся на булаву
            SearchInvResult maceResult = InventoryUtility.getMaceHotBar();
            if (maceResult.found() && auraAITarget != null) {
                previousSlot = mc.player.getInventory().selectedSlot;
                
                // Принудительное переключение на булаву
                forceSwitchToMace(maceResult);
                
                // Устанавливаем флаги
                wasUsingMace = true;
                maceSwitchTime = currentTime;
                maceAttackDone = false;
                
                // Небольшая задержка перед атакой для корректного переключения
                if (hitTicks <= 0) {
                    // Дополнительная проверка дистанции перед атакой булавой
                    double distance = mc.player.distanceTo(auraAITarget);
                    float maxAllowedDistance = getRange();
                    
                    if (distance <= maxAllowedDistance) {
                        hitTicks = getHitTicks();
                        boolean[] playerState = preAttack();
                        ModuleManager.criticals.doCrit();
                        mc.interactionManager.attackEntity(mc.player, auraAITarget);
                        swingHand();
                        postAttack(playerState[0], playerState[1]);
                        maceAttackDone = true;
                    }
                }
            }
        } else if (wasUsingMace) {
            // Проверяем, что действительно держим булаву
            if (!isHoldingMace()) {
                SearchInvResult maceResult = InventoryUtility.getMaceHotBar();
                if (maceResult.found()) {
                    forceSwitchToMace(maceResult);
                }
            }
            // Дополнительная атака булавой, если не атаковали еще
            if (!maceAttackDone && hitTicks <= 0 && auraAITarget != null) {
                // Дополнительная проверка дистанции перед атакой булавой
                double distance = mc.player.distanceTo(auraAITarget);
                float maxAllowedDistance = getRange();
                
                if (distance <= maxAllowedDistance) {
                    hitTicks = getHitTicks();
                    boolean[] playerState = preAttack();
                    ModuleManager.criticals.doCrit();
                    mc.interactionManager.attackEntity(mc.player, auraAITarget);
                    swingHand();
                    postAttack(playerState[0], playerState[1]);
                    maceAttackDone = true;
                }
            }
            
            // Проверяем, нужно ли вернуться к мечу
            boolean shouldReturn = false;
            
            if (returnToSword.getValue()) {
                // Возвращаемся к мечу только если:
                // 1. Прошло достаточно времени (maceHoldTime)
                // 2. ИЛИ условия больше не выполняются И прошло минимум 500мс
                if (currentTime - maceSwitchTime >= maceHoldTime.getValue()) {
                    shouldReturn = true;
                } else if (!shouldUse && currentTime - maceSwitchTime >= 500) {
                    shouldReturn = true;
                }
            }
            
            if (shouldReturn && previousSlot != -1) {
                // Проверяем, что мы не переключаемся слишком часто
                int minDelay = safeMode.getValue() ? minSwitchDelay.getValue() : 100;
                
                if (currentTime - lastSlotSwitchTime >= minDelay && !isSwitchingSlot) {
                    // Проверяем, что слот валиден
                    if (previousSlot >= 0 && previousSlot <= 8) {
                        // Дополнительная валидация пакетов
                        if (validatePackets.getValue()) {
                            if (mc.player.getHealth() <= 0 || mc.currentScreen != null || mc.player.getVelocity().length() > 10) {
                                return;
                            }
                        }
                        
                        isSwitchingSlot = true;
                        lastSlotSwitchTime = currentTime;
                        
                        mc.player.getInventory().selectedSlot = previousSlot;
                        sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                        
                        // Сбрасываем флаг через небольшую задержку
                        new Thread(() -> {
                            try {
                                Thread.sleep(minDelay);
                                isSwitchingSlot = false;
                            } catch (InterruptedException e) {
                                isSwitchingSlot = false;
                            }
                        }).start();
                    }
                    
                    previousSlot = -1;
                    wasUsingMace = false;
                    maceSwitchTime = 0;
                    maceAttackDone = false;
                }
            }
        }
    }
    
    private void handleStrongMode(boolean shouldUse, long currentTime) {
        // Обновляем человеческое поведение
        updateHumanBehavior();
        
        // Проверяем, нужно ли приостановить из-за движения
        if (strongPauseOnMovement.getValue() && isPlayerMoving() && 
            currentTime - lastMovementTime < 500) {
            return;
        }
        
        if (shouldUse && !wasUsingMace) {
            // Переключаемся на булаву с задержкой
            SearchInvResult maceResult = InventoryUtility.getMaceHotBar();
            if (maceResult.found() && auraAITarget != null) {
                previousSlot = mc.player.getInventory().selectedSlot;
                
                // Плавное переключение для обхода детекции
                if (currentTime - lastSwitchTime >= strongSwitchDelay.getValue()) {
                    // Принудительное переключение на булаву
                    forceSwitchToMace(maceResult);
                    
                    // Устанавливаем флаги
                    wasUsingMace = true;
                    maceSwitchTime = currentTime;
                    lastSwitchTime = currentTime;
                    maceAttackDone = false;
                    strongModeReady = true;
                    strongAttackCount = 0;
                    isLookingAtTarget = true;
                }
            }
        } else if (wasUsingMace && strongModeReady) {
            // Проверяем, что действительно держим булаву
            if (!isHoldingMace()) {
                SearchInvResult maceResult = InventoryUtility.getMaceHotBar();
                if (maceResult.found()) {
                    forceSwitchToMace(maceResult);
                }
            }
            
            // Атака с задержкой и рандомизацией
            int requiredDelay = strongAttackDelay.getValue();
            
            // Добавляем случайные задержки для легитности
            if (strongAddDelays.getValue()) {
                int randomDelay = (int) (Math.random() * (strongMaxDelay.getValue() - strongMinDelay.getValue())) + strongMinDelay.getValue();
                requiredDelay += randomDelay;
            }
            
            if (currentTime - lastAttackTime >= requiredDelay && hitTicks <= 0 && auraAITarget != null) {
                // Рандомизация атаки
                if (strongRandomizeTiming.getValue()) {
                    int randomDelay = (int) (requiredDelay * (0.8 + Math.random() * 0.4));
                    if (currentTime - lastAttackTime < randomDelay) return;
                }
                
                // Проверяем, нужно ли промахнуться
                boolean shouldMiss = shouldMissAttack();
                
                if (!shouldMiss) {
                    // Дополнительная проверка дистанции перед атакой булавой
                    double distance = mc.player.distanceTo(auraAITarget);
                    float maxAllowedDistance = getRange();
                    
                    if (distance <= maxAllowedDistance) {
                        hitTicks = getHitTicks();
                        boolean[] playerState = preAttack();
                        ModuleManager.criticals.doCrit();
                        mc.interactionManager.attackEntity(mc.player, auraAITarget);
                        swingHand();
                        postAttack(playerState[0], playerState[1]);
                        maceAttackDone = true;
                        lastAttackTime = currentTime;
                        strongAttackCount++;
                        
                        // Сбрасываем счетчик промахов при успешной атаке
                        consecutiveMisses = 0;
                    } else {
                        // Если дистанция слишком большая, имитируем промах
                        swingHand();
                        lastAttackTime = currentTime;
                        consecutiveMisses++;
                        lastMissTime = currentTime;
                    }
                } else {
                    // Имитируем промах - просто качаем рукой
                    swingHand();
                    lastAttackTime = currentTime;
                    consecutiveMisses++;
                    lastMissTime = currentTime;
                }
                
                // Ограничиваем количество атак для легитности
                if (strongAttackCount >= strongMaxAttacksPerSession.getValue()) {
                    strongModeReady = false;
                }
            }
            
            // Проверяем, нужно ли вернуться к мечу
            boolean shouldReturn = false;
            
            if (returnToSword.getValue()) {
                // Более консервативное возвращение для режима Strong
                if (currentTime - maceSwitchTime >= maceHoldTime.getValue() * 1.5f) {
                    shouldReturn = true;
                } else if (!shouldUse && currentTime - maceSwitchTime >= 1000) {
                    shouldReturn = true;
                } else if (strongAttackCount >= strongMaxAttacksPerSession.getValue()) {
                    shouldReturn = true;
                }
            }
            
            if (shouldReturn && previousSlot != -1) {
                // Плавное возвращение с дополнительными проверками
                int minDelay = safeMode.getValue() ? Math.max(minSwitchDelay.getValue(), 150) : 150;
                
                if (currentTime - lastSwitchTime >= strongSwitchDelay.getValue() && 
                    currentTime - lastSlotSwitchTime >= minDelay && !isSwitchingSlot) {
                    
                    // Проверяем, что слот валиден
                    if (previousSlot >= 0 && previousSlot <= 8) {
                        // Дополнительная валидация пакетов
                        if (validatePackets.getValue()) {
                            if (mc.player.getHealth() <= 0 || mc.currentScreen != null || mc.player.getVelocity().length() > 10) {
                                return;
                            }
                        }
                        
                        isSwitchingSlot = true;
                        lastSlotSwitchTime = currentTime;
                        
                        mc.player.getInventory().selectedSlot = previousSlot;
                        sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                        
                        // Сбрасываем флаг через небольшую задержку
                        new Thread(() -> {
                            try {
                                Thread.sleep(minDelay);
                                isSwitchingSlot = false;
                            } catch (InterruptedException e) {
                                isSwitchingSlot = false;
                            }
                        }).start();
                    }
                    
                    previousSlot = -1;
                    wasUsingMace = false;
                    maceSwitchTime = 0;
                    maceAttackDone = false;
                    strongModeReady = false;
                    strongAttackCount = 0;
                    lastSwitchTime = currentTime;
                    isLookingAtTarget = false;
                }
            }
        }
    }

    @Override
    public void onEnable() {
        auraAITarget = null;
        lookingAtHitbox = false;
        rotationPoint = Vec3d.ZERO;
        rotationMotion = Vec3d.ZERO;
        rotationYaw = mc.player.getYaw();
        rotationPitch = mc.player.getPitch();
        delayTimer.reset();
        
        // Инициализация SmoothAI
        lastTargetPos = Vec3d.ZERO;
        targetVelocity = Vec3d.ZERO;
        smoothAITargetYaw = 0f;
        smoothAITargetPitch = 0f;
        smoothAICurrentYaw = mc.player.getYaw();
        smoothAICurrentPitch = mc.player.getPitch();
        smoothAIVelocityYaw = 0f;
        smoothAIVelocityPitch = 0f;
        smoothAILastUpdate = System.currentTimeMillis();
        smoothAIOvershooting = false;
        smoothAIOvershootYaw = 0f;
        smoothAIOvershootPitch = 0f;
    }

    @Override
    public void onDisable() {
        auraAITarget = null;
        
        // Сброс SmoothAI
        lastTargetPos = Vec3d.ZERO;
        targetVelocity = Vec3d.ZERO;
        smoothAITargetYaw = 0f;
        smoothAITargetPitch = 0f;
        smoothAICurrentYaw = 0f;
        smoothAICurrentPitch = 0f;
        smoothAIVelocityYaw = 0f;
        smoothAIVelocityPitch = 0f;
        smoothAILastUpdate = 0;
        smoothAIOvershooting = false;
        smoothAIOvershootYaw = 0f;
        smoothAIOvershootPitch = 0f;
        
        // Сброс AutoMace состояния
        if (wasUsingMace && returnToSword.getValue() && previousSlot != -1) {
            mc.player.getInventory().selectedSlot = previousSlot;
        }
        previousSlot = -1;
        wasUsingMace = false;
        maceSwitchTime = 0;
        maceAttackDone = false;
        lastSlotSwitchTime = 0;
        isSwitchingSlot = false;
        
        // Сброс состояния режима Strong
        lastSwitchTime = 0;
        lastAttackTime = 0;
        strongModeReady = false;
        strongAttackCount = 0;
        
        // Сброс человеческого поведения
        isLookingAtTarget = false;
        targetLookYaw = 0f;
        targetLookPitch = 0f;
        lastMovementTime = 0;
        wasMoving = false;
        consecutiveMisses = 0;
    }

    private void calcRotations(boolean ready) {
        if (ready) {
            trackticks = (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1, 0.0)).iterator().hasNext() ? 1 : 3);
        } else if (trackticks > 0) {
            trackticks--;
        }

        if (auraAITarget == null) {
            // Сбрасываем плавную ротацию при отсутствии цели
            if (ultraSmoothRotation.getValue()) {
                currentYaw = mc.player.getYaw();
                currentPitch = mc.player.getPitch();
            }
            return;
        }

        // Получаем позицию цели для наведения
        Vec3d targetVec = getTargetPosition();
        
        if (targetVec == null)
            return;

        // Вычисляем углы наведения
        double[] rotations = calculateLookAngles(targetVec);
        if (rotations == null) return;
        
        float newTargetYaw = (float) rotations[0];
        float newTargetPitch = (float) rotations[1];

        if (ultraSmoothRotation.getValue()) {
            // Очень плавная ротация с интерполяцией
            updateUltraSmoothRotation(newTargetYaw, newTargetPitch);
        } else {
            // Обычная SmoothAI ротация
            updateSmoothAI(newTargetYaw, newTargetPitch);
        }
        
        // Обновляем lookingAtHitbox для проверки попадания
        lookingAtHitbox = checkHitboxHit(rotationYaw, rotationPitch);
        
        // Интеграция с MoveFix
        if (useMoveFix.getValue()) {
            ModuleManager.rotations.fixRotation = rotationYaw;
        }
    }
    
    private Vec3d getTargetPosition() {
        if (auraAITarget == null) return null;
        
        // Получаем точную позицию хитбокса
        Box boundingBox = auraAITarget.getBoundingBox();
        
        // Вычисляем центр хитбокса
        double centerX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double centerY = (boundingBox.minY + boundingBox.maxY) / 2.0;
        double centerZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        
        // Применяем режим наведения по хитбоксу
        if (preciseHitbox.getValue()) {
            switch (hitboxMode.getValue()) {
                case Center -> {
                    // Используем центр хитбокса
                    centerY = (boundingBox.minY + boundingBox.maxY) / 2.0;
                }
                case Head -> {
                    // Наводимся на голову
                    if (auraAITarget instanceof LivingEntity) {
                        LivingEntity livingEntity = (LivingEntity) auraAITarget;
                        centerY = livingEntity.getY() + livingEntity.getEyeHeight(livingEntity.getPose());
                    } else {
                        centerY = boundingBox.maxY;
                    }
                }
                case Chest -> {
                    // Наводимся на грудь
                    if (auraAITarget instanceof LivingEntity) {
                        LivingEntity livingEntity = (LivingEntity) auraAITarget;
                        double eyeHeight = livingEntity.getEyeHeight(livingEntity.getPose());
                        centerY = livingEntity.getY() + eyeHeight * 0.6; // Примерно на уровне груди
                    } else {
                        centerY = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * 0.6;
                    }
                }
                case Legs -> {
                    // Наводимся на ноги
                    if (auraAITarget instanceof LivingEntity) {
                        LivingEntity livingEntity = (LivingEntity) auraAITarget;
                        double eyeHeight = livingEntity.getEyeHeight(livingEntity.getPose());
                        centerY = livingEntity.getY() + eyeHeight * 0.2; // Примерно на уровне ног
                    } else {
                        centerY = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * 0.2;
                    }
                }
            }
            
            // Применяем смещение
            centerY += hitboxOffset.getValue();
        }
        
        // Предсказание движения цели
        if (predictMovement.getValue() && auraAITarget instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) auraAITarget;
            Vec3d velocity = livingEntity.getVelocity();
            
            // Вычисляем время полета атаки (примерно)
            double distance = mc.player.distanceTo(auraAITarget);
            double attackTime = distance / 20.0; // Примерная скорость атаки
            
            // Предсказываем позицию цели
            centerX += velocity.x * attackTime * predictionFactor.getValue();
            centerY += velocity.y * attackTime * predictionFactor.getValue();
            centerZ += velocity.z * attackTime * predictionFactor.getValue();
        }
        
        return new Vec3d(centerX, centerY, centerZ);
    }
    
    private double[] calculateLookAngles(Vec3d targetPos) {
        if (targetPos == null) return null;
        
        double deltaX = targetPos.x - mc.player.getX();
        double deltaY = targetPos.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double deltaZ = targetPos.z - mc.player.getZ();
        
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        
        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        float pitch = (float) Math.toDegrees(Math.atan2(deltaY, distance)) * -1.0f;
        
        // Добавляем человеческие ошибки для легитности
        if (humanizeAim.getValue()) {
            // Случайные ошибки в наводке с настраиваемой интенсивностью
            float errorIntensity = aimError.getValue();
            float yawError = (float) (Math.random() - 0.5) * errorIntensity;
            float pitchError = (float) (Math.random() - 0.5) * (errorIntensity * 0.7f);
            
            yaw += yawError;
            pitch += pitchError;
            
            // Иногда "дрожание" рук (чаще при движении)
            float shakeChance = isPlayerMoving() ? 0.15f : 0.05f;
            if (Math.random() < shakeChance) {
                float shakeIntensity = errorIntensity * 0.3f;
                yaw += (float) (Math.random() - 0.5) * shakeIntensity;
                pitch += (float) (Math.random() - 0.5) * (shakeIntensity * 0.6f);
            }
            
            // Дополнительные ошибки при быстром движении цели
            if (auraAITarget != null && auraAITarget.getVelocity().length() > 0.2) {
                float velocityError = errorIntensity * 0.5f;
                yaw += (float) (Math.random() - 0.5) * velocityError;
                pitch += (float) (Math.random() - 0.5) * (velocityError * 0.8f);
            }
        }
        
        // Добавляем человеческие ошибки для режима Strong
        if (strongRandomizeAim.getValue() && enableAutoMace.getValue() && autoMaceMode.getValue() == AutoMaceMode.STRONG) {
            // Случайные ошибки в наводке с настраиваемой интенсивностью
            float errorIntensity = strongAimError.getValue();
            float yawError = (float) (Math.random() - 0.5) * errorIntensity;
            float pitchError = (float) (Math.random() - 0.5) * (errorIntensity * 0.7f);
            
            yaw += yawError;
            pitch += pitchError;
            
            // Иногда "дрожание" рук (чаще при движении)
            float shakeChance = isPlayerMoving() ? 0.15f : 0.05f;
            if (Math.random() < shakeChance) {
                float shakeIntensity = errorIntensity * 0.3f;
                yaw += (float) (Math.random() - 0.5) * shakeIntensity;
                pitch += (float) (Math.random() - 0.5) * (shakeIntensity * 0.6f);
            }
            
            // Дополнительные ошибки при быстром движении цели
            if (auraAITarget != null && auraAITarget.getVelocity().length() > 0.2) {
                float velocityError = errorIntensity * 0.5f;
                yaw += (float) (Math.random() - 0.5) * velocityError;
                pitch += (float) (Math.random() - 0.5) * (velocityError * 0.8f);
            }
        }
        
        return new double[]{yaw, pitch};
    }
    
    private void updateUltraSmoothRotation(float targetYaw, float targetPitch) {
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastRotationUpdate) / 1000.0f;
        lastRotationUpdate = currentTime;
        
        // Инициализация при первом запуске
        if (currentYaw == 0 && currentPitch == 0) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }
        
        // Нормализация углов
        targetYaw = wrapDegrees(targetYaw);
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);
        
        // Вычисляем разность углов
        float deltaYaw = wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;
        
        // Адаптивная скорость в зависимости от расстояния до цели
        float distance = (float) mc.player.distanceTo(auraAITarget);
        float adaptiveSpeed = Math.min(1.0f, Math.max(0.01f, rotationSmoothness.getValue() * (1.0f - distance / 20.0f)));
        
        // Интерполяция с учетом времени
        float smoothFactor = Math.min(1.0f, adaptiveSpeed * deltaTime * 60.0f);
        
        // Применяем плавную интерполяцию
        currentYaw += deltaYaw * smoothFactor;
        currentPitch += deltaPitch * smoothFactor;
        
        // Ограничиваем pitch
        currentPitch = MathHelper.clamp(currentPitch, -90f, 90f);
        
        // Применяем ротацию
        rotationYaw = currentYaw;
        rotationPitch = currentPitch;
        
        // Применяем к игроку, если включен ClientLook
        if (clientLook.getValue()) {
            mc.player.setYaw(currentYaw);
            mc.player.setPitch(currentPitch);
        }
    }

    private void updateSmoothAI(float targetYaw, float targetPitch) {
        if (auraAITarget == null) return;
        
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - smoothAILastUpdate) / 1000f;
        smoothAILastUpdate = currentTime;
        
        // Вычисляем разность углов
        float delta_yaw = wrapDegrees(targetYaw - smoothAICurrentYaw);
        float delta_pitch = targetPitch - smoothAICurrentPitch;
        
        // Применяем SmoothAI логику
        updateSmoothAIMovement(delta_yaw, delta_pitch, deltaTime);
    }
    
    private void updateSmoothAIMovement(float delta_yaw, float delta_pitch, float deltaTime) {
        // Вычисляем шаги для SmoothAI
        float yawStep = random(minYawStep.getValue(), maxYawStep.getValue());
        float pitchStep = random(0.5f, 1.5f);
        
        // Применяем SmoothAI скорость
        yawStep *= smoothAISpeed.getValue();
        pitchStep *= smoothAISpeed.getValue();
        
        // Ограничиваем шаги
        float deltaYaw = MathHelper.clamp(delta_yaw, -yawStep, yawStep);
        float deltaPitch = MathHelper.clamp(delta_pitch, -pitchStep, pitchStep);
        
        // Вычисляем новые углы
        float newYaw = smoothAICurrentYaw + deltaYaw;
        float newPitch = MathHelper.clamp(smoothAICurrentPitch + deltaPitch, -90.0F, 90.0F);
        
        // Добавляем SmoothAI джиттер
        if (smoothAIHumanLike.getValue()) {
            newYaw += random(-smoothAIJitter.getValue(), smoothAIJitter.getValue());
            newPitch += random(-smoothAIJitter.getValue() * 0.5f, smoothAIJitter.getValue() * 0.5f);
        }
        
        // Добавляем микро-джиттер
        if (smoothAIMicroJitter.getValue() > 0) {
            newYaw += random(-smoothAIMicroJitter.getValue(), smoothAIMicroJitter.getValue());
            newPitch += random(-smoothAIMicroJitter.getValue() * 0.5f, smoothAIMicroJitter.getValue() * 0.5f);
        }
        
        // Применяем GCD fix как в Ares
        double gcdFix = (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;
        
        // Обновляем SmoothAI углы
        smoothAICurrentYaw = newYaw;
        smoothAICurrentPitch = newPitch;
        
        // Применяем GCD fix и обновляем rotationYaw/rotationPitch
        rotationYaw = (float) (smoothAICurrentYaw - (smoothAICurrentYaw - rotationYaw) % gcdFix);
        rotationPitch = (float) (smoothAICurrentPitch - (smoothAICurrentPitch - rotationPitch) % gcdFix);
        
        // Обновляем lookingAtHitbox
        lookingAtHitbox = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange(), getWallRange(), Aura.RayTrace.valueOf(rayTrace.getValue().name()));
    }

    private boolean autoCrit() {
        boolean reasonForSkipCrit =
                !smartCrit.getValue().isEnabled()
                        || mc.player.getAbilities().flying
                        || (mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled())
                        || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                        || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                        || Managers.PLAYER.isInWeb();

        if (hitTicks > 0)
            return false;

        if (pauseInInventory.getValue() && Managers.PLAYER.inInventory)
            return false;

        if (getAttackCooldown() < attackCooldown.getValue() && !oldDelay.getValue().isEnabled())
            return false;

        if (ModuleManager.criticals.isEnabled() && ModuleManager.criticals.mode.is(Criticals.Mode.Grim))
            return true;

        boolean mergeWithTargetStrafe = !ModuleManager.targetStrafe.isEnabled() || !ModuleManager.targetStrafe.jump.getValue();
        boolean mergeWithSpeed = !ModuleManager.speed.isEnabled() || mc.player.isOnGround();

        if (!mc.options.jumpKey.isPressed() && mergeWithTargetStrafe && mergeWithSpeed && !onlySpace.getValue() && !autoJump.getValue())
            return true;

        if (mc.player.isInLava() || mc.player.isSubmergedInWater())
            return true;

        if (!mc.options.jumpKey.isPressed() && isAboveWater())
            return true;

        // я хз почему оно не критует когда фд больше 1.14
        if (mc.player.fallDistance > 1 && mc.player.fallDistance < 1.14)
            return false;

        if (!reasonForSkipCrit)
            return !mc.player.isOnGround() && mc.player.fallDistance > (shouldRandomizeFallDistance() ? MathUtility.random(0.15f, 0.7f) : critFallDistance.getValue());
        return true;
    }

    private boolean shieldBreaker(boolean instant) {
        int axeSlot = InventoryUtility.getAxe().slot();
        if (axeSlot == -1) return false;
        if (!shieldBreaker.getValue()) return false;
        if (!(auraAITarget instanceof PlayerEntity)) return false;
        if (!((PlayerEntity) auraAITarget).isUsingItem() && !instant) return false;
        if (((PlayerEntity) auraAITarget).getOffHandStack().getItem() != Items.SHIELD && ((PlayerEntity) auraAITarget).getMainHandStack().getItem() != Items.SHIELD)
            return false;

        if (axeSlot >= 9) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.interactionManager.attackEntity(mc.player, auraAITarget);
            swingHand();
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        } else {
            sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
            mc.interactionManager.attackEntity(mc.player, auraAITarget);
            swingHand();
            sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        }
        hitTicks = 10;
        return true;
    }

    private void swingHand() {
        switch (attackHand.getValue()) {
            case OffHand -> mc.player.swingHand(Hand.OFF_HAND);
            case MainHand -> mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    public boolean isAboveWater() {
        return mc.player.isSubmergedInWater() || mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos().add(0, -0.4, 0))).getBlock() == Blocks.WATER;
    }

    public float getAttackCooldownProgressPerTick() {
        return (float) (1.0 / mc.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) * (20.0 * ThunderHack.TICK_TIMER * (tpsSync.getValue() ? Managers.SERVER.getTPSFactor() : 1f)));
    }

    public float getAttackCooldown() {
        return MathHelper.clamp(((float) ((ILivingEntity) mc.player).getLastAttackedTicks() + attackBaseTime.getValue()) / getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
    }

    private void updateTarget() {
        Entity candidat = findTarget();

        if (auraAITarget == null) {
            auraAITarget = candidat;
            return;
        }

        if (sort.getValue() == Sort.FOV || !lockTarget.getValue())
            auraAITarget = candidat;

        if (candidat instanceof ProjectileEntity)
            auraAITarget = candidat;

        if (skipEntity(auraAITarget))
            auraAITarget = null;
    }

    // ... остальные методы будут добавлены в следующей части

    private boolean shouldRandomizeDelay() {
        return randomHitDelay.getValue() && (mc.player.isOnGround() || mc.player.fallDistance < 0.12f || mc.player.isSwimming() || mc.player.isFallFlying());
    }

    private boolean shouldRandomizeFallDistance() {
        return randomHitDelay.getValue() && !shouldRandomizeDelay();
    }

    private Vec3d getLegitLook(Entity entity) {
        if (entity == null) return null;
        return entity.getBoundingBox().getCenter();
    }

    private Entity findTarget() {
        Entity target = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Entity entity : mc.world.getEntities()) {
            if (entity == null) continue;
            
            // Дополнительная проверка на предметы и неживые объекты
            if (entity instanceof net.minecraft.entity.ItemEntity) continue;
            if (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity) continue;
            if (entity instanceof net.minecraft.entity.vehicle.BoatEntity) continue;
            if (entity instanceof net.minecraft.entity.vehicle.MinecartEntity) continue;
            
            // Проверяем, что это живая сущность или проектил
            if (!(entity instanceof LivingEntity) && !(entity instanceof ProjectileEntity)) continue;
            
            if (!skipEntity(entity)) {
                double distance = mc.player.squaredDistanceTo(entity);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    target = entity;
                }
            }
        }
        
        return target;
    }

    private boolean skipEntity(Entity entity) {
        if (entity == null) return true;
        if (entity == mc.player) return true;
        if (!entity.isAlive()) return true;
        if (entity instanceof ArmorStandEntity) return true;
        if (entity instanceof CatEntity) return true;
        if (!InteractionUtility.isVecInFOV(entity.getPos(), fov.getValue())) return true;

        // Исключаем предметы (ItemEntity) - они не должны быть целями
        if (entity instanceof net.minecraft.entity.ItemEntity) return true;
        
        // Исключаем другие неживые объекты
        if (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.BoatEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.MinecartEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.ChestMinecartEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.FurnaceMinecartEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.TntMinecartEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.HopperMinecartEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.SpawnerMinecartEntity) return true;
        if (entity instanceof net.minecraft.entity.vehicle.CommandBlockMinecartEntity) return true;

        // Дополнительные проверки для предотвращения кика
        if (entity instanceof PlayerEntity player) {
            if (ModuleManager.antiBot.isEnabled() && ModuleManager.antiBot.bots.contains(entity))
                return true;
            if (player == mc.player || Managers.FRIEND.isFriend(player))
                return true;
            // Проверяем, что игрок не в креативе
            if (player.isCreative()) return true;
            // Проверяем, что игрок не в спектаторском режиме
            if (player.isSpectator()) return true;
        }

        // Проверяем, что сущность не является проектилом
        if (entity instanceof ProjectileEntity) {
            return !isBullet(entity);
        }

        return !isInRange(entity);
    }
    
    private boolean isBullet(Entity entity) {
        return (entity instanceof ShulkerBulletEntity || entity instanceof FireballEntity)
                && entity.isAlive()
                && PlayerUtility.squaredDistanceFromEyes(entity.getPos()) < getSquaredRange()
                && Projectiles.getValue();
    }

    private boolean isInRange(Entity entity) {
        double distance = mc.player.squaredDistanceTo(entity);
        return distance <= getSquaredRange();
    }

    private double getSquaredRange() {
        return getRange() * getRange();
    }
    
    private boolean isValidTarget(Entity target) {
        if (target == null) return false;
        
        // Проверяем, что цель не является предметом или неживым объектом
        if (target instanceof net.minecraft.entity.ItemEntity) return false;
        if (target instanceof net.minecraft.entity.decoration.ItemFrameEntity) return false;
        if (target instanceof net.minecraft.entity.vehicle.BoatEntity) return false;
        if (target instanceof net.minecraft.entity.vehicle.MinecartEntity) return false;
        
        // Проверяем, что цель не является игроком в недопустимом состоянии
        if (target instanceof PlayerEntity player) {
            // Проверяем, что игрок не в креативе
            if (player.isCreative()) return false;
            // Проверяем, что игрок не в спектаторском режиме
            if (player.isSpectator()) return false;
            // Проверяем, что игрок не мертв
            if (player.getHealth() <= 0) return false;
            // Проверяем, что игрок не является ботом
            if (ModuleManager.antiBot.isEnabled() && ModuleManager.antiBot.bots.contains(target)) return false;
            // Проверяем, что игрок не является другом
            if (Managers.FRIEND.isFriend(player)) return false;
        }
        
        // Проверяем, что цель не является проектилом (кроме разрешенных)
        if (target instanceof ProjectileEntity) {
            return isBullet(target);
        }
        
        return true;
    }
    
    private boolean isPlayerInValidState() {
        if (mc.player == null) return false;
        
        // Проверяем, что игрок не мертв
        if (mc.player.getHealth() <= 0) return false;
        
        // Проверяем, что игрок не в меню
        if (mc.currentScreen != null) return false;
        
        // Проверяем, что игрок не телепортируется слишком быстро
        if (mc.player.getVelocity().length() > 10) return false;
        
        // Проверяем, что игрок не в недопустимом состоянии
        if (mc.player.isSleeping()) return false;
        if (mc.player.isDead()) return false;
        
        return true;
    }
    
    private boolean isTargetInValidRange(Entity target) {
        if (target == null) return false;
        
        // Используем точное расстояние, а не квадрат для более точной проверки
        double distance = mc.player.distanceTo(target);
        float maxRange = getRange();
        
        // Проверяем препятствия для дистанции через стены
        if (wallRange.getValue() > 0) {
            HitResult hitResult = mc.world.raycast(new RaycastContext(
                mc.player.getEyePos(),
                target.getBoundingBox().getCenter(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                // Если есть препятствие, используем только дистанцию через стены
                maxRange = getWallRange();
            }
        }
        
        // Строгая проверка дистанции
        if (distance > maxRange) {
            return false;
        }
        
        // Дополнительная проверка для предотвращения кика за Reach
        if (antiKick.getValue() && distance > maxAttackRange.getValue()) {
            return false;
        }
        
        return true;
    }
    
    private boolean checkHitboxHit(float yaw, float pitch) {
        if (auraAITarget == null) return false;
        
        // Получаем позицию игрока
        Vec3d playerPos = mc.player.getEyePos();
        
        // Вычисляем направление взгляда
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        
        Vec3d direction = new Vec3d(x, y, z);
        
        // Вычисляем максимальную дальность
        double maxDistance = getRange();
        if (wallRange.getValue() > 0) {
            maxDistance = Math.max(maxDistance, wallRange.getValue());
        }
        
        // Создаем луч от позиции игрока
        Vec3d endPos = playerPos.add(direction.multiply(maxDistance));
        
        // Проверяем пересечение с хитбоксом цели
        Box targetBox = auraAITarget.getBoundingBox();
        
        // Расширяем хитбокс для более точного попадания
        if (preciseHitbox.getValue()) {
            double expansion = 0.1; // Небольшое расширение для компенсации неточностей
            targetBox = targetBox.expand(expansion);
        }
        
        // Проверяем пересечение луча с хитбоксом
        Optional<Vec3d> intersection = targetBox.raycast(playerPos, endPos);
        
        if (intersection.isPresent()) {
            // Дополнительная проверка для предотвращения атак через стены
            if (rayTrace.getValue() != RayTrace.OFF) {
                HitResult hitResult = mc.world.raycast(new RaycastContext(
                    playerPos,
                    intersection.get(),
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
                ));
                
                // Если луч пересекает блок до цели, не атакуем
                if (hitResult.getType() != HitResult.Type.MISS) {
                    return false;
                }
            }
            
            return true;
        }
        
        return false;
    }




    public enum RayTrace {
        OFF, OnlyTarget, AllEntities
    }

    public enum Sort {
        LowestDistance, HighestDistance, LowestHealth, HighestHealth, LowestDurability, HighestDurability, FOV
    }

    public enum Switch {
        Normal, None, Silent
    }

    public enum Resolver {
        Off, Advantage, Predictive, BackTrack
    }

    public enum AttackHand {
        MainHand, OffHand, None
    }

    public enum ESP {
        Off, ThunderHack, NurikZapen, PhasmoZapen, Skull, Rounded, CelkaPasta, ThunderHackV2
    }

    public enum AccelerateOnHit {
        Off, Yaw, Pitch, Both
    }

    public enum WallsBypass {
        Off, V1, V2
    }
    
    public enum AutoMaceMode {
        LITE, STRONG
    }
    
    public enum HitboxMode {
        Center, Head, Chest, Legs
    }
    
    @Override
    public void onRender3D(MatrixStack matrices) {
        if (auraAITarget == null || esp.getValue() == ESP.Off) return;
        
        switch (esp.getValue()) {
            case ThunderHack -> {
                // ThunderHack ESP rendering
                if (auraAITarget instanceof LivingEntity) {
                    Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0)));
                    Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0), 2f));
                }
            }
            case NurikZapen -> {
                // NurikZapen ESP rendering
                if (auraAITarget instanceof LivingEntity) {
                    Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0)));
                    Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0), 2f));
                }
            }
            case PhasmoZapen -> {
                // PhasmoZapen ESP rendering
                thunder.hack.utility.render.animation.PhasmoMark.render(auraAITarget);
            }
            case Skull -> {
                // Skull ESP rendering
                thunder.hack.utility.render.animation.SkullMark.render(auraAITarget);
            }
            case Rounded -> {
                // Rounded ESP rendering
                thunder.hack.utility.render.animation.RoundedMark.render(auraAITarget);
            }
            case CelkaPasta -> {
                // CelkaPasta ESP rendering
                if (auraAITarget instanceof LivingEntity) {
                    Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0)));
                    Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0), 2f));
                }
            }
            case ThunderHackV2 -> {
                // ThunderHackV2 ESP rendering
                if (auraAITarget instanceof LivingEntity) {
                    Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0)));
                    Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(auraAITarget.getBoundingBox(), HudEditor.getColor(0), 2f));
                }
            }
        }
        
        // ClientLook для плавной ротации
        if (clientLook.getValue() && ultraSmoothRotation.getValue() && auraAITarget != null) {
            mc.player.setYaw((float) Render2DEngine.interpolate(mc.player.prevYaw, rotationYaw, Render3DEngine.getTickDelta()));
            mc.player.setPitch((float) Render2DEngine.interpolate(mc.player.prevPitch, rotationPitch, Render3DEngine.getTickDelta()));
        }
    }
}
