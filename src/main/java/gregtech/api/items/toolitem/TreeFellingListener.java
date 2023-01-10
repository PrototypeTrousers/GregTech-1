package gregtech.api.items.toolitem;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
import java.util.*;
import java.util.stream.Collectors;

import static gregtech.api.items.toolitem.ToolHelper.RELOCATE_MINED_BLOCKS_KEY;

public final class TreeFellingListener {

    private final EntityPlayerMP player;
    private final ItemStack tool;
    private final Deque<BlockPos> orderedBlocks;
    private Deque<BlockPos> leafBlocks;

    private TreeFellingListener(EntityPlayerMP player, ItemStack tool, Deque<BlockPos> orderedBlocks) {
        this.player = player;
        this.tool = tool;
        this.orderedBlocks = orderedBlocks;
    }

    public static void start(@Nonnull IBlockState state, ItemStack tool, BlockPos start, @Nonnull EntityPlayerMP player) {
        World world = player.world;
        Block block = state.getBlock();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        Queue<BlockPos> checking = new ArrayDeque<>();
        Set<BlockPos> visited = new ObjectOpenHashSet<>();
        checking.add(start);

        while (!checking.isEmpty()) {
            BlockPos check = checking.remove();
            if (check != start) {
                visited.add(check);
            }
            for (int y = 0; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            mutablePos.setPos(check.getX() + x, check.getY() + y, check.getZ() + z);
                            if (!checking.contains(mutablePos)) {
                                if (!visited.contains(mutablePos)) {
                                    // Check that the found block matches the original block state, which is wood.
                                    if (block == world.getBlockState(mutablePos).getBlock()) {
                                        BlockPos immutablePos = mutablePos.toImmutable();
                                        checking.add(immutablePos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!visited.isEmpty()) {
            Deque<BlockPos> orderedBlocks = visited.stream()
                    .sorted(Comparator.comparingInt(pos -> start.getY() - pos.getY()))
                    .collect(Collectors.toCollection(ArrayDeque::new));
            MinecraftForge.EVENT_BUS.register(new TreeFellingListener(player, tool, orderedBlocks));
        }
    }

    @SubscribeEvent
    public void onWorldTick(@Nonnull TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.world == player.world && event.side == Side.SERVER) {
            if (leafBlocks == null) {
                gatherLeafs(event.world);
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
                return;
            }

            if (tool.isEmpty()) {
                MinecraftForge.EVENT_BUS.unregister(this);
                return;
            }
            ToolHelper.breakBlockRoutine(player, tool, orderedBlocks.removeLast());
        }
    }

    private void gatherLeafs(World world) {
        //Gather the leaves from an area determined by the amount of faces a log block not touching another log block.
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos maxXYZ = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos minXYZ = new BlockPos.MutableBlockPos();
        Set<BlockPos> tempLB = new ObjectOpenHashSet<>();
        leafBlocks = new ArrayDeque<>();

        int faces = 0;

        for (BlockPos b : orderedBlocks) {
            //Loop of the faces of the log and look for leaves or air to determine the area that might contain leaves
            //to be broken.
            for (EnumFacing e : EnumFacing.VALUES) {
                mutablePos.setPos(b);
                mutablePos.move(e);
                IBlockState bs = world.getBlockState(mutablePos);
                if (bs.getMaterial() == Material.LEAVES || bs.getBlock() == Blocks.AIR) {
                    if (bs.getMaterial() == Material.LEAVES) {
                        //add this neighbor to save 1 getBlockState call later
                        tempLB.add(mutablePos.toImmutable());
                    }
                    //Add the face to be checked later
                    faces = faces | 1 << e.getIndex();
                }
            }
            if (faces > 0) {
                minXYZ.setPos(b);
                maxXYZ.setPos(b);
                for (EnumFacing f : EnumFacing.VALUES) {
                    //if the face was added grow in that direction
                    if ((faces & 1 << f.getIndex()) > 0) {
                        if (f.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE) {
                            maxXYZ.move(f, 3);
                        } else {
                            minXYZ.move(f, 3);
                        }
                    }
                }
                //checks the blocks in the volume determined before for leaf blocks
                for (int y = minXYZ.getY(); y <= maxXYZ.getY(); ++y) {
                    for (int x = minXYZ.getX(); x <= maxXYZ.getX(); ++x) {
                        for (int z = minXYZ.getZ(); z <= maxXYZ.getZ(); ++z) {
                            if (x != 0 || y != 0 || z != 0) {
                                mutablePos.setPos(x, y, z);
                                if (!tempLB.contains(mutablePos)) {
                                    IBlockState bs = world.getBlockState(mutablePos);
                                    if (bs.getMaterial() == Material.LEAVES) {
                                        tempLB.add(mutablePos.toImmutable());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            faces = 0;
        }
        leafBlocks.addAll(tempLB);
    }
}
