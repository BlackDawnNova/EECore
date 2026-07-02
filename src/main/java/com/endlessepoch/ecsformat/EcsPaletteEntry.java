package com.endlessepoch.ecsformat;

import java.util.List;

/** Palette entry: character → block ID + tags. / 调色板条目：字符 → 方块ID + 标记 */
public record EcsPaletteEntry(char character, String blockId, List<String> tags) {}
