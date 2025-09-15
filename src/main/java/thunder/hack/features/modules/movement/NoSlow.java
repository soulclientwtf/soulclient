package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import thunder.hack.events.impl.EventKeyboardInput;
import thunder.hack.events.impl.EventTick;
import thunder.hack.events.impl.EventMove;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.player.MovementUtility;

public class NoSlow extends Module {
    public NoSlow() {
        super("NoSlow", Category.MOVEMENT);
    }

    @Override
    public void onDisable() {
        returnSneak = false;

        // Сброс GrimV4 переменных
        grimV4OriginalOffhandItem = net.minecraft.item.ItemStack.EMPTY;
        grimV4IsSwapping = false;
        grimV4BowSwapTimer = 0;
        grimV4CrossbowSwapTimer = 0;

        // Сброс GrimBow/GrimCrossbow переменных
        originalOffhandItem = net.minecraft.item.ItemStack.EMPTY;
        isSwapping = false;
        bowSwapTimer = 0;
        crossbowSwapTimer = 0;
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.NCP);
    private final Setting<Boolean> mainHand = new Setting<>("MainHand", true);
    private final Setting<SettingGroup> selection = new Setting<>("Selection", new SettingGroup(false, 0));
    private final Setting<Boolean> food = new Setting<>("Food", true).addToGroup(selection);
    private final Setting<Boolean> projectiles = new Setting<>("Projectiles", true).addToGroup(selection);
    private final Setting<Boolean> shield = new Setting<>("Shield", true).addToGroup(selection);
    public final Setting<Boolean> soulSand = new Setting<>("SoulSand", true).addToGroup(selection);
    public final Setting<Boolean> honey = new Setting<>("Honey", true).addToGroup(selection);
    public final Setting<Boolean> slime = new Setting<>("Slime", true).addToGroup(selection);
    public final Setting<Boolean> ice = new Setting<>("Ice", true).addToGroup(selection);
    public final Setting<Boolean> sweetBerryBush = new Setting<>("SweetBerryBush", true).addToGroup(selection);
    public final Setting<Boolean> sneak = new Setting<>("Sneak", false).addToGroup(selection);
    public final Setting<Boolean> crawl = new Setting<>("Crawl", false).addToGroup(selection);

    // GrimLatest settings (убрано grimBoost, так как теперь работает через canNoSlow)

    // SexyGrim settings
    private final Setting<Boolean> sexyGrimDelay = new Setting<>("SexyGrim Delay", true, v -> mode.getValue() == Mode.SexyGrim);
    private final Setting<Integer> sexyGrimTicks = new Setting<>("SexyGrim Ticks", 3, 1, 8, v -> mode.getValue() == Mode.SexyGrim && sexyGrimDelay.getValue());
    private final Setting<Boolean> sexyGrimRandom = new Setting<>("SexyGrim Random", true, v -> mode.getValue() == Mode.SexyGrim);
    private final Setting<Boolean> sexyGrimSmart = new Setting<>("SexyGrim Smart", true, v -> mode.getValue() == Mode.SexyGrim);
    private final Setting<Float> sexyGrimChance = new Setting<>("SexyGrim Chance", 0.7f, 0.1f, 1.0f, v -> mode.getValue() == Mode.SexyGrim);

    // GrimV4 settings
    private final Setting<GrimV4Mode> grimV4Mode = new Setting<>("GrimV4 Mode", GrimV4Mode.GrimBow, v -> mode.getValue() == Mode.GrimV4);
    private final Setting<Boolean> grimV4OnlyOnGround = new Setting<>("GrimV4 Only On Ground", false, v -> mode.getValue() == Mode.GrimV4);

    // GrimBow/GrimCrossbow settings
    private final Setting<Boolean> onlyOnGround = new Setting<>("Only On Ground", false, v -> mode.getValue() == Mode.GrimBow || mode.getValue() == Mode.GrimCrossbow);

