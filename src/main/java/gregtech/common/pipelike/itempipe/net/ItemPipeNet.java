package gregtech.common.pipelike.itempipe.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.FacingPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

public class ItemPipeNet extends PipeNet<ItemPipeProperties> {

    private final Map<BlockPos, List<Inventory>> NET_DATA = new HashMap<>();

    private final EnumMap<EnumFacing, Boolean> neighbors = new EnumMap<>(EnumFacing.class);

    public ItemPipeNet(WorldPipeNet<ItemPipeProperties, ? extends PipeNet<ItemPipeProperties>> world) {
        super(world);
    }

    public List<Inventory> getNetData(BlockPos pipePos, EnumFacing facing) {
        List<Inventory> data = NET_DATA.get(pipePos);
        if (data == null || data.isEmpty()) {
            data = ItemNetWalker.createNetData(getWorldData(), pipePos, facing);
            data.sort(Comparator.comparingInt(inv -> inv.properties.getPriority()));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    @Override
    public void onNeighbourUpdate(World w, BlockPos pos, BlockPos neighbor) {
        EnumFacing facing = null;
        if (pos.getX() != neighbor.getX()) {
            if (pos.getX() < neighbor.getX()) {
                facing = EnumFacing.WEST;
            } else {
                facing = EnumFacing.EAST;
            }
        } else if (pos.getY() != neighbor.getY()) {
            if (pos.getY() < neighbor.getY()) {
                facing = EnumFacing.DOWN;
            } else {
                facing = EnumFacing.UP;
            }
        } else if (pos.getZ() != neighbor.getZ()) {
            if (pos.getZ() < neighbor.getZ()) {
                facing = EnumFacing.NORTH;
            } else {
                facing = EnumFacing.SOUTH;
            }
        }
        List<Inventory> data = new ArrayList<>(getNetData(pos, facing));
        for (Inventory inventory : data) {
            if (pos.offset(facing.getOpposite()).equals(neighbor)) {
                inventory.cached = null;
                boolean hasNeighbor = w.getTileEntity(neighbor) == null;
                neighbors.putIfAbsent(facing, !hasNeighbor);
                if (neighbors.get(facing) != hasNeighbor) {
                    if (!hasNeighbor) {
                        NET_DATA.get(pos).remove(inventory);
                    } else {
                        NET_DATA.get(pos).add(inventory);
                    }
                    neighbors.put(facing, hasNeighbor);
                }
            }
        }
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<ItemPipeProperties>> transferredNodes, PipeNet<ItemPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((ItemPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }


    @Override
    protected void writeNodeData(ItemPipeProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("Resistance", nodeData.getPriority());
        tagCompound.setFloat("Rate", nodeData.getTransferRate());
    }

    @Override
    protected ItemPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return new ItemPipeProperties(tagCompound.getInteger("Range"), tagCompound.getFloat("Rate"));
    }

    public static class Inventory {
        private final BlockPos pipePos;
        private final EnumFacing faceToHandler;
        private final int distance;
        private final ItemPipeProperties properties;
        private IItemHandler cached;

        public Inventory(BlockPos pipePos, EnumFacing facing, int distance, ItemPipeProperties properties) {
            this.pipePos = pipePos;
            this.faceToHandler = facing;
            this.distance = distance;
            this.properties = properties;
        }

        public BlockPos getPipePos() {
            return pipePos;
        }

        public EnumFacing getFaceToHandler() {
            return faceToHandler;
        }

        public int getDistance() {
            return distance;
        }

        public ItemPipeProperties getProperties() {
            return properties;
        }

        public BlockPos getHandlerPos() {
            return pipePos.offset(faceToHandler);
        }

        public IItemHandler getHandler(World world) {
            if (cached != null) return cached;
            TileEntity tile = world.getTileEntity(getHandlerPos());
            if (tile != null) {
                cached = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, faceToHandler.getOpposite());
                return cached;
            }
            return null;
        }

        public FacingPos toFacingPos() {
            return new FacingPos(pipePos, faceToHandler);
        }
    }
}
