package com.github.kozakura913.fedstorage.tile;

import com.github.kozakura913.fedstorage.EnderStorage;
import com.github.kozakura913.fedstorage.api.Frequency;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.network.EnderStorageSPH;
import com.github.kozakura913.fedstorage.network.TankSynchroniser;
import com.github.kozakura913.fedstorage.storage.EnderLiquidStorage;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.fluid.FluidUtils;
import codechicken.lib.math.MathHelper;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.vec.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.github.kozakura913.fedstorage.handler.ConfigurationHandler.*;
import static codechicken.lib.vec.Vector3.center;

public class TileEnderTank extends TileFrequencyOwner {

    public class EnderTankState extends TankSynchroniser.TankState {

        @Override
        public void sendSyncPacket() {
            PacketCustom packet = new PacketCustom(EnderStorageSPH.channel, 5);
            packet.writePos(getPos());
            packet.writeFluidStack(s_liquid);
            packet.sendToChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
        }

        @Override
        public void onLiquidChanged() {
            world.checkLight(pos);
        }
    }

    public class PressureState {

        public boolean invert_redstone;
        public boolean a_pressure;
        public boolean b_pressure;

        public double a_rotate;
        public double b_rotate;

        public void update(boolean client) {
            if (client) {
                b_rotate = a_rotate;
                a_rotate = MathHelper.approachExp(a_rotate, approachRotate(), 0.5, 20);
            } else {
                b_pressure = a_pressure;
                //a_pressure = world.isBlockPowered(getPos()) != invert_redstone;
                if (a_pressure != b_pressure) {
                    sendSyncPacket();
                    return;
                }
                if(world.getWorldTime() % 20 == 0) {
                    sendSyncPacket();
                }
            }
        }

        public double approachRotate() {
            return a_pressure ? -90 : 90;
        }

        private void sendSyncPacket() {
            PacketCustom packet = new PacketCustom(EnderStorageSPH.channel, 6);
            packet.writePos(getPos());
            packet.writeBoolean(a_pressure);
            packet.sendToChunk(world, pos.getX() >> 4, pos.getZ() >> 4);
        }

        public void invert() {
        	a_pressure=!a_pressure;
        	//invert_redstone = !invert_redstone;
            world.getChunkFromChunkCoords(pos.getX(), pos.getZ()).markDirty();
        }
    }

    public class TankFluidCap implements IFluidHandler {

