package thunder.hack.features.modules.combat;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

import java.util.function.Function;

public class AutoSwap extends Module {
    public AutoSwap() {
        super("AutoSwap", Category.COMBAT);
    }

    private final Setting<Item1> item1 = new Setting<>("Item1", Item1.Totem);
    private final Setting<Item2> item2 = new Setting<>("Item2", Item2.Shield);
    private final Setting<Bind> swapButton = new Setting<>("SwapButton", new Bind(GLFW.GLFW_KEY_CAPS_LOCK, false, false));
    private final Setting<Bypass> bypass = new Setting<>("Bypass", Bypass.None);
    private final Setting<Boolean> pauseEating = new Setting<>("PauseEating", true);
    private final Setting<Boolean> pauseAura = new Setting<>("PauseAura", true);

    private boolean swapToSecond = false;
    private final Timer delayTimer = new Timer();
    private final Timer cursorResetTimer = new Timer();
    private boolean wasSprinting = false;
    private boolean wasMovingForward = false;
    private int cursorResetTicks = 0;

    public enum Item1 {
        Totem, PlayerHead, EnchantedTotem, Shield
    }

    public enum Item2 {
        Totem, PlayerHead, EnchantedTotem, Shield
    }

    public enum Bypass {
        None, Matrix, GrimSwap, FunTime, SpookyTime
    }

    @Override
    public void onEnable() {
        swapToSecond = false;
        delayTimer.reset();
        cursorResetTimer.reset();
        cursorResetTicks = 0;
    }

    @Override
    public void onDisable() {
        swapToSecond = false;
        delayTimer.reset();
        cursorResetTimer.reset();
        cursorResetTicks = 0;
        if (wasSprinting) {
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            wasSprinting = false;
        }
        if (wasMovingForward) {
            mc.options.forwardKey.setPressed(true);
            wasMovingForward = false;
        }
        // Способ 1: Повторный сброс курсора при отключении
        resetCursor();
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (mc.player == null || mc.world == null) return;

        if (pauseEating.getValue() && mc.player.isUsingItem()) {
            return;
        }

        // Способ 5: Сброс курсора через EventSync
        if (cursorResetTicks > 0) {
            cursorResetTicks--;
            if (cursorResetTicks <= 0 && mc.currentScreen == null) {
                resetCursor();
            }
        }

        if (isKeyPressed(swapButton.getValue().getKey())) {
            long delay = bypass.getValue() == Bypass.SpookyTime ? (600 + (long) (Math.random() * 400)) : 250;
            if (delayTimer.every(delay)) {
                swapToSecond = !swapToSecond;
                swapItem();
            }
        }
    }

    private void swapItem() {
        if (mc.currentScreen instanceof GenericContainerScreen) return;

        if (pauseAura.getValue() && ModuleManager.aura.isEnabled()) {
            ModuleManager.aura.pause();
        }

        Item targetItem = getTargetItem();
        SearchInvResult hotbarResult = InventoryUtility.findItemInHotBar(targetItem);
        SearchInvResult invResult = InventoryUtility.findItemInInventory(targetItem);
        int slot = findItemSlot(targetItem, isEnchantedTotemTarget());

        if (slot == -1 && !hotbarResult.found() && !invResult.found()) return;

        // Сохраняем состояние движения вперёд
        wasMovingForward = mc.options.forwardKey.isPressed();

        switch (bypass.getValue()) {
            case None -> {
                if (slot >= 9) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                } else {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    mc.player.getInventory().selectedSlot = slot;
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                    mc.player.getInventory().selectedSlot = prevSlot;
                }
                delayTimer.reset();
            }
            case Matrix -> {
                wasSprinting = mc.player.isSprinting();
                if (wasSprinting) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }

                sendSequencedPacket((Function<Integer, Packet<?>>) id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));

