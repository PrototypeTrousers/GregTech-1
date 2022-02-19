package gregtech.common.pipelike.cable.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.WireProperties;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class EnergyNet extends PipeNet<WireProperties> {

    private long lastEnergyFluxPerSec;
    private long energyFluxPerSec;
    private long lastTime;

    private final Map<BlockPos, List<RoutePath>> NET_DATA = new HashMap<>();

    private final EnumMap<EnumFacing, Boolean> neighbors = new EnumMap<>(EnumFacing.class);

    protected EnergyNet(WorldPipeNet<WireProperties, EnergyNet> world) {
        super(world);
    }

    public List<RoutePath> getNetData(BlockPos pipePos) {
        List<RoutePath> data = NET_DATA.get(pipePos);
        if (data == null || data.isEmpty()) {
            data = EnergyNetWalker.createNetData(getWorldData(), pipePos);
            data.sort(Comparator.comparingInt(RoutePath::getDistance));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    public void onNeighbourStateChanged(World w, BlockPos pos, BlockPos neighbor) {
        List<RoutePath> data = new ArrayList<>(getNetData(pos));
        for (RoutePath routePath : data) {
            EnumFacing facing = routePath.getFaceToHandler();
            if (pos.offset(facing.getOpposite()).equals(neighbor)) {
                routePath.cached = null;
                boolean hasNeighbor = w.getTileEntity(neighbor) == null;
                neighbors.putIfAbsent(facing, !hasNeighbor);
                if (neighbors.get(facing) != hasNeighbor) {
                    if (!hasNeighbor) {
                        NET_DATA.get(pos).remove(routePath);
                    } else {
                        NET_DATA.get(pos).add(routePath);
                    }
                    neighbors.put(facing, hasNeighbor);
                }
            }
        }
    }

    public long getEnergyFluxPerSec() {
        World world = getWorldData();
        if (world != null && !world.isRemote && (world.getTotalWorldTime() - lastTime) >= 20) {
            lastTime = world.getTotalWorldTime();
            clearCache();
        }
        return lastEnergyFluxPerSec;
    }

    public void addEnergyFluxPerSec(long energy) {
        energyFluxPerSec += energy;
    }

    public void clearCache() {
        lastEnergyFluxPerSec = energyFluxPerSec;
        energyFluxPerSec = 0;
    }

    @Override
    protected void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<WireProperties>> transferredNodes, PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((EnergyNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("voltage", nodeData.getVoltage());
        tagCompound.setInteger("amperage", nodeData.getAmperage());
        tagCompound.setInteger("loss", nodeData.getLossPerBlock());
    }

    @Override
    protected WireProperties readNodeData(NBTTagCompound tagCompound) {
        int voltage = tagCompound.getInteger("voltage");
        int amperage = tagCompound.getInteger("amperage");
        int lossPerBlock = tagCompound.getInteger("loss");
        return new WireProperties(voltage, amperage, lossPerBlock);
    }
}
