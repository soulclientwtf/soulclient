package thunder.hack.features.hud.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import thunder.hack.features.hud.HudElement;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.gui.font.FontRenderers;
import thunder.hack.setting.Setting;
import thunder.hack.utility.render.Render2DEngine;

import java.awt.Color;

public class ArmorHud extends HudElement {
    public ArmorHud() {
        super("ArmorHud", 60, 25);
    }

    private final Setting<Mode> mode = new Setting<>("Mode", Mode.V2);
    private final Setting<Boolean> showBackground = new Setting<>("Show Background", true);

    private enum Mode {
        V1, V2
    }
    
    private Color getDurabilityColor(int percentage) {
        if (percentage >= 50) {
            // Зеленый цвет для 50-100%
            return new Color(0, 255, 0, 255);
        } else if (percentage >= 20) {
            // Желтый цвет для 20-49%
            return new Color(255, 255, 0, 255);
        } else {
            // Красный цвет для 1-19%
            return new Color(255, 0, 0, 255);
        }
    }

    public void onRender2D(DrawContext context) {
        super.onRender2D(context);
        
        // Подсчитываем количество предметов брони для расчета ширины фона
        int armorCount = 0;
        for (ItemStack itemStack : mc.player.getInventory().armor.reversed()) {
            if (!itemStack.isEmpty()) armorCount++;
        }
        
        // Рисуем фон с BlurColor, если есть предметы брони и включен фон
        if (armorCount > 0 && showBackground.getValue() && HudEditor.hudStyle.is(HudEditor.HudStyle.Blurry)) {
            float backgroundWidth = armorCount * 20f - 2f; // 20 пикселей на предмет, -2 для корректировки
            float backgroundHeight = 28f;
            // Добавляем отступы: 2 пикселя слева, 1 пиксель справа, 1 пиксель сверху и снизу
            Render2DEngine.drawRoundedBlur(context.getMatrices(), getPosX() - 2, getPosY() - 1, backgroundWidth + 2, backgroundHeight, 2, HudEditor.blurColor.getValue().getColorObject());
        }
        
        float xItemOffset = getPosX();
        for (ItemStack itemStack : mc.player.getInventory().armor.reversed()) {
            if (itemStack.isEmpty()) continue;

            if (mode.is(Mode.V1)) {
                context.drawItem(itemStack, (int) xItemOffset, (int) getPosY());
                context.drawItemInSlot(mc.textRenderer,itemStack,  (int) xItemOffset, (int) getPosY());
            } else {
                RenderSystem.setShaderColor(0.4f,0.4f,0.4f,0.35f);
                context.drawItem(itemStack, (int) xItemOffset, (int) getPosY());
                RenderSystem.setShaderColor(1f,1f,1f,1f);
                float offset = ((itemStack.getItem() instanceof ArmorItem ai) && ai.getSlotType() == EquipmentSlot.HEAD) ? -4 : 0;
                Render2DEngine.addWindow(context.getMatrices(), (int) xItemOffset, getPosY() + offset + (15 - offset) * ((float) itemStack.getDamage() / (float) itemStack.getMaxDamage()), xItemOffset + 15, getPosY() + 15, 1f);
                context.drawItem(itemStack, (int) xItemOffset, (int) getPosY());
                Render2DEngine.popWindow();
            }
            
            // Отображаем процент прочности под предметом
            if (itemStack.isDamageable()) {
                int maxDamage = itemStack.getMaxDamage();
                int currentDamage = itemStack.getDamage();
                int durability = maxDamage - currentDamage;
                int percentage = (int) ((durability * 100.0f) / maxDamage);
                
                String percentageText = String.valueOf(percentage);
                Color durabilityColor = getDurabilityColor(percentage);
                
                // Улучшенное центрирование текста под предметом
                float textWidth = FontRenderers.sf_bold.getStringWidth(percentageText);
                float itemCenterX = xItemOffset + 10f; // Центр предмета (20px / 2)
                float textX = itemCenterX - (textWidth / 2f) - 2f; // Точное центрирование + 2 пикселя влево
                float textY = getPosY() + 17f; // Оптимальная позиция под предметом
                
                FontRenderers.sf_bold.drawString(context.getMatrices(), percentageText, textX, textY, durabilityColor.getRGB());
            }
            
            xItemOffset += 20;
        }

        // Обновляем границы в зависимости от количества предметов брони
        float backgroundWidth = armorCount > 0 ? armorCount * 20f - 2f : 60f;
        float backgroundHeight = 30f;
        
        // Учитываем отступы в границах только если фон включен
        if (showBackground.getValue() && HudEditor.hudStyle.is(HudEditor.HudStyle.Blurry)) {
            setBounds(getPosX() - 2, getPosY() - 1, backgroundWidth + 3, backgroundHeight);
        } else {
            setBounds(getPosX(), getPosY(), backgroundWidth, backgroundHeight);
        }
    }
}
