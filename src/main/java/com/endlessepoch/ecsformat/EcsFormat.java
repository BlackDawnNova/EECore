package com.endlessepoch.ecsformat;

/**
 * ECS binary format constants. / ECS 二进制格式常量
 */
public final class EcsFormat {
    public static final byte[] MAGIC = {0x45, 0x45, 0x43, 0x53}; // "EECS"
    public static final byte VERSION = 3;
    public static final byte VOXEL_COMPRESSED = 2; // v3+: non-air only, 8-bit palette index
    public static final byte VOXEL_16BIT_COMPRESSED = 3; // v3+: non-air only, 16-bit palette index
    public static final byte FLAG_COMPRESSED = 0x01;
    public static final byte FLAG_FRAME_BASED = 0x02;
    public static final byte VOXEL_8BIT = 0;
    public static final byte VOXEL_16BIT = 1;
    public static final String EXTENSION = ".ecs";
    public static final char CHAR_AIR = 'A';
    public static final char CHAR_CONTROLLER = 'K';
    public static final char CHAR_WILDCARD = '#';
    public static final int AIR_INDEX = 0;
    public static final int CONTROLLER_INDEX = 1;
    public static final int WILDCARD_INDEX = 2;
    public static final int FIRST_USER_INDEX = 3;
    public static final char[] SAFE_CHAR_POOL = buildSafeCharPool();

    private static char[] buildSafeCharPool() {
        StringBuilder sb = new StringBuilder();
        for (char c = 'B'; c <= 'J'; c++) sb.append(c);
        for (char c = 'L'; c <= '~'; c++) { if (c != '#') sb.append(c); }
        for (char c = 0xA0; c <= 0xFE; c++) sb.append(c);
        char[] pool = new char[sb.length()];
        for (int i = 0; i < sb.length(); i++) pool[i] = sb.charAt(i);
        return pool;
    }

    private EcsFormat() {}
}