    private boolean returnSneak;
    public static int ticks = 0;

    // GrimV3 variables
    private int grimV3Ticks = 0;

    // GrimV4 variables
    private net.minecraft.item.ItemStack grimV4OriginalOffhandItem = net.minecraft.item.ItemStack.EMPTY;
    private boolean grimV4IsSwapping = false;
    private long grimV4BowSwapTimer = 0;
    private long grimV4CrossbowSwapTimer = 0;

    // GrimBow/GrimCrossbow variables
    private net.minecraft.item.ItemStack originalOffhandItem = net.minecraft.item.ItemStack.EMPTY;
    private boolean isSwapping = false;
    private long bowSwapTimer = 0;
    private long crossbowSwapTimer = 0;

    // SexyGrim variables
    private int sexyGrimCounter = 0;
    private boolean sexyGrimActive = false;
    private int sexyGrimLastAction = 0;
    private int sexyGrimSkipTicks = 0;
    private boolean sexyGrimWasMoving = false;

    @Override
    public void onUpdate() {
        if (returnSneak) {
            mc.options.sneakKey.setPressed(false);
            mc.player.setSprinting(true);
            returnSneak = false;
        }

        if (mc.player.isUsingItem() && !mc.player.isRiding() && !mc.player.isFallFlying()) {
            switch (mode.getValue()) {
                case NCP -> {
                    // NCP режим - ничего не делаем
                }
                case StrictNCP -> sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                case MusteryGrief -> {
                    if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
                        mc.options.sneakKey.setPressed(true);
                        returnSneak = true;
                    }
                }
                case Grim -> {
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                    } else if (mainHand.getValue()) {
                        // TODO rotations
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    }
                }
                case Matrix -> {
                    if (mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                        mc.player.setVelocity(mc.player.getVelocity().x * 0.3, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.3);
                    } else if (mc.player.fallDistance > 0.2f)
                        mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
                }
                case GrimNew -> {
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                    } else if (mainHand.getValue() && (mc.player.getItemUseTime() <= 3 || mc.player.age % 2 == 0)) {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    }
                }
                case Matrix2 -> {
                    if (mc.player.isOnGround())
                        if (mc.player.age % 2 == 0)
                            mc.player.setVelocity(mc.player.getVelocity().x * 0.5f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.5f);
                        else
                            mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
                }
                case LFCraft -> {
                    if (mc.player.getItemUseTime() <= 3)
                        sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, mc.player.getBlockPos().up(), Direction.NORTH, id));
                }
                case AresMine -> {
                    // AresMine обход - как GrimNew но с луком в левой руке при использовании предметов
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                        sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                    } else if (mainHand.getValue() && (mc.player.getItemUseTime() <= 3 || mc.player.age % 2 == 0)) {
                        // Ищем лук в инвентаре и ставим в левую руку
                        int bowSlot = findBowSlot();
                        if (bowSlot != -1) {
                            // Меняем лук в левую руку
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, bowSlot, 40, SlotActionType.SWAP, mc.player);
                        }
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    }
                }
                case Matrix3 -> {
                    // Matrix3 режим - обрабатывается в onKeyboardInput
                }
                case GrimLatest -> {
                    // GrimLatest режим - обрабатывается в onTick и onMove
                }
                case Vanilla -> {
                    // Vanilla режим - отменяем замедление через canNoSlow()
                }
                case HolyWorld -> {
                    // HolyWorld режим - отправляем пакеты для отмены замедления
                    if (mc.player.getOffHandStack().getItem() == Items.SHIELD && mc.player.getActiveHand() == Hand.MAIN_HAND ||
                            mc.player.getOffHandStack().getComponents().contains(DataComponentTypes.FOOD) && mc.player.getActiveHand() == Hand.MAIN_HAND) {
                        return;
                    }

                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(mc.player.getActiveHand(), id, mc.player.getYaw(), mc.player.getPitch()));
                    sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(mc.player.getActiveHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                }
                case SexyGrim -> {
                    // SexyGrim - улучшенный Grim с обходом античита
                    handleSexyGrim();
                }
                case GrimV3 -> {
                    // GrimV3 - новый обход на основе CClickWindowPacket
                    handleGrimV3();
                }
                case GrimV4 -> {
                    // GrimV4 - обход с переключением лука/арбалета
                    handleGrimV4();
                }
                case GrimOld -> {
                    // GrimOld - обрабатывается в onMove
                }
                case Test -> {
                    // Test - обрабатывается в onMove
                }
                case GrimBow -> {
                    // GrimBow - обрабатывается в onMove
                }
                case GrimCrossbow -> {
                    // GrimCrossbow - обрабатывается в onMove
                }
            }
        }
    }

    @EventHandler
    public void onKeyboardInput(EventKeyboardInput e) {
        if (mode.getValue() == Mode.Matrix3 && mc.player.isUsingItem() && !mc.player.isFallFlying()) {
            mc.player.input.movementForward *= 5f;
            mc.player.input.movementSideways *= 5f;
            float mult = 1f;

            if (mc.player.isOnGround()) {
                if (mc.player.input.movementForward != 0 && mc.player.input.movementSideways != 0) {
                    mc.player.input.movementForward *= 0.35f;
                    mc.player.input.movementSideways *= 0.35f;
                } else {
                    mc.player.input.movementForward *= 0.5f;
                    mc.player.input.movementSideways *= 0.5f;
                }
            } else {
                if (mc.player.input.movementForward != 0 && mc.player.input.movementSideways != 0) {
                    mult = 0.47f;
                } else {
                    mult = 0.67f;
                }
            }
            mc.player.input.movementForward *= mult;
            mc.player.input.movementSideways *= mult;
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (mode.getValue() == Mode.GrimLatest && mc.player != null && !mc.player.isFallFlying()) {
            if (mc.player.isUsingItem()) {
                ticks++;
                if (ticks >= 2) {
                    ticks = 0; // Сбрасываем после применения
                }
            } else {
                ticks = 0;
            }
        }

        // GrimV3 логика
        if (mode.getValue() == Mode.GrimV3 && mc.player != null && !mc.player.isFallFlying()) {
            if (mc.player.isUsingItem()) {
                grimV3Ticks++;
            } else {
                grimV3Ticks = 0;
            }
        }

        // GrimBow логика
        if (mode.getValue() == Mode.GrimBow && mc.player != null) {
            if (!mc.player.isUsingItem() && isSwapping) {
                isSwapping = false;
            }
        }

        // GrimCrossbow логика
        if (mode.getValue() == Mode.GrimCrossbow && mc.player != null) {
            if (!mc.player.isUsingItem() && isSwapping) {
                isSwapping = false;
            }
        }

        // GrimV4 логика
        if (mode.getValue() == Mode.GrimV4 && mc.player != null) {
            if (!mc.player.isUsingItem() && grimV4IsSwapping) {
                grimV4IsSwapping = false;
            }
        }
    }

    @EventHandler
    public void onMove(EventMove event) {
        // GrimLatest теперь работает через canNoSlow()

        // GrimOld логика (аналог SlowWalkingEvent из оригинального кода)
        if (mode.getValue() == Mode.GrimOld && mc.player != null && !mc.player.isFallFlying()) {
            if (mc.player.isUsingItem()) {
                // Отправляем пакет как в оригинале
                sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, mc.player.getBlockPos().up(), Direction.NORTH));
                // Отменяем замедление (аналог e.cancel())
                event.setX(event.getX() * 1.0);
                event.setZ(event.getZ() * 1.0);
            }
        }

        // Test логика (аналог SlowWalkingEvent из оригинального кода)
        if (mode.getValue() == Mode.Test && mc.player != null && !mc.player.isFallFlying()) {
            if (mc.player.isUsingItem()) {
                // Проверяем условия как в оригинале
                if ((mc.player.getOffHandStack().getItem() == Items.SHIELD ||
                        mc.player.getOffHandStack().getComponents().contains(DataComponentTypes.FOOD)) &&
                        mc.player.getActiveHand() == Hand.MAIN_HAND) {
                    return;
                }

                // Отправляем пакеты как в оригинале
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(mc.player.getActiveHand(), id, mc.player.getYaw(), mc.player.getPitch()));
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(mc.player.getActiveHand() == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                // Отменяем замедление (аналог e.cancel())
                event.setX(event.getX() * 1.0);
                event.setZ(event.getZ() * 1.0);
            }
        }

        // GrimBow логика (аналог SlowWalkingEvent из оригинального кода)
        if (mode.getValue() == Mode.GrimBow && mc.player != null && !mc.player.isFallFlying()) {
            if (!mc.player.isUsingItem() || !MovementUtility.isMoving()) return;

            if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

            // Отменяем замедление (аналог e.cancel())
            event.setX(event.getX() * 1.0);
            event.setZ(event.getZ() * 1.0);

            // Проверяем таймер для переключения
            long currentTime = System.currentTimeMillis();
            if (currentTime - bowSwapTimer >= 30) {
                performBowSwap();
                bowSwapTimer = currentTime;
            }
        }

        // GrimCrossbow логика (аналог SlowWalkingEvent из оригинального кода)
        if (mode.getValue() == Mode.GrimCrossbow && mc.player != null && !mc.player.isFallFlying()) {
            if (!mc.player.isUsingItem() || !MovementUtility.isMoving()) return;

            if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

            // Отменяем замедление (аналог e.cancel())
            event.setX(event.getX() * 1.0);
            event.setZ(event.getZ() * 1.0);

            // Проверяем таймер для переключения
            long currentTime = System.currentTimeMillis();
            if (currentTime - crossbowSwapTimer >= 30) {
                performCrossbowSwap();
                crossbowSwapTimer = currentTime;
            }
        }

        // GrimV4 логика (аналог SlowWalkingEvent из оригинального кода)
        if (mode.getValue() == Mode.GrimV4 && mc.player != null && !mc.player.isFallFlying()) {
            if (!mc.player.isUsingItem() || !MovementUtility.isMoving()) return;

            if (grimV4OnlyOnGround.getValue() && !mc.player.isOnGround()) return;

            // Отменяем замедление (аналог e.cancel())
            event.setX(event.getX() * 1.0);
            event.setZ(event.getZ() * 1.0);

            // Проверяем таймеры для переключения в зависимости от режима
            long currentTime = System.currentTimeMillis();

            if (grimV4Mode.getValue() == GrimV4Mode.GrimBow) {
                if (currentTime - grimV4BowSwapTimer >= 30) {
                    performGrimV4BowSwap();
                    grimV4BowSwapTimer = currentTime;
                }
            } else if (grimV4Mode.getValue() == GrimV4Mode.GrimCrossbow) {
                if (currentTime - grimV4CrossbowSwapTimer >= 30) {
                    performGrimV4CrossbowSwap();
                    grimV4CrossbowSwapTimer = currentTime;
                }
            }
        }
    }

    public boolean canNoSlow() {
        if (mode.getValue() == Mode.Matrix3)
            return false;

        if (mode.getValue() == Mode.GrimLatest)
            return ticks >= 2;

        if (mode.getValue() == Mode.GrimV3)
            return grimV3Ticks > 6;

        if (mode.getValue() == Mode.Vanilla)
            return true;

        if (!food.getValue() && mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD))
            return false;

        if (!shield.getValue() && mc.player.getActiveItem().getItem() == Items.SHIELD)
            return false;

        if (!projectiles.getValue()
                && (mc.player.getActiveItem().getItem() == Items.CROSSBOW || mc.player.getActiveItem().getItem() == Items.BOW || mc.player.getActiveItem().getItem() == Items.TRIDENT))
            return false;

        if (mode.getValue() == Mode.MusteryGrief && mc.player.isOnGround() && !mc.options.jumpKey.isPressed())
            return false;

        if (!mainHand.getValue() && mc.player.getActiveHand() == Hand.MAIN_HAND)
            return false;

        if ((mc.player.getOffHandStack().getComponents().contains(DataComponentTypes.FOOD) || mc.player.getOffHandStack().getItem() == Items.SHIELD)
                && (mode.getValue() == Mode.GrimNew || mode.getValue() == Mode.Grim || mode.getValue() == Mode.SexyGrim) && mc.player.getActiveHand() == Hand.MAIN_HAND)
            return false;

        return true;
    }

    private int findBowSlot() {
        // Ищем лук в инвентаре (слоты 9-44)
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem() == Items.BOW) {
                return i >= 36 ? i - 36 : i;
            }
        }
        return -1;
    }

    private void handleSexyGrim() {
        if (mc.player == null) return;

        // Проверяем движение игрока
        boolean isMoving = mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;

        // Проверяем, использует ли игрок предмет
        if (!mc.player.isUsingItem()) {
            sexyGrimActive = false;
            sexyGrimCounter = 0;
            sexyGrimSkipTicks = 0;
            sexyGrimWasMoving = false;
            return;
        }

        // Умная логика - пропускаем некоторые тики
        if (sexyGrimSmart.getValue() && sexyGrimSkipTicks > 0) {
            sexyGrimSkipTicks--;
            return;
        }

        // Проверяем шанс активации
        if (Math.random() > sexyGrimChance.getValue()) {
            return;
        }

        // Увеличиваем счетчик только если игрок движется
        if (isMoving) {
            sexyGrimCounter++;
            sexyGrimWasMoving = true;
        } else if (sexyGrimWasMoving) {
            // Если игрок перестал двигаться, сбрасываем счетчик
            sexyGrimCounter = 0;
            sexyGrimWasMoving = false;
            return;
        }

        // Определяем нужное количество тиков с большей случайностью
        int requiredTicks = sexyGrimTicks.getValue();
        if (sexyGrimRandom.getValue()) {
            // Более сложная случайность
            int randomFactor = (int) (Math.random() * 5) - 2; // -2 до +2
            requiredTicks += randomFactor;
            requiredTicks = Math.max(1, Math.min(8, requiredTicks));
        }

        // Если еще не достигли нужного количества тиков
        if (sexyGrimDelay.getValue() && sexyGrimCounter < requiredTicks) {
            return;
        }

        // Проверяем, не слишком ли часто активируемся
        if (mc.player.age - sexyGrimLastAction < 2) {
            return;
        }

        // Логика обхода замедления с вариациями
        if (mc.player.getActiveHand() == Hand.OFF_HAND) {
            // Если используется предмет в левой руке - переключаем слоты с вариациями
            int currentSlot = mc.player.getInventory().selectedSlot;

            // Случайно выбираем паттерн переключения
            int pattern = (int) (Math.random() * 3);
            switch (pattern) {
                case 0 -> {
                    // Паттерн 1: +1, +2, возврат
                    int newSlot1 = (currentSlot + 1) % 9;
                    int newSlot2 = (currentSlot + 2) % 9;
                    sendPacket(new UpdateSelectedSlotC2SPacket(newSlot1));
                    sendPacket(new UpdateSelectedSlotC2SPacket(newSlot2));
                    sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                }
                case 1 -> {
                    // Паттерн 2: -1, +1, возврат
                    int newSlot1 = (currentSlot - 1 + 9) % 9;
                    int newSlot2 = (currentSlot + 1) % 9;
                    sendPacket(new UpdateSelectedSlotC2SPacket(newSlot1));
                    sendPacket(new UpdateSelectedSlotC2SPacket(newSlot2));
                    sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                }
                case 2 -> {
                    // Паттерн 3: +2, -1, возврат
                    int newSlot1 = (currentSlot + 2) % 9;
                    int newSlot2 = (currentSlot - 1 + 9) % 9;
                    sendPacket(new UpdateSelectedSlotC2SPacket(newSlot1));
                    sendPacket(new UpdateSelectedSlotC2SPacket(newSlot2));
                    sendPacket(new UpdateSelectedSlotC2SPacket(currentSlot));
                }
            }
        } else if (mainHand.getValue()) {
            // Если используется предмет в правой руке - отправляем пакеты взаимодействия
            if (sexyGrimActive || mc.player.getItemUseTime() <= 3 || mc.player.age % 3 == 0) {
                // Случайно выбираем руку для взаимодействия
                Hand targetHand = Math.random() > 0.5 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(targetHand, id, mc.player.getYaw(), mc.player.getPitch()));
                sexyGrimActive = true;
            }
        }

        // Обновляем время последнего действия
        sexyGrimLastAction = mc.player.age;

        // Сбрасываем счетчик после применения
        if (sexyGrimDelay.getValue()) {
            sexyGrimCounter = 0;
        }

        // Случайно пропускаем следующие тики
        if (sexyGrimSmart.getValue()) {
            sexyGrimSkipTicks = (int) (Math.random() * 3) + 1; // 1-3 тика
        }
    }

    private void handleGrimV3() {
        if (mc.player == null || mc.player.isFallFlying()) return;

        // GrimV3 логика - отправляем пакеты для обхода замедления
        if (grimV3Ticks < 5) {
            // Отправляем пакет клика по слоту 40 (левая рука)
            clickSlot(40, SlotActionType.PICKUP);
        }

        // Если тиков больше 6, отменяем замедление через canNoSlow
        if (grimV3Ticks > 6) {
            // Логика отмены замедления будет в canNoSlow()
        }
    }

    private void handleGrimV4() {
        // GrimV4 обрабатывается в onMove
    }

    private void performGrimV4BowSwap() {
        net.minecraft.item.ItemStack currentOffhandItem = mc.player.getOffHandStack();

        int bowSlot = findGrimV4BowInInventory();

        if (bowSlot != -1) {
            if (!grimV4IsSwapping) {
                grimV4OriginalOffhandItem = currentOffhandItem.copy();
                grimV4IsSwapping = true;
            }

            swapGrimV4ItemToOffhand(bowSlot);

            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));

            if (!grimV4OriginalOffhandItem.isEmpty()) {
                int originalItemSlot = findGrimV4ItemInInventory(grimV4OriginalOffhandItem);
                if (originalItemSlot != -1) {
                    swapGrimV4ItemToOffhand(originalItemSlot);
                }
            }
        }
    }

    private void performGrimV4CrossbowSwap() {
        net.minecraft.item.ItemStack currentOffhandItem = mc.player.getOffHandStack();

        int crossbowSlot = findGrimV4CrossbowInInventory();

        if (crossbowSlot != -1) {
            if (!grimV4IsSwapping) {
                grimV4OriginalOffhandItem = currentOffhandItem.copy();
                grimV4IsSwapping = true;
            }

            swapGrimV4ItemToOffhand(crossbowSlot);

            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));

            if (!grimV4OriginalOffhandItem.isEmpty()) {
                int originalItemSlot = findGrimV4ItemInInventory(grimV4OriginalOffhandItem);
                if (originalItemSlot != -1) {
                    swapGrimV4ItemToOffhand(originalItemSlot);
                }
            }
        }
    }

    private int findGrimV4BowInInventory() {
        // Ищем лук в хотбаре (слоты 0-8)
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.BOW) {
                return i;
            }
        }

        // Ищем лук в инвентаре (слоты 9-35)
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.BOW) {
                return i;
            }
        }

        return -1;
    }

    private int findGrimV4CrossbowInInventory() {
        // Ищем арбалет в хотбаре (слоты 0-8)
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.CROSSBOW) {
                return i;
            }
        }

        // Ищем арбалет в инвентаре (слоты 9-35)
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.CROSSBOW) {
                return i;
            }
        }

        return -1;
    }

    private int findGrimV4ItemInInventory(net.minecraft.item.ItemStack itemToFind) {
        // Ищем предмет в хотбаре (слоты 0-8)
        for (int i = 0; i < 9; i++) {
            if (net.minecraft.item.ItemStack.areItemsEqual(mc.player.getInventory().getStack(i), itemToFind)) {
                return i;
            }
        }

        // Ищем предмет в инвентаре (слоты 9-35)
        for (int i = 9; i < 36; i++) {
            if (net.minecraft.item.ItemStack.areItemsEqual(mc.player.getInventory().getStack(i), itemToFind)) {
                return i;
            }
        }

        return -1;
    }

    private void swapGrimV4ItemToOffhand(int inventorySlot) {
        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        int offhandSlot = 40; // Слот левой руки

        // Меняем предмет в левую руку
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, containerSlot, offhandSlot, SlotActionType.SWAP, mc.player);
    }

    // GrimBow/GrimCrossbow методы (точно как в оригинале)
    private void performBowSwap() {
        net.minecraft.item.ItemStack currentOffhandItem = mc.player.getOffHandStack();
        int bowSlot = findBowInInventory();

        if (bowSlot != -1) {
            if (!isSwapping) {
                originalOffhandItem = currentOffhandItem.copy();
                isSwapping = true;
            }

            swapItemToOffhand(bowSlot);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));

            if (!originalOffhandItem.isEmpty()) {
                int originalItemSlot = findItemInInventory(originalOffhandItem);
                if (originalItemSlot != -1) {
                    swapItemToOffhand(originalItemSlot);
                }
            }
        }
    }

    private void performCrossbowSwap() {
        net.minecraft.item.ItemStack currentOffhandItem = mc.player.getOffHandStack();
        int crossbowSlot = findCrossbowInInventory();

        if (crossbowSlot != -1) {
            if (!isSwapping) {
                originalOffhandItem = currentOffhandItem.copy();
                isSwapping = true;
            }

            swapItemToOffhand(crossbowSlot);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));

            if (!originalOffhandItem.isEmpty()) {
                int originalItemSlot = findItemInInventory(originalOffhandItem);
                if (originalItemSlot != -1) {
                    swapItemToOffhand(originalItemSlot);
                }
            }
        }
    }

    private int findBowInInventory() {
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.BOW) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.BOW) {
                return i;
            }
        }

        return -1;
    }

    private int findCrossbowInInventory() {
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.CROSSBOW) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.CROSSBOW) {
                return i;
            }
        }

        return -1;
    }

    private int findItemInInventory(net.minecraft.item.ItemStack itemToFind) {
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (net.minecraft.item.ItemStack.areItemsEqual(stack, itemToFind)) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (net.minecraft.item.ItemStack.areItemsEqual(stack, itemToFind)) {
                return i;
            }
        }

        return -1;
    }

    private void swapItemToOffhand(int inventorySlot) {
        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        int offhandSlot = 40; // Offhand slot

        // Меняем предмет в левую руку
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, containerSlot, offhandSlot, SlotActionType.SWAP, mc.player);
    }

    public enum Mode {
        NCP, StrictNCP, Matrix, Grim, MusteryGrief, GrimNew, Matrix2, LFCraft, Matrix3, AresMine, GrimLatest, Vanilla, HolyWorld, SexyGrim, GrimV3, GrimV4, GrimOld, Test, GrimBow, GrimCrossbow
    }

    public enum GrimV4Mode {
        GrimBow, GrimCrossbow
    }
}