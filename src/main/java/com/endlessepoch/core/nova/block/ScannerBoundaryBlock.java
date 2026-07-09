package com.endlessepoch.core.nova.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Scanner boundary block — angel block that can be placed in mid-air.
 * 扫描辅助块，可凭空放置的天使方块。
 */
public class ScannerBoundaryBlock extends Block {

    public ScannerBoundaryBlock(Properties properties) {
        super(properties);
    }

    public static class Item extends BlockItem {
        public Item(Block block, Properties p) { super(block, p); }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            InteractionResult r = super.useOn(ctx);
            if (r != InteractionResult.PASS) return r;
            BlockPos target = ctx.getClickedPos().relative(ctx.getClickedFace());
            if (ctx.getLevel().isEmptyBlock(target)) {
                return this.place(new BlockPlaceContext(ctx.getLevel(), ctx.getPlayer(),
                        ctx.getHand(), ctx.getItemInHand(),
                        new net.minecraft.world.phys.BlockHitResult(
                                ctx.getClickLocation(), ctx.getClickedFace(), target, false)));
            }
            return r;
        }

        @Override
        public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
                net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                net.minecraft.world.InteractionHand hand) {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            var hit = player.pick(5.0, 0.0f, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                return net.minecraft.world.InteractionResultHolder.pass(stack);
            }
            BlockPos pos = net.minecraft.core.BlockPos.containing(hit.getLocation());
            if (level.isEmptyBlock(pos)) {
                level.setBlock(pos, getBlock().defaultBlockState(), 3);
                if (!player.isCreative()) stack.shrink(1);
                return net.minecraft.world.InteractionResultHolder.success(stack);
            }
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this.asItem()));
    }
}
