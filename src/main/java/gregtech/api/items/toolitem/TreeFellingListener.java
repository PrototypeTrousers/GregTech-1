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
    private Deque<BlockPos> orderedBlocks;
    private final Deque<BlockPos> leafBlocks;
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
    }

    public static void start(@Nonnull IBlockState state, ItemStack tool, BlockPos start, @Nonnull EntityPlayerMP player) {
        MinecraftForge.EVENT_BUS.register(new TreeFellingListener(player, tool, state, start));
    }

    @SubscribeEvent
    public void onWorldTick(@Nonnull TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.world == player.world && event.side == Side.SERVER) {
            if (orderedBlocks.isEmpty()) {
                gatherLogLayer();
                gatherLeafs(event.world);
                pass++;
            }

            if (orderedBlocks.isEmpty()) {
                while (!leafBlocks.isEmpty()) {
                    BlockPos check = leafBlocks.pop();
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
            ToolHelper.breakBlockRoutine(player, tool, orderedBlocks.removeLast());
        }
    }

    private void gatherLogLayer() {
        World world = player.world;
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
        BlockPos.MutableBlockPos maxXYZ = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos minXYZ = new BlockPos.MutableBlockPos();
        ArrayDeque<BlockPos> toBreak = new ArrayDeque<>();
        ArrayDeque<BlockPos> visited = new ArrayDeque<>();

        int faces = 0;

        for (BlockPos b : orderedBlocks) {
            //Loop of the faces of the log and look for leaves or air to determine the area that might contain leaves
            //to be broken.
            for (EnumFacing e : EnumFacing.VALUES) {
                mutablePos.setPos(b);
                mutablePos.move(e);
                if (pass == 1) {
                    if (mutablePos.equals(startPos)) {
                        continue;
                    }
                }
                IBlockState bs = world.getBlockState(mutablePos);
                if (bs.getMaterial() == Material.LEAVES || bs.getBlock() == Blocks.AIR) {
                    //Add the face to be checked later
                    faces = faces | 1 << e.getIndex();
                }
            }
            if (faces > 0) {
                for (int distance = 1; distance < 4; distance++) {
                    minXYZ.setPos(b);
                    maxXYZ.setPos(b);
                    boolean anyLeafs = false;
                    for (EnumFacing facing : EnumFacing.VALUES) {
                        //if the face was added grow in that direction
                        if ((faces & 1 << facing.getIndex()) > 0) {
                            if (facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) {
                                maxXYZ.move(facing, distance);
                            } else {
                                minXYZ.move(facing, distance);
                            }
                        }
                    }
                    //checks the blocks in the volume determined before for leaf blocks
                    for (int y = minXYZ.getY(); y <= maxXYZ.getY(); y++) {
                        for (int x = minXYZ.getX(); x <= maxXYZ.getX(); x++) {
                            for (int z = minXYZ.getZ(); z <= maxXYZ.getZ(); z++) {
                                if (x != 0 || y != 0 || z != 0) {
                                    mutablePos.setPos(x, y, z);
                                    if (!visited.contains(mutablePos)) {
                                        BlockPos immutable = mutablePos.toImmutable();
                                        if (!toBreak.contains(mutablePos)) {
                                            IBlockState bs = world.getBlockState(mutablePos);
                                            if (bs.getMaterial() == Material.LEAVES) {
                                                toBreak.add(immutable);
                                                anyLeafs = true;
                                            }
                                        }
                                        visited.add(immutable);
                                    }
                                }
                            }
                        }
                    }
                    if (!anyLeafs) {
                        break;
                    }
                }
            }
            faces = 0;
        }
        leafBlocks.addAll(toBreak);
    }
}
