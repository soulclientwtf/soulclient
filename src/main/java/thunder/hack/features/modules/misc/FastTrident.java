package thunder.hack.features.modules.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import thunder.hack.events.impl.EventTick;
import thunder.hack.features.modules.Module;

public class FastTrident extends Module {
    public FastTrident() {
        super("FastTrident", Category.MISC);
    }

    @EventHandler
    public void onTick(EventTick event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.getMainHandStack().getItem() != Items.TRIDENT) return;

        if (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND) {
            if (player.getItemUseTime() >= 10) {
                player.stopUsingItem();
            }
        }
    }
}
