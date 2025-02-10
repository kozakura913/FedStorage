package com.github.kozakura913.fedstorage.storage;

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.api.Frequency;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class EnderEnergyStorage extends AbstractEnderStorage implements net.minecraftforge.energy.IEnergyStorage{

	public long local_buffer;
	public int energy;
	public EnderEnergyStorage(EnderStorageManager manager, Frequency freq) {
		super(manager, freq);
	}

	public EnderEnergyStorage(EnderStorageManager manager, ItemStack stack) {
		super(manager, EnderLiquidStorage.getFreq(stack));
	}

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		if(!canReceive()) {
			return 0;
		}
		int in=Math.min(Integer.MAX_VALUE-energy,maxReceive);
		if(!simulate) {
			energy+=in;
			setDirty();
		}
		return in;
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		if(!canExtract()) {
			return 0;
		}
		int out=Math.min(energy,maxExtract);
		if(!simulate) {
			energy-=out;
			setDirty();
		}
		return out;
	}

	@Override
	public int getEnergyStored() {
		return energy;
	}

	@Override
	public int getMaxEnergyStored() {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean canExtract() {
		return this.isPull;
	}

	@Override
	public boolean canReceive() {
		return !this.isPull;
	}

	@Override
	public void clearStorage() {
		energy=0;
	}

	@Override
	public String type() {
		return "energy";
	}

	@Override
	public NBTTagCompound saveToTag() {
		NBTTagCompound nbt=new NBTTagCompound();
		nbt.setInteger("energy", energy);
		nbt.setBoolean("isPull", isPull);
		return nbt;
	}

	@Override
	public void loadFromTag(NBTTagCompound tag) {
		energy=tag.getInteger("energy");
		isPull=tag.getBoolean("isPull");
	}

	@Override
	public void setStack(ItemStack stack) {
		
	}
}
