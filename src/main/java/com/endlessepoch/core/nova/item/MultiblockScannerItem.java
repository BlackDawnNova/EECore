package com.endlessepoch.core.nova.item;

import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.SyncPatternBinaryPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.endlessepoch.core.api.item.AnimatedItem;
import com.endlessepoch.core.api.item.ItemTooltipAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

public class MultiblockScannerItem extends AnimatedItem {

    // Character pool for scanned blocks — A=air, K=controller, #=wildcard are reserved
    // 扫描字符池：优先 SAFE_CHAR_POOL（兼容 8-bit .ecs），再扩展 Unicode（16-bit 用）
    private static final char[] CHAR_POOL = buildCharPool();

    private static char[] buildCharPool() {
        // Start with SAFE_CHAR_POOL for 8-bit .ecs compatibility / 优先单字节安全字符池
        java.util.ArrayList<Character> list = new java.util.ArrayList<>();
        for (char c : com.endlessepoch.ecsformat.EcsFormat.SAFE_CHAR_POOL) {
            if (c != 'A' && c != 'K' && c != '#') list.add(c);
        }
        // Extend to full range for 16-bit mode / 扩展至全范围供 16-bit 使用
        for (char c = 0x100; c <= 0xFFFE; c++) {
            if (list.size() >= 65000) break;
            // Skip surrogates and common whitespace/control chars / 跳过代理区和常见控制字符
            if (Character.isSurrogate(c) || Character.isISOControl(c)) continue;
            if (c == 'A' || c == 'K' || c == '#') continue;
            list.add(c);
        }
        char[] pool = new char[list.size()];
        for (int i = 0; i < list.size(); i++) pool[i] = list.get(i);
        return pool;
    }
    private int poolIdx = 0;
    private static final long MAX_VOLUME = 1_000_000;

    /**
     * Blocks excluded from scanning. Addon mods can register via {@link #skipBlock(Block)}.
     * 扫描排除方块集。附属 mod 可通过 skipBlock() 注册。
     */
    public static final Set<Block> SKIP_BLOCKS = new java.util.LinkedHashSet<>();
    static {
        // Category 1: Cannot exist independently / 无法独立存在
        SKIP_BLOCKS.addAll(Set.of(
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.NETHER_PORTAL, Blocks.BUBBLE_COLUMN,
            Blocks.PISTON_HEAD, Blocks.MOVING_PISTON, Blocks.FROSTED_ICE,
            Blocks.END_PORTAL, Blocks.END_GATEWAY
        ));
        // Category 2: Creative-only / 生存不可获取
        SKIP_BLOCKS.addAll(Set.of(
            Blocks.COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.BARRIER, Blocks.LIGHT,
            Blocks.JIGSAW, Blocks.SPAWNER, Blocks.BUDDING_AMETHYST, Blocks.REINFORCED_DEEPSLATE
        ));
    }

    /** Register a block to be skipped during scanning. Call in mod init. / 附属 mod 调用此方法注册需跳过的方块 */
    public static void skipBlock(Block block) {
        SKIP_BLOCKS.add(block);
    }

    public MultiblockScannerItem(Properties properties) {
        super(properties, new ItemTooltipAnimation(
                com.endlessepoch.core.api.text.AnimatedText::cyanGreen,
                com.endlessepoch.core.api.text.AnimatedText::rainbowKey,
                null,
                new String[]{
                    "eecore.scanner.tip_controller",
                    "eecore.scanner.tip_mark",
                    "eecore.scanner.tip_scan",
                    "eecore.scanner.tip_clear",
                    "eecore.scanner.tip_visualizer",
                    "eecore.scanner.tip_reset"
                }),
                true, "EECore",
                "tooltip.eecore.multiblock_scanner.title");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.no_permission").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            return scanStructure(stack, (ServerPlayer) player, level);
        }

