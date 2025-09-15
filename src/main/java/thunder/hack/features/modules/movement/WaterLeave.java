package thunder.hack.features.modules.movement;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;

public class WaterLeave extends Module {
    public WaterLeave() {
        super("WaterLeave", Category.MOVEMENT);
    }

    public enum Mode {
        SoulSand,
        Magma
    }

    public final Setting<Mode> mode = new Setting<>("Mode", Mode.SoulSand);

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isTouchingWater()) return;

        BlockPos below = mc.player.getBlockPos().down();
        if (mode.getValue() == Mode.SoulSand && mc.world.getBlockState(below).getBlock() == Blocks.SOUL_SAND
                || mode.getValue() == Mode.Magma && mc.world.getBlockState(below).getBlock() == Blocks.MAGMA_BLOCK) {

            double vx = mc.player.getVelocity().x;
            double vz = mc.player.getVelocity().z;
            mc.player.setVelocity(vx, 1.7, vz);
        }
    }
}
