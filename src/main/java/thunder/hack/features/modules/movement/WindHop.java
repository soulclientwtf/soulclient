package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import thunder.hack.events.impl.EventPlayerJump;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

public class WindHop extends Module {
    public enum Mode {
        RAGE,
        LEGIT
    }
    
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.RAGE);
    private final Setting<Boolean> autoJump = new Setting<>("AutoJump", false);
    private final Setting<Boolean> jumpPrediction = new Setting<>("JumpPrediction", true);

    private final Timer timer = new Timer();
    private final Timer jumpTimer = new Timer();
    private boolean hasUsedWindChargeThisJump = false;
    private boolean wasOnGround = false;
    private boolean isJumping = false;
    private int jumpTicks = 0;
    private double jumpStartY = 0;
    private double lastY = 0;
    private boolean isFalling = false;
    private boolean useOnLanding = false;
    private static final float PITCH_ANGLE = 90.0f;
    private static final int OPTIMAL_JUMP_TICKS = 0;
    private static final double LANDING_THRESHOLD = 0.01;

    public WindHop() {
        super("WindHop", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
        wasOnGround = mc.player != null && mc.player.isOnGround();
        if (mc.player != null) {
            lastY = mc.player.getY();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
    }

    private void resetState() {
        hasUsedWindChargeThisJump = false;
        isJumping = false;
        jumpTicks = 0;
        isFalling = false;
        jumpStartY = 0;
        useOnLanding = false;
    }

    @EventHandler
    public void onJump(EventPlayerJump event) {
        if (mc.player == null) return;

        isJumping = true;
        jumpTicks = 0;
        hasUsedWindChargeThisJump = false;
        jumpStartY = mc.player.getY();
        isFalling = false;
        useOnLanding = false;

        if (!jumpPrediction.getValue() && canUseWindCharge()) {
            useWindCharge();
            hasUsedWindChargeThisJump = true;
        } else if (jumpPrediction.getValue() && canUseWindCharge()) {
            useWindCharge();
            hasUsedWindChargeThisJump = true;
            useOnLanding = true;
        }
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (mc.player == null || mc.world == null) return;

        double currentY = mc.player.getY();
        boolean wasJustFalling = isFalling;
        isFalling = currentY < lastY;
        lastY = currentY;

        if (mc.player.isOnGround()) {
            if (!wasOnGround) {
                if (useOnLanding && jumpPrediction.getValue() && canUseWindCharge()) {
                    useWindCharge();
                }

                isJumping = false;
                jumpTicks = 0;
                isFalling = false;
                useOnLanding = false;
            }

            hasUsedWindChargeThisJump = false;

            wasOnGround = true;

            if (autoJump.getValue() && jumpTimer.passedMs(500) && canUseWindCharge()) {
                mc.player.jump();
                jumpTimer.reset();
                isJumping = true;
                jumpTicks = 0;
                jumpStartY = mc.player.getY();
                isFalling = false;
                useOnLanding = false;

                if (!jumpPrediction.getValue() && canUseWindCharge()) {
                    useWindCharge();
                    hasUsedWindChargeThisJump = true;
                } else if (jumpPrediction.getValue() && canUseWindCharge()) {
                    useWindCharge();
                    hasUsedWindChargeThisJump = true;
                    useOnLanding = true;
                }
            }
        } else {
            wasOnGround = false;

            if (jumpPrediction.getValue() && isJumping && !hasUsedWindChargeThisJump) {
                jumpTicks++;

                if (jumpTicks == OPTIMAL_JUMP_TICKS && canUseWindCharge()) {
                    useWindCharge();
                    hasUsedWindChargeThisJump = true;
                }

                if (!hasUsedWindChargeThisJump && isFalling && !wasJustFalling && canUseWindCharge()) {
                    useWindCharge();
                    hasUsedWindChargeThisJump = true;
                }

                double jumpHeight = currentY - jumpStartY;
                if (!hasUsedWindChargeThisJump && jumpHeight >= 0.8 && jumpHeight <= 1.0 && canUseWindCharge()) {
                    useWindCharge();
                    hasUsedWindChargeThisJump = true;
                }

                if (!hasUsedWindChargeThisJump && isFalling && canUseWindCharge()) {
                    double distanceToGround = getDistanceToGround();
                    if (distanceToGround <= LANDING_THRESHOLD && distanceToGround > 0) {
                        useWindCharge();
                        hasUsedWindChargeThisJump = true;
                    }
                }
            }
        }
    }

    private double getDistanceToGround() {
        double y = mc.player.getY();
        for (double i = y; i > 0; i -= 0.01) {
            if (i < 0) return 0;
            if (!mc.world.getBlockState(mc.player.getBlockPos().down((int)Math.ceil(y - i))).isAir()) {
                return y - i;
            }
        }
        return 0;
    }

    private boolean canUseWindCharge() {
        if (mode.getValue() == Mode.RAGE) {
            SearchInvResult windChargeResult = InventoryUtility.findItemInHotBar(Items.WIND_CHARGE);
            return windChargeResult.found();
        } else { // LEGIT mode
            // Проверяем есть ли заряд ветра в хотбаре или в инвентаре
            SearchInvResult hotbarResult = InventoryUtility.findItemInHotBar(Items.WIND_CHARGE);
            SearchInvResult inventoryResult = InventoryUtility.findItemInInventory(Items.WIND_CHARGE);
            return hotbarResult.found() || inventoryResult.found();
        }
    }

    private void useWindCharge() {
        if (mode.getValue() == Mode.RAGE) {
            useWindChargeRage();
        } else { // LEGIT mode
            useWindChargeLegit();
        }
    }
    
    private void useWindChargeRage() {
        SearchInvResult windChargeResult = InventoryUtility.findItemInHotBar(Items.WIND_CHARGE);
        if (windChargeResult.found()) {
            int originalSlot = mc.player.getInventory().selectedSlot;
            float originalYaw = mc.player.getYaw();
            float originalPitch = mc.player.getPitch();

            mc.player.getInventory().selectedSlot = windChargeResult.slot();
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(windChargeResult.slot()));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, PITCH_ANGLE, mc.player.isOnGround()));
            InteractionUtility.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, originalYaw, PITCH_ANGLE));
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            mc.player.getInventory().selectedSlot = originalSlot;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, originalPitch, mc.player.isOnGround()));

            timer.reset();
            hasUsedWindChargeThisJump = true;
        }
    }
    
    private void useWindChargeLegit() {
        SearchInvResult hotbarResult = InventoryUtility.findItemInHotBar(Items.WIND_CHARGE);
        SearchInvResult inventoryResult = InventoryUtility.findItemInInventory(Items.WIND_CHARGE);
        
        if (hotbarResult.found()) {
            // Используем заряд из хотбара
            useWindChargeFromSlotLegit(hotbarResult.slot());
        } else if (inventoryResult.found()) {
            // Берем заряд из инвентаря и кладем в хотбар
            int originalSlot = mc.player.getInventory().selectedSlot;
            int targetSlot = 0; // Используем первый слот хотбара
            
            // Сохраняем предмет из первого слота хотбара
            var originalItem = mc.player.getInventory().getStack(targetSlot);
            var windChargeItem = mc.player.getInventory().getStack(inventoryResult.slot());
            
            // Меняем предметы местами в инвентаре клиента
            mc.player.getInventory().setStack(targetSlot, windChargeItem);
            mc.player.getInventory().setStack(inventoryResult.slot(), originalItem);
            
            // Переключаемся на слот с зарядом
            mc.player.getInventory().selectedSlot = targetSlot;
            
            // Используем заряд
            useWindChargeFromSlotLegit(targetSlot);
            
            // Возвращаем предметы обратно
            mc.player.getInventory().setStack(inventoryResult.slot(), windChargeItem);
            mc.player.getInventory().setStack(targetSlot, originalItem);
            
            // Возвращаемся к исходному слоту
            mc.player.getInventory().selectedSlot = originalSlot;
        }
    }
    
    private void useWindChargeFromSlot(int slot) {
        int originalSlot = mc.player.getInventory().selectedSlot;
        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();

        // Переключаемся на слот с зарядом ветра
        mc.player.getInventory().selectedSlot = slot;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        
        // Поворачиваем голову вниз (90 градусов по pitch)
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, 90.0f, mc.player.isOnGround()));
        
        // Используем заряд ветра (кидаем под себя)
        InteractionUtility.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, originalYaw, 90.0f));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        
        // Возвращаем голову в исходное положение
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, originalPitch, mc.player.isOnGround()));
        
        // Возвращаемся к исходному слоту
        mc.player.getInventory().selectedSlot = originalSlot;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));

        timer.reset();
        hasUsedWindChargeThisJump = true;
    }
    
    private void useWindChargeFromSlotLegit(int slot) {
        int originalSlot = mc.player.getInventory().selectedSlot;
        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();

        // Переключаемся на слот с зарядом ветра (только клиентская сторона)
        mc.player.getInventory().selectedSlot = slot;
        
        // Поворачиваем голову вниз (90 градусов по pitch) - только клиентская сторона
        mc.player.setYaw(originalYaw);
        mc.player.setPitch(90.0f);
        
        // Используем заряд ветра (кидаем под себя) - реальное использование предмета
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        // Возвращаем голову в исходное положение
        mc.player.setYaw(originalYaw);
        mc.player.setPitch(originalPitch);
        
        // Возвращаемся к исходному слоту
        mc.player.getInventory().selectedSlot = originalSlot;

        timer.reset();
        hasUsedWindChargeThisJump = true;
    }
}