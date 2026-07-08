package com.endlessepoch.core.api.multiblock;

/** Multiblock part types. / 多方块部件类型。 */
public enum PartType {
    INPUT_BUS("eecore.part.input_bus"),
    OUTPUT_BUS("eecore.part.output_bus"),
    INPUT_HATCH("eecore.part.input_hatch"),
    OUTPUT_HATCH("eecore.part.output_hatch"),
    INPUT_ASSEMBLY("eecore.part.input_assembly"),
    OUTPUT_ASSEMBLY("eecore.part.output_assembly");

    private final String translationKey;
    PartType(String key) { this.translationKey = key; }
    public String getTranslationKey() { return translationKey; }
}
