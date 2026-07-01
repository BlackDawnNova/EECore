package com.endlessepoch.core.nova.item;

import com.endlessepoch.core.api.multiblock.MultiBlockPattern;
import com.endlessepoch.core.api.multiblock.MultiBlockRegistry;
import com.endlessepoch.core.network.SyncPatternPacket;
import com.endlessepoch.core.registry.Blocks;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

public class MultiblockScannerItem extends AnimatedItem {

    // Character pool for scanned blocks — A=air, K=controller, #=wildcard are reserved
    private static final char[] CHAR_POOL = {
        'B','C','D','E','F','G','H','I','J','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
        'b','c','d','e','f','g','h','i','j','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
        '0','1','2','3','4','5','6','7','8','9',
        '@','$','%','&','*','+','-','=','_','~','!','?','<','>','|',';',':',',','.','/',
        '一','二','三','四','五','六','七','八','九','十',
        '壹','贰','叁','肆','伍','陆','柒','捌','玖','拾',
        '★','☆','♂','♀','©','®','™','€','¥','£',
        'α','β','γ','δ','ε','ζ','η','θ','ι','κ','λ','μ','ν','ξ','π','ρ','σ','τ','υ','φ','χ','ψ','ω',
        'Α','Β','Γ','Δ','Ε','Ζ','Η','Θ','Ι','Κ','Λ','Μ','Ν','Ξ','Ο','Π','Ρ','Σ','Τ','Υ','Φ','Χ','Ψ','Ω',
        '←','→','↑','↓','↔','↕','↖','↗','↘','↙','⇐','⇒','⇑','⇓','⇔','⇕',
        '⊕','⊖','⊗','⊘','⊙','⊚','⊛','⊜','⊝','∞','∝','∠','∟','∣',
        '±','×','÷','≠','≤','≥','≡','≈','∼','∽',
        '∃','∀','∂','∅','∆','∇','∈','∉','∋','∌','∏','∑','∓','∔',
        '∫','∬','∭','∮','∯','∰','∱','∲','∳','■','□','▪','▫','▬','▭','▮','▯',
        '▓','▒','░','█','▄','▌','▐','▀','○','●','◘','◙','◦','☼','☺','☻',
        '♠','♣','♥','♦','♪','♫','◄','►','▲','▼',
        '㏑','㏒','㏓','㏔','㏕','㏖','㎎','㎏','㎜','㎝','㎞','㎟','㎡','㎢','㎣','㎤',
        '㏗','㏘','㏙','㏚','㏛','㏜','㏝','㏞','㏟','㎥','㎦','㎧','㎨','㎩','㎪','㎫',
        'ㄅ','ㄆ','ㄇ','ㄈ','ㄉ','ㄊ','ㄋ','ㄌ','ㄍ','ㄎ','ㄏ',
        'ㄐ','ㄑ','ㄒ','ㄓ','ㄔ','ㄕ','ㄖ','ㄗ','ㄘ','ㄙ'
    };
    private int poolIdx = 0;
    private static final long MAX_VOLUME = 1_000_000;

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

        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > MAX_VOLUME) {
            player.displayClientMessage(
                    Component.translatable("eecore.scanner.too_large", volume, MAX_VOLUME)
                            .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        java.util.List<BlockPos> controllerPositions = new java.util.ArrayList<>();
        Map<Block, Character> blockToChar = new LinkedHashMap<>();
        Map<Character, BlockState> definitions = new LinkedHashMap<>();
        poolIdx = 0;
        definitions.put('A', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        definitions.put('#', net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

        char[][][] raw = new char[sizeY][sizeZ][sizeX];
        BlockPos controllerPos = null;
        int controllerCount = 0;
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos wPos = new BlockPos(minX + x, minY + y, minZ + z);
                    BlockState state = level.getBlockState(wPos);
                    Block block = state.getBlock();

                    if (block == net.minecraft.world.level.block.Blocks.AIR) {
                        raw[y][z][x] = 'A';
                        continue;
                    }

                    // Controller detection — must check every / 必须遍历所有方块检查控制器
                    if (level.getBlockEntity(wPos) instanceof com.endlessepoch.core.nova.block.ScannerControllerBlockEntity) {
                        controllerPositions.add(wPos);
                        controllerCount++;
                        if (controllerCount == 1) {
                            controllerPos = new BlockPos(x, y, z);
                            var scc = (com.endlessepoch.core.nova.block.ScannerControllerBlockEntity)level.getBlockEntity(wPos);
                            facing = scc.getFacing();
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

        MultiBlockPattern pattern = new MultiBlockPattern(
                rotW, sizeY, rotD,
                newCx, controllerPos.getY(), newCz,
                layers, definitions);

        String name = "scanned_" + System.currentTimeMillis() % 100000;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("eecore", name);
        MultiBlockRegistry.registerLocal(player.getUUID(), id, pattern);

        PacketDistributor.sendToPlayer(player, SyncPatternPacket.fromPattern(id, pattern));

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