        return markPosition(stack, player, pos);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(
                        Component.translatable("eecore.scanner.no_permission").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) clearSelections(stack, player);
            return InteractionResultHolder.success(stack);
        }
        if (level.isClientSide()) {
            net.minecraft.client.Minecraft.getInstance()
                    .setScreen(new com.endlessepoch.core.nova.client.MultiblockVisualizerScreen());
        }
        return InteractionResultHolder.success(stack);
    }

    private InteractionResult markPosition(ItemStack stack, Player player, BlockPos pos) {
        if (!(player.level().getBlockState(pos).getBlock()
                instanceof com.endlessepoch.core.nova.block.ScannerBoundaryBlock)) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.boundary_required").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        CompoundTag tag = getTag(stack);

        if (!tag.contains("pos1")) {
            tag.putIntArray("pos1", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            saveTag(stack, tag);
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.marked1",
                            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
        } else if (!tag.contains("pos2")) {
            tag.putIntArray("pos2", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            saveTag(stack, tag);
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.marked2",
                            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD), true);
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.ready").withStyle(ChatFormatting.GREEN), true);
        } else {
            tag.remove("pos2");
            tag.putIntArray("pos1", new int[]{pos.getX(), pos.getY(), pos.getZ()});
            saveTag(stack, tag);
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.reset1",
                            pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult scanStructure(ItemStack stack, ServerPlayer player, Level level) {
        CompoundTag tag = getTag(stack);
        BlockPos pos1 = getPos(tag, "pos1");
        BlockPos pos2 = getPos(tag, "pos2");

        if (pos1 == null || pos2 == null) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.need_two").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        long totalVolume = (long) sizeX * sizeY * sizeZ;
        long MAX_TOTAL = 500_000_000L;
        if (totalVolume > MAX_TOTAL) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.area_too_large", totalVolume, MAX_TOTAL)
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // Pre-scan — count + validate before allocating / 预扫描 — 先计数校验再分配内存
        int nonAirCount = 0;
        int controllerCount = 0;
        BlockPos controllerWorldPos = null;
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        java.util.List<BlockPos> controllerPositions = new java.util.ArrayList<>();
        Map<Block, Boolean> blockSeen = new LinkedHashMap<>();

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos wPos = new BlockPos(minX + x, minY + y, minZ + z);
                    BlockState state = resolveFluidBlock(level, wPos);
                    Block block = state.getBlock();

                    if (block == Blocks.AIR
                            || block instanceof com.endlessepoch.core.nova.block.ScannerBoundaryBlock
                            || SKIP_BLOCKS.contains(block)) {
                        continue;
                    }

                    nonAirCount++;
                    if (nonAirCount > MAX_VOLUME) {
                        player.displayClientMessage(
                                Component.translatable("eecore.scanner.too_many_blocks", MAX_VOLUME)
                                        .withStyle(ChatFormatting.RED), true);
                        return InteractionResult.FAIL;
                    }

                    blockSeen.putIfAbsent(block, true);

                    if (level.getBlockEntity(wPos) instanceof com.endlessepoch.core.nova.block.ScannerControllerBlockEntity scc) {
                        controllerPositions.add(wPos);
                        controllerCount++;
                        if (controllerCount == 1) {
                            controllerWorldPos = wPos;
                            facing = scc.getFacing();
                        }
                    }
                }
            }
        }

        if (blockSeen.size() > CHAR_POOL.length) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.too_many_types", blockSeen.size(), CHAR_POOL.length)
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        // Full scan — allocate array and encode pattern / 正式扫描 — 分配数组编码结构
        Map<Block, Character> blockToChar = new LinkedHashMap<>();
        Map<Character, BlockState> definitions = new LinkedHashMap<>();
        poolIdx = 0;
        definitions.put('A', Blocks.AIR.defaultBlockState());
        definitions.put('#', Blocks.AIR.defaultBlockState());

        char[][][] raw = new char[sizeY][sizeZ][sizeX];
        BlockPos controllerPos = null;
        controllerPositions.clear();

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos wPos = new BlockPos(minX + x, minY + y, minZ + z);
                    BlockState state = resolveFluidBlock(level, wPos);
                    Block block = state.getBlock();

                    if (block == Blocks.AIR
                            || block instanceof com.endlessepoch.core.nova.block.ScannerBoundaryBlock
                            || SKIP_BLOCKS.contains(block)) {
                        raw[y][z][x] = 'A';
                        continue;
                    }

                    if (level.getBlockEntity(wPos) instanceof com.endlessepoch.core.nova.block.ScannerControllerBlockEntity) {
                        controllerPositions.add(wPos);
                        if (controllerPos == null) {
                            controllerPos = new BlockPos(x, y, z);
                        }
                        definitions.put('K', state);
                        raw[y][z][x] = 'K';
                        continue;
                    }

                    Character c = blockToChar.get(block);
                    if (c == null) {
                        c = poolIdx < CHAR_POOL.length ? CHAR_POOL[poolIdx++] : 'X';
                        blockToChar.put(block, c);
                        definitions.put(c, state);
                    }
                    raw[y][z][x] = c;
                }
            }
        }

        int trimMinY = 0, trimMaxY = sizeY - 1;
        int trimMinZ = 0, trimMaxZ = sizeZ - 1;
        int trimMinX = 0, trimMaxX = sizeX - 1;

        while (trimMinY <= trimMaxY) {
            boolean allAir = true;
            for (int z = trimMinZ; z <= trimMaxZ && allAir; z++) {
                for (int x = trimMinX; x <= trimMaxX && allAir; x++) {
                    if (raw[trimMinY][z][x] != 'A') { allAir = false; }
                }
            }
            if (allAir) trimMinY++;
            else break;
        }
        while (trimMinY <= trimMaxY) {
            boolean allAir = true;
            for (int z = trimMinZ; z <= trimMaxZ && allAir; z++) {
                for (int x = trimMinX; x <= trimMaxX && allAir; x++) {
                    if (raw[trimMaxY][z][x] != 'A') { allAir = false; }
                }
            }
            if (allAir) trimMaxY--;
            else break;
        }
        while (trimMinZ <= trimMaxZ) {
            boolean allAir = true;
            for (int y = trimMinY; y <= trimMaxY && allAir; y++) {
                for (int x = trimMinX; x <= trimMaxX && allAir; x++) {
                    if (raw[y][trimMinZ][x] != 'A') { allAir = false; }
                }
            }
            if (allAir) trimMinZ++;
            else break;
        }
        while (trimMinZ <= trimMaxZ) {
            boolean allAir = true;
            for (int y = trimMinY; y <= trimMaxY && allAir; y++) {
                for (int x = trimMinX; x <= trimMaxX && allAir; x++) {
                    if (raw[y][trimMaxZ][x] != 'A') { allAir = false; }
                }
            }
            if (allAir) trimMaxZ--;
            else break;
        }
        while (trimMinX <= trimMaxX) {
            boolean allAir = true;
            for (int y = trimMinY; y <= trimMaxY && allAir; y++) {
                for (int z = trimMinZ; z <= trimMaxZ && allAir; z++) {
                    if (raw[y][z][trimMinX] != 'A') { allAir = false; }
                }
            }
            if (allAir) trimMinX++;
            else break;
        }
        while (trimMinX <= trimMaxX) {
            boolean allAir = true;
            for (int y = trimMinY; y <= trimMaxY && allAir; y++) {
                for (int z = trimMinZ; z <= trimMaxZ && allAir; z++) {
                    if (raw[y][z][trimMaxX] != 'A') { allAir = false; }
                }
            }
            if (allAir) trimMaxX--;
            else break;
        }

        int newSizeX = trimMaxX - trimMinX + 1;
        int newSizeY = trimMaxY - trimMinY + 1;
        int newSizeZ = trimMaxZ - trimMinZ + 1;
        if (newSizeX < sizeX || newSizeY < sizeY || newSizeZ < sizeZ) {
            char[][][] trimmed = new char[newSizeY][newSizeZ][newSizeX];
            for (int y = 0; y < newSizeY; y++) {
                for (int z = 0; z < newSizeZ; z++) {
                    System.arraycopy(raw[trimMinY + y][trimMinZ + z], trimMinX, trimmed[y][z], 0, newSizeX);
                }
            }
            minX += trimMinX;
            minY += trimMinY;
            minZ += trimMinZ;
            sizeX = newSizeX;
            sizeY = newSizeY;
            sizeZ = newSizeZ;
            raw = trimmed;
            if (controllerPos != null) {
                controllerPos = new BlockPos(
                        controllerPos.getX() - trimMinX,
                        controllerPos.getY() - trimMinY,
                        controllerPos.getZ() - trimMinZ
                );
            }
        }

        if (blockToChar.size() > CHAR_POOL.length) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.too_many_types", blockToChar.size(), CHAR_POOL.length)
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (controllerPos == null) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.no_controller").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        if (controllerCount > 1) {
            int[] posArr = new int[controllerPositions.size() * 3];
            for (int i = 0; i < controllerPositions.size(); i++) {
                posArr[i * 3] = controllerPositions.get(i).getX();
                posArr[i * 3 + 1] = controllerPositions.get(i).getY();
                posArr[i * 3 + 2] = controllerPositions.get(i).getZ();
            }
            CompoundTag tagForHighlight = getTag(stack);
            tagForHighlight.putIntArray("controllers", posArr);
            tagForHighlight.putBoolean("scan_failed", true);
            saveTag(stack, tagForHighlight);
            player.getInventory().setChanged();

            player.displayClientMessage(
                    Component.translatable("eecore.scanner.multiple_controllers_highlight",
                            controllerCount).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        net.minecraft.core.Direction controllerFront = facing;
        int rotW = sizeX, rotD = sizeZ;
        int newCx = controllerPos.getX(), newCz = controllerPos.getZ();
        String[][] layers;
        if (controllerFront != net.minecraft.core.Direction.NORTH) {
            java.util.List<int[]> rotData = new java.util.ArrayList<>();
            int rMinX = 0, rMaxX = 0, rMinZ = 0, rMaxZ = 0;
            for (int y = 0; y < sizeY; y++)
                for (int z = 0; z < sizeZ; z++)
                    for (int x = 0; x < sizeX; x++) {
                        char c = raw[y][z][x];
                        if (c == 'A') { rotData.add(null); continue; }
                        int[] r = rotateXZ(x - newCx, z - newCz, controllerFront);
                        rotData.add(new int[]{r[0], r[1], y, z, x});
                        rMinX = Math.min(rMinX, r[0]); rMaxX = Math.max(rMaxX, r[0]);
                        rMinZ = Math.min(rMinZ, r[1]); rMaxZ = Math.max(rMaxZ, r[1]);
                    }
            rotW = Math.max(1, rMaxX - rMinX + 1);
            rotD = Math.max(1, rMaxZ - rMinZ + 1);
            newCx = 0 - rMinX;
            newCz = 0 - rMinZ;
            layers = new String[sizeY][rotD];
            for (int y = 0; y < sizeY; y++)
                for (int z = 0; z < rotD; z++) {
                    char[] row = new char[rotW];
                    java.util.Arrays.fill(row, 'A');
                    layers[y][z] = new String(row);
                }
            for (int[] rd : rotData) {
                if (rd == null) continue;
                int nx = rd[0] - rMinX, nz = rd[1] - rMinZ;
                int ly = rd[2], lz = nz, lx = nx;
                char[] row = layers[ly][lz].toCharArray();
                row[lx] = raw[rd[2]][rd[3]][rd[4]];
                layers[ly][lz] = new String(row);
            }
        } else {
            layers = new String[sizeY][sizeZ];
            for (int y = 0; y < sizeY; y++)
                for (int z = 0; z < sizeZ; z++)
                    layers[y][z] = new String(raw[y][z]);
        }

        // Position collection — cache non-air positions for fast preview / 位置收集 — 缓存非空气坐标供预览快速遍历
        java.util.List<BlockPos> nonAirPositions = new java.util.ArrayList<>(nonAirCount);
        java.util.List<BlockPos> controllers = new java.util.ArrayList<>();
        for (int y = 0; y < sizeY; y++)
            for (int z = 0; z < rotD; z++)
                for (int x = 0; x < rotW; x++)
                    if (layers[y][z].charAt(x) != 'A') {
                        var bp = new BlockPos(x, y, z);
                        nonAirPositions.add(bp);
                        if (layers[y][z].charAt(x) == 'K') controllers.add(bp);
                    }

        MultiBlockPattern pattern = new MultiBlockPattern(
                rotW, sizeY, rotD,
                newCx, controllerPos.getY(), newCz,
                layers, definitions,
                nonAirPositions, controllers);

        String name = "scanned_" + System.currentTimeMillis() % 100000;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("eecore", name);
        MultiBlockRegistry.registerLocal(player.getUUID(), id, pattern);

        PacketDistributor.sendToPlayer(player, SyncPatternBinaryPacket.fromPattern(id, pattern));

        clearSelections(stack, player);
        player.displayClientMessage(
                Component.translatable("eecore.scanner.done", name,
                        sizeX, sizeY, sizeZ, definitions.size()).withStyle(ChatFormatting.GREEN), true);

        return InteractionResult.SUCCESS;
    }

    private static int[] rotateXZ(int dx, int dz, net.minecraft.core.Direction facing) {
        return switch (facing) {
            case SOUTH -> new int[]{-dx, -dz};
            case EAST  -> new int[]{dz, -dx};
            case WEST  -> new int[]{-dz, dx};
            default    -> new int[]{dx, dz};
        };
    }

    private void clearSelections(ItemStack stack, Player player) {
        CompoundTag tag = getTag(stack);
        boolean had = tag.contains("pos1") || tag.contains("pos2") || tag.contains("controllers");
        tag.remove("pos1");
        tag.remove("pos2");
        tag.remove("controllers");
        tag.remove("scan_failed");
        saveTag(stack, tag);
        if (had && !player.level().isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.cleared").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Flowing fluid is transient — treat as air. Only fluid source blocks count as structure.
     * 流动液体不计入结构，仅流体源算作方块。
     */
    private static BlockState resolveFluidBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.isEmpty() && !fluid.isSource()) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    private CompoundTag getTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null ? cd.copyTag() : new CompoundTag();
    }

    private void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private BlockPos getPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        int[] arr = tag.getIntArray(key);
        if (arr.length != 3) return null;
        return new BlockPos(arr[0], arr[1], arr[2]);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        String titleKey = this.titleKey != null ? this.titleKey : getDescriptionId();
        String title = Component.translatable(titleKey).getString();
        tooltip.add(animation.titleRenderer().apply(title));
        tooltip.add(Component.empty());

        for (String key : animation.extraLines()) {
            if (key != null && !key.isEmpty())
                tooltip.add(Component.translatable(key));
        }

        CompoundTag tag = getTag(stack);
        BlockPos p1 = getPos(tag, "pos1");
        BlockPos p2 = getPos(tag, "pos2");
        if (p1 != null) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("eecore.scanner.pos1",
                    p1.getX(), p1.getY(), p1.getZ()).withStyle(ChatFormatting.AQUA));
        }
        if (p2 != null) {
            tooltip.add(Component.translatable("eecore.scanner.pos2",
                    p2.getX(), p2.getY(), p2.getZ()).withStyle(ChatFormatting.GOLD));
        }

        // Author (last line, blank before) / 最后一行显示作者，前面留空行
        tooltip.add(Component.empty());
        tooltip.add(animation.authorRenderer().apply("eecore.item.author"));
    }

    public static BlockPos getPos1(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        return getPosStatic(cd.copyTag(), "pos1");
    }

    public static BlockPos getPos2(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        return getPosStatic(cd.copyTag(), "pos2");
    }

    public static List<BlockPos> getControllerPositions(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return List.of();
        CompoundTag tag = cd.copyTag();
        if (!tag.contains("controllers", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) return List.of();
        int[] arr = tag.getIntArray("controllers");
        if (arr.length == 0 || arr.length % 3 != 0) return List.of();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < arr.length; i += 3) {
            positions.add(new BlockPos(arr[i], arr[i + 1], arr[i + 2]));
        }
        return positions;
    }

    private static BlockPos getPosStatic(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        int[] a = tag.getIntArray(key);
        if (a.length != 3) return null;
        return new BlockPos(a[0], a[1], a[2]);
    }
}
