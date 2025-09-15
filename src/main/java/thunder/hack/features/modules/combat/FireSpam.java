package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.features.modules.Module;
import thunder.hack.gui.notification.Notification;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

import java.util.Random;

public class FireSpam extends Module {
    private final Setting<Integer> delay = new Setting<>("Delay", 500, -1, 2000);
    private final Setting<Boolean> synchAura = new Setting<>("SynchAura", false);
    private final Timer fireworkTimer = new Timer();
    private final Random random = new Random();

    public FireSpam() {
        super("FireSpam", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (synchAura.getValue() && isAuraHittingSoon()) return;
        if (!fireworkTimer.passedMs(getDelayValue())) return;

        useFirework();
        fireworkTimer.reset();
    }

    private int getDelayValue() {
        if (delay.getValue() == -1) {
            int randomValue = random.nextInt(100);
            if (randomValue < 50) {
                return 700 + random.nextInt(201);
            } else if (randomValue < 80) {
                return 900 + random.nextInt(101);
            } else {
                return 50 + random.nextInt(650);
            }
        }
        return delay.getValue();
    }

    private void useFirework() {
        SearchInvResult hotbarFireWorkResult = InventoryUtility.findItemInHotBar(Items.FIREWORK_ROCKET);
        SearchInvResult fireWorkResult = InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET);

        InventoryUtility.saveSlot();
        if (hotbarFireWorkResult.found()) {
            hotbarFireWorkResult.switchTo();
        } else if (fireWorkResult.found()) {
            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    fireWorkResult.slot(),
                    mc.player.getInventory().selectedSlot,
                    SlotActionType.SWAP,
                    mc.player
            );
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        } else {
            logNotification("No fireworks available!", Notification.Type.ERROR);
            return;
        }

        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
        sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        InventoryUtility.returnSlot();
        if (!hotbarFireWorkResult.found() && fireWorkResult.found()) {
            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    fireWorkResult.slot(),
                    mc.player.getInventory().selectedSlot,
                    SlotActionType.SWAP,
                    mc.player
            );
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }
    }

    private boolean isAuraHittingSoon() {
        if (ModuleManager.aura.isEnabled() && Aura.target != null) {
            double distanceToTarget = mc.player.getPos().distanceTo(Aura.target.getPos());
            if (distanceToTarget <= ModuleManager.aura.attackRange.getValue()) {
                return true;
            }

            if (Aura.target.getVelocity().lengthSquared() > 0) {
                Vec3d predictedPosition = Aura.target.getPos().add(Aura.target.getVelocity().multiply(1.5));
                if (mc.player.getPos().distanceTo(predictedPosition) <= ModuleManager.aura.attackRange.getValue()) {
                    return true;
                }
            }

            if (ModuleManager.aura.minCPS.getValue() > 0 && ModuleManager.aura.maxCPS.getValue() > 0) {
                int averageCPS = (ModuleManager.aura.minCPS.getValue() + ModuleManager.aura.maxCPS.getValue()) / 2;
                double attackDelay = 1000.0 / averageCPS;
                if (mc.player.age % (int) attackDelay == 0) {
                    return true;
                }
            }
        }
        return false;
    }



    private void logNotification(String message, Notification.Type type) {
        Managers.NOTIFICATION.publicity("FireSpam", message, 2, type);
    }
}
