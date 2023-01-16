package gregtech.api.items.toolitem;

import gregtech.api.util.GTLog;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static gregtech.api.items.toolitem.ToolHelper.RELOCATE_MINED_BLOCKS_KEY;

public final class TreeFellingListener {

    private final EntityPlayerMP player;
    private final ItemStack tool;

    private ArrayDeque<BlockPos> futureLayer;
    private ArrayDeque<BlockPos> leafToBreak;

    private Deque<BlockPos> orderedBlocks;
    private final ArrayDeque<BlockPos> leafBlocks;
    private final Block logBlock;
    private final BlockPos startPos;
    private int pass = 0;

    private TreeFellingListener(EntityPlayerMP player, ItemStack tool, IBlockState state, BlockPos start) {
        this.player = player;
        this.tool = tool;
        this.logBlock = state.getBlock();
        this.startPos = start;
        this.orderedBlocks = new ArrayDeque<>();
        this.futureLayer = new ArrayDeque<>();
        this.leafBlocks = new ArrayDeque<>();
        this.leafToBreak = new ArrayDeque<>();
    }

    public static void start(@Nonnull IBlockState state, ItemStack tool, BlockPos start, @Nonnull EntityPlayerMP player) {
        MinecraftForge.EVENT_BUS.register(new TreeFellingListener(player, tool, state, start));
    }

    @SubscribeEvent
    public void onWorldTick(@Nonnull TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.world == player.world && event.side == Side.SERVER) {
            if (orderedBlocks.isEmpty()) {
                gatherLogLayer(event.world);
                gatherLeafs(event.world);
                pass++;
            }

            if (orderedBlocks.isEmpty()) {
                gatherLeafs2(event.world);
                while (!leafToBreak.isEmpty()) {
                    BlockPos check = leafToBreak.pop();
                    IBlockState state = event.world.getBlockState(check);

                    if (state == Blocks.AIR.getDefaultState()) continue;

                    if (ToolHelper.getBehaviorsTag(tool).getBoolean(RELOCATE_MINED_BLOCKS_KEY)) {
                        List<ItemStack> drops = state.getBlock().getDrops(player.world, check, state, ToolHelper.getFortuneOrLootingLevel(tool));
                        for (ItemStack dropStack : drops) {
                            if (!player.addItemStackToInventory(dropStack)) {
                                player.dropItem(dropStack, false, false);
                            }
                        }
                    } else {
                        state.getBlock().dropBlockAsItem(player.world, check, state, 0);
                    }
                    player.world.setBlockToAir(check);
                }
                MinecraftForge.EVENT_BUS.unregister(this);
                GTLog.logger.info("Total gathering ticks: " + pass);
                return;
            }

            if (tool.isEmpty()) {
                MinecraftForge.EVENT_BUS.unregister(this);
                return;
            }
            int breaks = 10;
            while (!orderedBlocks.isEmpty() && breaks > 0) {
                ToolHelper.breakBlockRoutine(player, tool, orderedBlocks.removeLast());
                breaks--;
            }
        }
    }

    private void gatherLogLayer(World world) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        if (pass == 0) {
            futureLayer.add(startPos);
        }

        ArrayDeque<BlockPos> currentLayer = futureLayer;
        this.futureLayer = new ArrayDeque<>();

        ArrayDeque<BlockPos> visited = new ArrayDeque<>();
        ArrayDeque<BlockPos> toBreak = new ArrayDeque<>();

        while (!currentLayer.isEmpty()) {
            BlockPos check = currentLayer.remove();
            toBreak.add(check);

            for (int y = 0; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            mutablePos.setPos(check.getX() + x, check.getY() + y, check.getZ() + z);
                            if (!visited.contains(mutablePos)) {
                                BlockPos immutablePos = mutablePos.toImmutable();
                                if (!currentLayer.contains(mutablePos)) {
                                    // Check that the found block matches the original block state, which is wood.
                                    if (logBlock == world.getBlockState(mutablePos).getBlock()) {
                                        if (y == 0) {
                                            currentLayer.add(immutablePos);
                                        } else {
                                            futureLayer.add(immutablePos);
                                        }
                                    }
                                }
                                visited.add(immutablePos);
                            }
                        }
                    }
                }
            }
        }
        this.orderedBlocks = toBreak;
    }

    private void gatherLeafs(World world) {
        //Gather the leaves from an area determined by the amount of faces a log block not touching another log block.
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        ArrayDeque<BlockPos> visited = new ArrayDeque<>();

        for (BlockPos b : orderedBlocks) {
            //Loop of the faces of the log and look for leaves or air to determine the area that might contain leaves
            //to be broken.
            for (EnumFacing e : EnumFacing.VALUES) {
                mutablePos.setPos(b);
                mutablePos.move(e);
                if (!visited.contains(mutablePos)) {
                    BlockPos immutable = mutablePos.toImmutable();
                    if (!leafBlocks.contains(mutablePos)) {
                        IBlockState bs = world.getBlockState(mutablePos);
                        if (bs.getMaterial() == Material.LEAVES) {
                            leafBlocks.add(immutable);
                        }
                    }
                    visited.add(immutable);
                }
            }
        }
    }

    private void gatherLeafs2(World world) {
        if (!leafBlocks.isEmpty()) {
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            ArrayDeque<BlockPos> toGatherFrom;
            ArrayDeque<BlockPos> next = new ArrayDeque<>();

            while (!leafBlocks.isEmpty()) {
                ArrayDeque<BlockPos> visited = new ArrayDeque<>();

                int stepsLeft = 5;
                boolean stfn = false;
                boolean stem;
                toGatherFrom = new ArrayDeque<>();
                toGatherFrom.add(leafBlocks.pop());

                while (!toGatherFrom.isEmpty() && stepsLeft > 0) {
                    stem = stepsLeft == 5;

                    BlockPos p = toGatherFrom.pop();
                    leafToBreak.add(p);
                    for (EnumFacing e : EnumFacing.VALUES) {
                        if (e == EnumFacing.UP && stfn) {
                            if (!leafBlocks.contains(mutablePos)) {
                                stfn = false;
                            } else {
                                continue;
                            }
                        }
                        mutablePos.setPos(p);
                        mutablePos.move(e);
                        if (!visited.contains(mutablePos)) {
                            if (e == EnumFacing.UP) {
                                if (stem) {
                                    if (leafBlocks.contains(mutablePos)) {
                                        stfn = true;
                                    }
                                }
                            }
                            BlockPos immutable = mutablePos.toImmutable();

                            if (!toGatherFrom.contains(mutablePos)) {
                                IBlockState bs = world.getBlockState(mutablePos);
                                if (bs.getMaterial() == Material.LEAVES) {
                                    next.add(immutable);
                                }
                            }
                            visited.add(immutable);
                        }
                    }

                    if (toGatherFrom.isEmpty()) {
                        toGatherFrom = next;
                        stepsLeft--;
                        next = new ArrayDeque<>();

                    }
                }
            }
        }
    }
}
