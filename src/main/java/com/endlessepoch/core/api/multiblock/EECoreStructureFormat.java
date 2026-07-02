package com.endlessepoch.core.api.multiblock;

import com.endlessepoch.ecsformat.EcsFormat;

/** Delegates to {@link EcsFormat} for backward compat. / 委托给 EcsFormat 以保持向后兼容 */
public final class EECoreStructureFormat {
    public static final byte[] MAGIC = EcsFormat.MAGIC;
    public static final byte VERSION = EcsFormat.VERSION;
    public static final byte FLAG_COMPRESSED = EcsFormat.FLAG_COMPRESSED;
    public static final byte VOXEL_8BIT = EcsFormat.VOXEL_8BIT;
    public static final byte VOXEL_16BIT = EcsFormat.VOXEL_16BIT;
    public static final String EXTENSION = ".ecs";
    public static final char CHAR_AIR = EcsFormat.CHAR_AIR;
    public static final char CHAR_CONTROLLER = EcsFormat.CHAR_CONTROLLER;
    public static final char CHAR_WILDCARD = EcsFormat.CHAR_WILDCARD;
    public static final int AIR_INDEX = EcsFormat.AIR_INDEX;
    public static final int CONTROLLER_INDEX = EcsFormat.CONTROLLER_INDEX;
    public static final int WILDCARD_INDEX = EcsFormat.WILDCARD_INDEX;
    public static final int FIRST_USER_INDEX = EcsFormat.FIRST_USER_INDEX;
    public static final char[] SAFE_CHAR_POOL = EcsFormat.SAFE_CHAR_POOL;
    private EECoreStructureFormat() {}
}
