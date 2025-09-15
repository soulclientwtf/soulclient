package thunder.hack.features.modules.misc;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.ClientSettings;
import thunder.hack.setting.Setting;

public class GodMode extends Module {
    public GodMode() {
        super("GodMode", Category.MISC);
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.WaitStanding);

    private boolean canClip = true;

    @Override
    public void onEnable() {
        if (mc.player == null) return;

        if (mode.is(Mode.Instant)) {
            clip(-30);
            toggle();
        } else if (mode.is(Mode.WaitStanding)) {
            if (isStandingStill()) {
                clip(-30);
                toggle();
            }
        }

    }

    @Override
    public void onUpdate() {
        if (mc.player == null) return;

        if (mode.is(Mode.WaitStanding)) {
            if (isStandingStill()) {
                clip(-30);
                toggle();
            }
        } else if (mode.is(Mode.Auto)) {
            if (isStandingStill() && canClip) {
                clip(-30);
                canClip = false;
            } else if (!isStandingStill()) {
                canClip = true;
            }
        }
    }

    //ВАДИК ЕСЛИ ТЫ НАХУЙ НЕ ДОДЕЛАЕШЬ РЕЖИМЫ ТО Я ТЕБЕ НАХУЙ ВЕНЫ ВСКРОЮ
    private boolean isStandingStill() {
        return mc.player.input.movementForward == 0 && mc.player.input.movementSideways == 0;
    }

    private void clip(double b) {
        if (ClientSettings.clipCommandMode.getValue() == ClientSettings.ClipCommandMode.Matrix) {
            for (int i = 0; i < 10; ++i)
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));

            for (int i = 0; i < 10; ++i)
                mc.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.PositionAndOnGround(
                                mc.player.getX(), mc.player.getY() + b, mc.player.getZ(), false));
        } else {
            mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), mc.player.getY() + b, mc.player.getZ(), false));
        }

        mc.player.setPosition(mc.player.getX(), mc.player.getY() + b, mc.player.getZ());
    }

    public enum Mode {
        Instant,       // сразу годмодит подрузумеваю остановку плеера
        WaitStanding,  // бле блую
        Auto           // оч выажна авто годмодит
    }
}