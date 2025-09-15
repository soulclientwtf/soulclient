package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;

import java.util.ArrayList;
import java.util.List;

public class AutoTotemNew extends Module {

    // Настройки
    private final Setting<Float> healthAmount = new Setting<>("HealthAmount", 3.5f, 1f, 20f);
    private final Setting<Boolean> countTotem = new Setting<>("CountTotem", true);
    private final Setting<Boolean> checkCrystal = new Setting<>("CheckCrystal", true);
    private final Setting<Float> radiusCrystal = new Setting<>("DistanceToCrystal", 6f, 1f, 8f);
    private final Setting<Integer> swapBackDelay = new Setting<>("SwapBackDelay", 100, 10, 500);
    private final Setting<Float> fallDistance = new Setting<>("FallDistance", 15f, 5f, 125f);
    private final Setting<Boolean> switchBack = new Setting<>("SwapBack", true);
    private final Setting<Boolean> checkFall = new Setting<>("CheckFall", true);

    // Состояние
    private final List<Integer> lastItem = new ArrayList<>();
    private final Timer timerHelper = new Timer();
    private boolean swap = false;

    public AutoTotemNew() {
        super("AutoTotemNew", Category.COMBAT);
    }

    /**
     * Подсчитывает количество тотемов в инвентаре
     */
    private int foundTotemCount() {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                count++;
            }
        }
        return count;
    }


    @EventHandler
    public void onSync(EventSync e) {
        if (mc.player == null || mc.world == null) return;

        int tIndex = -1;
        int totemCount = 0;

        // Ищем тотемы в инвентаре
        for (int i = 0; i < 45; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING && tIndex == -1) {
                tIndex = i;
            }
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                totemCount++;
            }
        }

        // Проверяем условия для взятия тотема
        if ((mc.player.getHealth() < healthAmount.getValue() || checkCrystal() || checkFall(fallDistance.getValue()))
                && totemCount != 0 && tIndex != -1) {

            if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                // Меняем местами тотем с предметом в левой руке
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        tIndex < 9 ? tIndex + 36 : tIndex,
                        40, // Слот левой руки
                        SlotActionType.SWAP,
                        mc.player
                );

                swap = true;
                lastItem.add(tIndex);
            }
        } else if (switchBack.getValue() && (swap || totemCount == 0) && lastItem.size() > 0) {
            // Возвращаем предмет обратно
            if (!mc.player.getInventory().getStack(lastItem.get(0)).isEmpty()) {
                if (timerHelper.passedMs(swapBackDelay.getValue())) {
                    mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            lastItem.get(0) < 9 ? lastItem.get(0) + 36 : lastItem.get(0),
                            40, // Слот левой руки
                            SlotActionType.SWAP,
                            mc.player
                    );
                    timerHelper.reset();
                }
            }
            swap = false;
            lastItem.clear();
        }
    }

    /**
     * Проверяет падение
     */
    private boolean checkFall(float fallDist) {
        if (!checkFall.getValue()) {
            return false;
        }
        return mc.player.fallDistance > fallDist;
    }

    /**
     * Проверяет наличие кристаллов рядом
     */
    private boolean checkCrystal() {
        if (!checkCrystal.getValue()) {
            return false;
        }

        for (var entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity) {
                if (mc.player.distanceTo(entity) <= radiusCrystal.getValue()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        // Сбрасываем состояние
        swap = false;
        lastItem.clear();
        timerHelper.reset();
    }
}