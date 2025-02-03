package com.github.kozakura913.fedstorage.storage;

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.api.Frequency;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import codechicken.lib.fluid.ExtendedFluidTank;
import codechicken.lib.fluid.FluidUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;

import static com.github.kozakura913.fedstorage.handler.ConfigurationHandler.tankSize;

import java.util.ArrayList;

public class EnderLiquidStorage extends AbstractEnderStorage implements IFluidHandler, IFluidHandlerItem {

    public static final int CAPACITY = tankSize;
	public FluidStack recv_buffer;//インベントリ行きサーバー内
	public FluidStack send_buffer;//転送待ちサーバー内

    @Nonnull
    @Override
    public ItemStack getContainer() {
        return stack;
    }

    public void setFluid(FluidStack fluid) {
        tank.setFluid(fluid);
    }

    private class Tank extends ExtendedFluidTank {

        public Tank(int capacity) {
            super(capacity);
        }

        @Override
        public void onLiquidChanged() {
            setDirty();
        }

        public void setFluid(FluidStack fluid) {
            NBTTagCompound tag = new NBTTagCompound();
            fluid.writeToNBT(tag);
            this.fromTag(tag);
        }
    }

    private Tank tank;
    private ItemStack stack;

    public static Frequency getFreq(ItemStack stack) {
        return Frequency.readFromStack(stack);
    }

    public EnderLiquidStorage(EnderStorageManager manager, ItemStack stack) {
        super(manager, getFreq(stack));
        this.stack = stack;
        tank = new Tank(CAPACITY);
    }

    public EnderLiquidStorage(EnderStorageManager manager, Frequency freq) {
        super(manager, freq);
        tank = new Tank(CAPACITY);
    }

    @Override
    public void clearStorage() {
        tank = new Tank(CAPACITY);
        setDirty();
    }

    @Override
    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public String type() {
        return "liquid";
    }

    public void loadFromTag(NBTTagCompound tag) {
        tank.fromTag(tag.getCompoundTag("tank"));
        recv_buffer=FluidUtils.read(tag.getCompoundTag("Recv"));
        send_buffer=FluidUtils.read(tag.getCompoundTag("Send"));
    }

    public NBTTagCompound saveToTag() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("tank", tank.toTag());
        compound.setTag("Recv", FluidUtils.write(recv_buffer, new NBTTagCompound()));
        compound.setTag("Send", FluidUtils.write(send_buffer, new NBTTagCompound()));
        return compound;
    }

    public FluidStack getFluid() {
        return tank.getFluid();
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return tank.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        return tank.drain(resource, doDrain);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return tank.drain(maxDrain, doDrain);
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return new IFluidTankProperties[] { new FluidTankProperties(tank.getInfo().fluid, tank.getInfo().capacity) };
    }
}