                if (slot >= 9) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                } else {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    mc.player.getInventory().selectedSlot = slot;
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                    mc.player.getInventory().selectedSlot = prevSlot;
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                }

                if (wasSprinting) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                }
                delayTimer.reset();
            }
            case GrimSwap -> {
                if (slot >= 9) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                } else {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    mc.player.getInventory().selectedSlot = slot;
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                    mc.player.getInventory().selectedSlot = prevSlot;
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                }
                delayTimer.reset();
            }
            case FunTime -> {
                wasSprinting = mc.player.isSprinting();
                if (wasSprinting) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }

                // Открываем инвентарь через клиент
                mc.setScreen(new InventoryScreen(mc.player));

                // Выполняем свап
                if (slot >= 9) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                } else {
                    int prevSlot = mc.player.getInventory().selectedSlot;
                    sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    mc.player.getInventory().selectedSlot = slot;
                    sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                    sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
                    mc.player.getInventory().selectedSlot = prevSlot;
                }

                // Закрываем инвентарь с увеличенной задержкой и применяем все способы сброса курсора
                long delay = 100 + (long) (Math.random() * 50); // Способ 1: Увеличенная задержка
                Managers.ASYNC.run(() -> {
                    mc.player.closeScreen();
                    mc.setScreen(null);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));

                    // Способ 1: Повторный сброс курсора
                    resetCursor();

                    // Способ 2: Принудительное обновление состояния окна
                    GLFW.glfwPollEvents();

                    // Способ 3: Эмуляция клиентского клика
                    sendSequencedPacket((Function<Integer, Packet<?>>) id -> new ClickSlotC2SPacket(
                            mc.player.currentScreenHandler.syncId, mc.player.currentScreenHandler.getRevision(),
                            mc.player.getInventory().selectedSlot, 0, SlotActionType.PICKUP,
                            mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot),
                            new Int2ObjectOpenHashMap<>()
                    ));

                    // Способ 4: Переключение фокуса окна
                    GLFW.glfwFocusWindow(mc.getWindow().getHandle());

                    // Восстанавливаем движение и спринт
                    if (wasMovingForward) {
                        mc.options.forwardKey.setPressed(true);
                    }
                    if (wasSprinting) {
                        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                    }

                    // Способ 5: Установка тиков для проверки в EventSync
                    cursorResetTicks = 10; // Проверять 10 тиков
                }, delay);

                delayTimer.reset();
            }
            case SpookyTime -> {
                if (mc.currentScreen != null) return;

                if (hotbarResult.found()) {
                    InventoryUtility.saveAndSwitchTo(hotbarResult.slot());
                    sendSequencedPacket((Function<Integer, Packet<?>>) id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, id));
                    InventoryUtility.returnSlot();
                } else if (invResult.found()) {
                    wasSprinting = mc.player.isSprinting();
                    if (wasSprinting) {
                        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    }

                    clickSlot(invResult.slot(), mc.player.getInventory().selectedSlot, SlotActionType.SWAP);
                    sendSequencedPacket((Function<Integer, Packet<?>>) id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN, id));
                    clickSlot(invResult.slot(), mc.player.getInventory().selectedSlot, SlotActionType.SWAP);
                    sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));

                    if (wasSprinting) {
                        sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                    }
                }

                delayTimer.reset();
            }
        }
    }

    private void resetCursor() {
        mc.mouse.unlockCursor();
        mc.mouse.lockCursor();
        GLFW.glfwSetInputMode(mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    private Item getTargetItem() {
        if (swapToSecond) {
            return switch (item2.getValue()) {
                case Totem -> Items.TOTEM_OF_UNDYING;
                case PlayerHead -> Items.PLAYER_HEAD;
                case EnchantedTotem -> Items.TOTEM_OF_UNDYING;
                case Shield -> Items.SHIELD;
            };
        } else {
            return switch (item1.getValue()) {
                case Totem -> Items.TOTEM_OF_UNDYING;
                case PlayerHead -> Items.PLAYER_HEAD;
                case EnchantedTotem -> Items.TOTEM_OF_UNDYING;
                case Shield -> Items.SHIELD;
            };
        }
    }

    private boolean isEnchantedTotemTarget() {
        return (swapToSecond && item2.getValue() == Item2.EnchantedTotem) || (!swapToSecond && item1.getValue() == Item1.EnchantedTotem);
    }

    private int findItemSlot(Item item, boolean enchantedTotem) {
        for (int i = 0; i < 45; i++) {
            int slot = i >= 36 ? i - 36 : i;
            Item stackItem = mc.player.getInventory().getStack(slot).getItem();
            if (stackItem == item) {
                if (enchantedTotem && item == Items.TOTEM_OF_UNDYING) {
                    if (mc.player.getInventory().getStack(slot).hasEnchantments()) {
                        return slot;
                    }
                } else if (item == Items.TOTEM_OF_UNDYING) {
                    if (!mc.player.getInventory().getStack(slot).hasEnchantments()) {
                        return slot;
                    }
                } else {
                    return slot;
                }
            }
        }
        return -1;
    }

    private void sendSequencedPacket(Function<Integer, net.minecraft.network.packet.Packet<?>> packetSupplier) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendPacket(packetSupplier.apply(mc.player.currentScreenHandler.getRevision()));
    }

    public static void clickSlot(int slot, int button, SlotActionType type) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, type, mc.player);
    }
}