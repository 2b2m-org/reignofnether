package com.solegendary.reignofnether.blocks;

import net.minecraft.world.level.block.SkullBlock;

public enum SkullTypes implements SkullBlock.Type {
    STRAY("stray"),
    BOGGED("bogged"),
    DROWNED("drowned"),
    HUSK("husk");

    private final String name;

    SkullTypes(String name) {
        this.name = name;
        SkullBlock.Type.TYPES.put(name, this);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
