package com.github.kozakura913.fedstorage.tile;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.misc.EnderDyeButton;
import com.github.kozakura913.fedstorage.misc.EnderKnobSlot;
import com.github.kozakura913.fedstorage.storage.EnderEnergyStorage;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.vec.Cuboid6;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class TileEnderEnergy extends TileFrequencyOwner {

	public int rotation;

	public static EnderDyeButton[] buttons;
	private int comparatorHint;

	static {
		buttons = new EnderDyeButton[3];
		for (int i = 0; i < 3; i++) {
			buttons[i] = new EnderDyeButton(i);
		}
	}

	public TileEnderEnergy() {
	}

	@Override
	public void update() {
		super.update();
		EnderEnergyStorage storage = getStorage();
		comparatorHint=storage.energy;
		if(!this.world.isRemote) {
			synchronized(storage) {
				if(storage.isPull) {
					int recv=(int) Math.min(storage.local_buffer, Integer.MAX_VALUE-storage.energy);
					storage.energy+=recv;
					storage.local_buffer-=recv;
				}else {
					int send=(int) Math.max(Integer.MAX_VALUE-storage.local_buffer,0);
					send=Math.min(storage.energy, send);
					storage.energy-=send;
					storage.local_buffer+=send;
				}
			}
		}
		if(!this.world.isRemote&&storage.isPull) {
			for (EnumFacing side: EnumFacing.VALUES) {
				TileEntity te = world.getTileEntity(getPos().offset(side));
				if(te == null) {
					continue;
				}
				IEnergyStorage energy = te.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
				if(energy == null) {
					continue;
				}
				if(!energy.canReceive()) {
					continue;
				}
				int max=storage.extractEnergy(Integer.MAX_VALUE,true);
				int used=energy.receiveEnergy(max,false);
				storage.extractEnergy(used, false);
			}
		}
	}

	@Override
	public boolean receiveClientEvent(int id, int type) {
		if (id == 1) {
			return true;
		}
		return false;
	}

	@Override
	public EnderEnergyStorage getStorage() {
		return (EnderEnergyStorage) EnderStorageManager.instance(world.isRemote).getStorage(frequency, "energy");
	}

	@Override
	public void writeToPacket(MCDataOutput packet) {
		super.writeToPacket(packet);
		packet.writeByte(rotation);
		packet.writeBoolean(getStorage().isPull);
	}

	@Override
	public void readFromPacket(MCDataInput packet) {
		super.readFromPacket(packet);
		rotation = packet.readUByte() & 3;
		getStorage().isPull=packet.readBoolean();
	}

    @Override
    public void onPlaced(EntityLivingBase entity) {
        rotation = (int) Math.floor(entity.rotationYaw * 4 / 360 + 2.5D) & 3;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("rot", (byte) rotation);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        rotation = tag.getByte("rot") & 3;
    }

    @Override
    public boolean activate(EntityPlayer player, int subHit, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (subHit == 4) {
            toggleMode();
            String subtype = "default";
            if (getStorage().isPull) {
                subtype = "push";
            }
            player.sendMessage(new TextComponentTranslation("enderstorage.tile.mode." + subtype));
            return true;
        }
        String energy=new DecimalFormat("0,000").format(getStorage().getEnergyStored());
        player.sendMessage(new TextComponentTranslation("enderstorage.tile." +energy+"RF"));
        return true;
    }

    private void toggleMode() {
    	getStorage().isPull=!getStorage().isPull;
        markDirty();
    }

    @Override
    public List<IndexedCuboid6> getIndexedCuboids() {
        List<IndexedCuboid6> cuboids = new ArrayList<>();

        cuboids.add(new IndexedCuboid6(0, new Cuboid6(1 / 16D, 0, 1 / 16D, 15 / 16D, 14 / 16D, 15 / 16D)));


        // DyeButtons.
        for (int button = 0; button < 3; button++) {
            EnderDyeButton ebutton = TileEnderEnergy.buttons[button].copy();
            ebutton.rotate(0, 0.5625, 0.0625, 1, 0, 0, 0);
            ebutton.rotateMeta(rotation);

            cuboids.add(new IndexedCuboid6(button + 1, new Cuboid6(ebutton.getMin(), ebutton.getMax())));
        }

        //Lock Button.
        cuboids.add(new IndexedCuboid6(4, new Cuboid6(new EnderKnobSlot(rotation).getSelectionBB())));
        return cuboids;
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
        return comparatorHint>0?15:0;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityEnergy.ENERGY;
    }

    @SuppressWarnings ("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) getStorage();
        }
        return super.getCapability(capability, facing);
    }

    public byte mode() {
        return (byte) (getStorage().isPull?1:0);
    }

    public enum ChestMode {
        DEFAULT((byte) 0),
        PUSH((byte) 1);

        public final byte mode;
        public byte mode() {
            return mode;
        }
        ChestMode(byte mode) {
            this.mode = mode;
        }
    }
}
