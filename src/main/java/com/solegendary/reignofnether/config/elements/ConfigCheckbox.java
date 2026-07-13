package com.solegendary.reignofnether.config.elements;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigCheckbox extends AbstractButton {

    private static final ResourceLocation SELECTED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected");
    private static final ResourceLocation SELECTED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private static final ResourceLocation UNSELECTED = ResourceLocation.withDefaultNamespace("widget/checkbox");
    private static final ResourceLocation UNSELECTED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");

    private final ModConfigSpec.ConfigValue<Boolean> configValue;
    private final String labelOn;
    private final String labelOff;
    private boolean selected;

    public ConfigCheckbox(ModConfigSpec.ConfigValue<Boolean> configValue, String label) {
        super(0, 0, 1, 1, Component.literal(label));
        this.configValue = configValue;
        this.labelOn = label;
        this.labelOff = label;
        this.selected = configValue.get();
    }

    public ConfigCheckbox(ModConfigSpec.ConfigValue<Boolean> configValue, String labelOn, String labelOff) {
        super(0, 0, 1, 1, Component.literal(configValue.get() ? labelOn : labelOff));
        this.configValue = configValue;
        this.labelOn = labelOn;
        this.labelOff = labelOff;
        this.selected = configValue.get();
    }

    @Override
    public void onPress() {
        this.selected = !this.selected;
        configValue.set(this.selected);
        setMessage(Component.literal(this.selected ? labelOn : labelOff));
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int boxSize = 17;
        int boxY = getY() + (getHeight() - boxSize) / 2;
        ResourceLocation sprite = selected
                ? (isHoveredOrFocused() ? SELECTED_HIGHLIGHTED : SELECTED)
                : (isHoveredOrFocused() ? UNSELECTED_HIGHLIGHTED : UNSELECTED);
        guiGraphics.blitSprite(sprite, getX(), boxY, boxSize, boxSize);
        guiGraphics.drawString(font, getMessage(), getX() + boxSize + 4,
                getY() + (getHeight() - font.lineHeight) / 2, 0xE0E0E0);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    public ConfigCheckbox pos(int x, int y) {
        super.setPosition(x, y);
        return this;
    }

    public ConfigCheckbox size(int w, int h) {
        super.setWidth(w);
        super.setHeight(h);
        return this;
    }
}