        @Override
        public IFluidTankProperties[] getTankProperties() {
            if (world.isRemote) {
                return new IFluidTankProperties[] { new FluidTankProperties(liquid_state.s_liquid, EnderLiquidStorage.CAPACITY) };
            }
            return getStorage().getTankProperties();
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return getStorage().fill(resource, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return getStorage().drain(resource, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return getStorage().drain(maxDrain, doDrain);
        }
    }

    private static Cuboid6[] selectionBoxes = new Cuboid6[4];
    public static Transformation[] buttonT = new Transformation[3];

    static {
        for (int i = 0; i < 3; i++) {
            buttonT[i] = new Scale(0.6).with(new Translation(0.35 + (2 - i) * 0.15, 0.91, 0.5));
            selectionBoxes[i] = selection_button.copy().apply(buttonT[i]);
        }
        selectionBoxes[3] = new Cuboid6(0.358, 0.268, 0.05, 0.662, 0.565, 0.15);
    }

    public int rotation;
    public EnderTankState liquid_state = new EnderTankState();
    public PressureState pressure_state = new PressureState();
    public TankFluidCap fluidCap = new TankFluidCap();


	private boolean described;

	@Override
	public void update() {
		super.update();

		pressure_state.update(world.isRemote);
		EnderLiquidStorage storage = getStorage();
		if(!world.isRemote) {
			if(storage.isPull) {
				synchronized(storage) {
					FluidStack send_buffer = storage.send_buffer;
					if(send_buffer!=null) {
						int count=storage.fill(send_buffer,true);
						send_buffer.amount-=count;
						if(send_buffer.amount<1) {
							storage.send_buffer=null;
						}
					}
					FluidStack recv_buffer = storage.recv_buffer;
					if(recv_buffer!=null) {
						int count=storage.fill(recv_buffer,true);
						recv_buffer.amount-=count;
						if(recv_buffer.amount<1) {
							storage.recv_buffer=null;
						}
					}
				}
			}else {
				synchronized(storage) {
					FluidStack recv_buffer = storage.recv_buffer;
					if(recv_buffer!=null) {
						int count=storage.fill(recv_buffer,true);
						recv_buffer.amount-=count;
						if(recv_buffer.amount<1) {
							storage.recv_buffer=null;
						}
					}
					if(storage.send_buffer==null) {
						FluidStack fs = storage.drain(Integer.MAX_VALUE,true);
						if(fs!=null) {
							storage.send_buffer=fs;
						}
					}else {
						FluidStack fs = storage.drain(storage.send_buffer,true);
						if(fs!=null) {
							storage.send_buffer.amount+=fs.amount;
						}
					}
				}
			}
		}
		if (pressure_state.a_pressure) {
			ejectLiquid();
		}
		liquid_state.setFrequency(frequency);
		liquid_state.update(world.isRemote, world.getWorldTime() % 20 == 0);
	}

    private void ejectLiquid() {
        for (EnumFacing side : EnumFacing.values()) {
            TileEntity te = world.getTileEntity(getPos().offset(side));
            if(te == null) continue;
            if(te instanceof TileEnderTank) {
                TileEnderTank sideTe = (TileEnderTank) te;
                if(sideTe.getStorage().freq.equals(frequency)) {
                    continue;
                }
            }
            IFluidHandler c = FluidUtils.getFluidHandlerOrEmpty(world, getPos().offset(side), side.getOpposite());
            FluidStack liquid = getStorage().drain(tankOutputRate, false);
            if (liquid == null) {
                continue;
            }
            int qty = c.fill(liquid, true);
            if (qty > 0) {
                getStorage().drain(qty, true);
            }
        }
    }

    @Override
    public void setFreq(Frequency frequency) {
        super.setFreq(frequency);
        if (!world.isRemote) {
            liquid_state.setFrequency(frequency);
        }
    }

    @Override
    public EnderLiquidStorage getStorage() {
        return (EnderLiquidStorage) EnderStorageManager.instance(world.isRemote).getStorage(frequency, "liquid");
    }

    @Override
    public void onPlaced(EntityLivingBase entity) {
        rotation = (int) Math.floor(entity.rotationYaw * 4 / 360 + 2.5D) & 3;
        pressure_state.b_rotate = pressure_state.a_rotate = pressure_state.approachRotate();
        if(world.isRemote) {
            return;
        }
        setFreq(frequency.copy().setDimId(String.valueOf(entity.dimension)));
        liquid_state.setFrequency(frequency);
        liquid_state.forceUpdate();
        markDirty();
        if(entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            FluidUtil.interactWithFluidHandler(player, player.swingingHand, getStorage());
        }
        getStorage().manager.requestSave(getStorage());
        sendUpdatePacket();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("rot", (byte) rotation);
        tag.setBoolean("ir", pressure_state.invert_redstone);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        liquid_state.setFrequency(frequency);
        rotation = tag.getByte("rot") & 3;
        pressure_state.invert_redstone = tag.getBoolean("ir");
    }

    @Override
    public void writeToPacket(MCDataOutput packet) {
        super.writeToPacket(packet);
        packet.writeByte(rotation);
        packet.writeFluidStack(liquid_state.s_liquid);
        packet.writeBoolean(pressure_state.a_pressure);
    }

    @Override
    public void readFromPacket(MCDataInput packet) {
        super.readFromPacket(packet);
        liquid_state.setFrequency(frequency);
        rotation = packet.readUByte() & 3;
        liquid_state.s_liquid = packet.readFluidStack();
        pressure_state.a_pressure = packet.readBoolean();
        getStorage().isPull=pressure_state.a_pressure;
        if (!described) {
            liquid_state.c_liquid = liquid_state.s_liquid;
            pressure_state.b_rotate = pressure_state.a_rotate = pressure_state.approachRotate();
        }
        described = true;
        liquid_state.update(true, true);
    }

    @Override
    public boolean activate(EntityPlayer player, int subHit, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (subHit == 4) {
            pressure_state.invert();
            String subtype = "default";
            if (pressure_state.a_pressure) {
                subtype = "push";
            }
            getStorage().isPull=pressure_state.a_pressure;
            player.sendMessage(new TextComponentTranslation("enderstorage.tile.mode." + subtype));
            return true;
        }
        return false;
    }

    @Override
    public List<IndexedCuboid6> getIndexedCuboids() {
        ArrayList<IndexedCuboid6> cuboids = new ArrayList<>();

        cuboids.add(new IndexedCuboid6(0, new Cuboid6(0.15, 0, 0.15, 0.85, 0.916, 0.85)));

        for (int i = 0; i < 4; i++) {
            cuboids.add(new IndexedCuboid6(i + 1, selectionBoxes[i].copy().apply(Rotation.quarterRotations[rotation ^ 2].at(center))));
        }
        return cuboids;
    }

    @Override
    public int getLightValue() {
        if (liquid_state.s_liquid.amount > 0) {
            return FluidUtils.getLuminosity(liquid_state.c_liquid, liquid_state.s_liquid.amount / 16D);
        }

        return 0;
    }

    @Override
    public boolean redstoneInteraction() {
        return true;
    }

    public void sync(PacketCustom packet) {
        if (packet.getType() == 5) {
            liquid_state.sync(packet.readFluidStack());
        } else if (packet.getType() == 6) {
            pressure_state.a_pressure = packet.readBoolean();
        }
    }

    @Override
    public boolean rotate() {
        if (!world.isRemote) {
            rotation = (rotation + 1) % 4;
            PacketCustom.sendToChunk(getUpdatePacket(), world, pos.getX() >> 4, pos.getZ() >> 4);
        }

        return true;
    }

    @Override
    public int comparatorInput() {
        IFluidTankProperties tank = getStorage().getTankProperties()[0];
        FluidStack fluid = tank.getContents();
        if (fluid == null) {
            fluid = FluidUtils.emptyFluid();
        }
        int signal = fluid.amount * 14 / tank.getCapacity() + (fluid.amount > 0 ? 1 : 0);
        if(signal > 0) {
            return signal;
        }
        return signal;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidCap);
        }
        return super.getCapability(capability, facing);
    }
}
