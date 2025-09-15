package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import thunder.hack.events.impl.EventMove;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class NoSlowNew extends Module {
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.GrimPacket);
    private final Setting<Integer> packetInterval = new Setting<>("Packet Interval", 2, 1, 10);
    private final Setting<Boolean> shieldBypass = new Setting<>("Shield", true);
    private final Setting<Boolean> foodBypass = new Setting<>("Food", true);
    private final Setting<Boolean> strictRotation = new Setting<>("Strict Rotation", true);

    private int tickCounter = 0;
    private boolean wasUsingItem = false;

    public NoSlowNew() {
        super("NoSlowNew", Module.Category.MOVEMENT);
    }

    public enum Mode {
        GrimPacket,
        GrimStrict
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        wasUsingItem = false;
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mc.player == null) return;

        tickCounter++;

        // Сбрасываем счетчик, если игрок перестал использовать предмет
        if (!mc.player.isUsingItem() && wasUsingItem) {
            tickCounter = 0;
            wasUsingItem = false;
        }

        if (mc.player.isUsingItem()) {
            wasUsingItem = true;

            if (!shouldBypass(mc.player.getActiveItem())) {
                return;
            }

            // Отправка пакетов каждые N тиков
            if (tickCounter % packetInterval.getValue() == 0) {
                sendGrimPackets();
            }
        }
    }

    @EventHandler
    public void onMove(EventMove event) {
        if (mc.player == null || !mc.player.isUsingItem()) return;

        if (!shouldBypass(mc.player.getActiveItem())) {
            return;
        }

        // Полная отмена замедления движения
        event.setX(event.getX() * 1.0);
        event.setZ(event.getZ() * 1.0);
    }

    private void sendGrimPackets() {
        ClientPlayerEntity player = mc.player;

        if (mode.getValue() == Mode.GrimPacket) {
            // Основной метод - отправка пакетов использования предмета
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(
                    Hand.OFF_HAND, id, player.getPitch(), player.getYaw()
            ));

            // Дополнительные пакеты для обхода
            if (tickCounter % 3 == 0) {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(
                        Hand.MAIN_HAND, id, player.getPitch(), player.getYaw()
                ));
            }
        }
        else if (mode.getValue() == Mode.GrimStrict) {
            // Строгий режим с ротацией
            float yaw = player.getYaw() + (tickCounter % 2 == 0 ? 0.1f : -0.1f);
            float pitch = strictRotation.getValue() ?
                    Math.max(-90, Math.min(90, player.getPitch() + (tickCounter % 3 == 0 ? 0.1f : -0.1f))) :
                    player.getPitch();

            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(
                    Hand.OFF_HAND, id, pitch, yaw
            ));

            // Дополнительный movement packet для обхода
            sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                    player.getX(), player.getY(), player.getZ(), player.isOnGround()
            ));
        }
    }

    private boolean shouldBypass(ItemStack item) {
        if (shieldBypass.getValue() && item.getItem() == Items.SHIELD) {
            return true;
        }

        if (foodBypass.getValue() && (
                item.getItem() == Items.ENCHANTED_GOLDEN_APPLE ||
                        item.getItem() == Items.GOLDEN_APPLE ||
                        item.getItem() == Items.APPLE ||
                        item.getItem() == Items.BREAD ||
                        item.getItem() == Items.COOKED_BEEF ||
                        item.getItem() == Items.COOKED_CHICKEN ||
                        item.getItem() == Items.COOKED_PORKCHOP ||
                        item.getItem() == Items.COOKED_MUTTON ||
                        item.getItem() == Items.COOKED_RABBIT ||
                        item.getItem() == Items.COOKED_SALMON ||
                        item.getItem() == Items.PUMPKIN_PIE ||
                        item.getItem() == Items.CARROT ||
                        item.getItem() == Items.BAKED_POTATO ||
                        item.getItem() == Items.DRIED_KELP ||
                        item.getItem() == Items.MELON_SLICE ||
                        item.getItem() == Items.GLOW_BERRIES ||
                        item.getItem() == Items.SWEET_BERRIES ||
                        item.getItem() == Items.CHORUS_FRUIT ||
                        item.getItem() == Items.HONEY_BOTTLE)) {
            return true;
        }

        return false;
    }
}