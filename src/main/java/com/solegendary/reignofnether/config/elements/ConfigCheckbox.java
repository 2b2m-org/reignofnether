package com.solegendary.reignofnether.config.elements;


import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigCheckbox extends Checkbox {

    private final ModConfigSpec.ConfigValue<Boolean> configValue;
    private final String labelOn;
    private final String labelOff;

    public ConfigCheckbox(ModConfigSpec.ConfigValue<Boolean> configValue, String label) {
        super(0, 0, 1, 1, Component.literal(label), configValue.get(), true);
        this.configValue = configValue;
        this.labelOn = label;
        this.labelOff = label;
    }

    public ConfigCheckbox(ModConfigSpec.ConfigValue<Boolean> configValue, String labelOn, String labelOff) {
        super(0, 0, 1, 1, Component.literal(configValue.get() ? labelOn : labelOff), configValue.get(), true);
        this.configValue = configValue;
        this.labelOn = labelOn;
        this.labelOff = labelOff;
    }

    @Override
    public void onPress() {
        super.onPress();
        configValue.set(this.selected());
        setMessage(Component.literal(this.selected() ? labelOn : labelOff));
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