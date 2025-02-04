package com.github.kozakura913.fedstorage.storage;

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.api.Frequency;
import com.github.kozakura913.fedstorage.client.gui.GuiEnderItemStorage;
import com.github.kozakura913.fedstorage.container.ContainerEnderItemStorage;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.network.EnderStorageSPH;
import codechicken.lib.inventory.InventoryUtils;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.util.ArrayUtils;
import codechicken.lib.util.ClientUtils;
import codechicken.lib.util.ServerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.github.kozakura913.fedstorage.plugin.EnderItemStoragePlugin.configSize;
import static com.github.kozakura913.fedstorage.plugin.EnderItemStoragePlugin.sizes;
import static com.github.kozakura913.fedstorage.storage.EnderLiquidStorage.getFreq;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class EnderItemStorage extends AbstractEnderStorage implements IInventory {

	private ItemStack[] items;
	private int open;
	private int size;
	public ArrayList<ItemStack> recv_buffer=new ArrayList<>();//インベントリ行きサーバー内
	public ArrayList<ItemStack> send_buffer=new ArrayList<>();//転送待ちサーバー内
	public int lastServerRejects,recv_queue,send_queue;

	public EnderItemStorage(EnderStorageManager manager, Frequency freq) {
		super(manager, freq);
		size = configSize;
		empty();
	}

	@Override
	public void setStack(ItemStack stack) {

	}

	public EnderItemStorage(EnderStorageManager manager, ItemStack stack) {
		super(manager, getFreq(stack));
		size = configSize;
		empty();
	}

	@Override
	public void clearStorage() {
		synchronized (this) {
			empty();
			setDirty();
		}
	}

	private void alignSize() {
		if (configSize > size) {
			ItemStack[] newItems = new ItemStack[sizes[configSize]];
			ArrayUtils.fillArray(newItems, ItemStack.EMPTY);
			System.arraycopy(items, 0, newItems, 0, items.length);
			items = newItems;
			size = configSize;
			markDirty();
		} else {
			int numStacks = 0;
			for (ItemStack item : items) {
				if (!item.isEmpty()) {
					numStacks++;
				}
			}

			if (numStacks <= sizes[configSize]) {
				ItemStack[] newItems = new ItemStack[sizes[configSize]];
				ArrayUtils.fillArray(newItems, ItemStack.EMPTY);
				int copyTo = 0;
				for (ItemStack item : items) {
					if (!item.isEmpty()) {
						newItems[copyTo] = item;
						copyTo++;
					}
				}
				items = newItems;
				size = configSize;
				markDirty();
			}
		}
	}

	@Override
	public String type() {
		return "item";
	}

	public void loadFromTag(NBTTagCompound tag) {
		size = tag.getByte("size");
		empty();
		InventoryUtils.readItemStacksFromTag(items, tag.getTagList("Items", 10));
		isPull=tag.getBoolean("isPull");
		NBTTagList tagList ;
		ItemStack[] list;
		tagList = tag.getTagList("Recv", 10);
		list = new ItemStack[tagList.tagCount()];
		InventoryUtils.readItemStacksFromTag(list, tagList);
		recv_buffer.addAll(Arrays.asList(list));
		tagList = tag.getTagList("Send", 10);
		list = new ItemStack[tagList.tagCount()];
		InventoryUtils.readItemStacksFromTag(list, tagList);
		send_buffer.addAll(Arrays.asList(list));
		if (size != configSize) {
			alignSize();
		}
	}
	public NBTTagCompound saveToTag() {
		if (size != configSize && open == 0) {
			alignSize();
		}

		NBTTagCompound compound = new NBTTagCompound();
		compound.setTag("Items", InventoryUtils.writeItemStacksToTag(items));
		compound.setByte("size", (byte) size);
		compound.setBoolean("isPull", isPull);
		synchronized(recv_buffer) {
			ItemStack[] arr=new ItemStack[recv_buffer.size()];
			recv_buffer.toArray(arr);
			compound.setTag("Recv", InventoryUtils.writeItemStacksToTag(arr));
		}
		synchronized(send_buffer) {
			ItemStack[] arr=new ItemStack[send_buffer.size()];
			send_buffer.toArray(arr);
			compound.setTag("Send", InventoryUtils.writeItemStacksToTag(arr));
		}

		return compound;
	}

	public ItemStack getStackInSlot(int slot) {
		synchronized (this) {
			return items[slot];
		}
	}

	public ItemStack[] getInventory() {
		synchronized (this) {
			return items;
		}
	}

	public ItemStack removeStackFromSlot(int slot) {
		synchronized (this) {
			return InventoryUtils.removeStackFromSlot(this, slot);
		}
	}

	public void setInventorySlotContents(int slot, ItemStack stack) {
		synchronized (this) {
			if(this.isPull||lastServerRejects>1) {
				items[slot] = stack;
			}else if(!super.manager.client) {
				synchronized (send_buffer) {
					send_buffer.add(stack);
				}
			}
			markDirty();
		}
	}

    public void openInventory() {
        if (manager.client) {
            return;
        }

        synchronized (this) {
            open++;
            if (open == 1) {
                EnderStorageSPH.sendOpenUpdateTo(null, freq, true);
            }
        }
    }

    public void closeInventory() {
        if (manager.client) {
            return;
        }

        synchronized (this) {
            open--;
            if (open == 0) {
                EnderStorageSPH.sendOpenUpdateTo(null, freq, false);
            }
        }
    }

    public int getNumOpen() {
        return open;
    }

    @Override
    public int getSizeInventory() {
        return sizes[size];
    }

    @Override
    public boolean isEmpty() {
        return ArrayUtils.count(items, (stack -> !stack.isEmpty())) <= 0;
    }

    public ItemStack decrStackSize(int slot, int size) {
        synchronized (this) {
            return InventoryUtils.decrStackSize(this, slot, size);
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        setDirty();
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer var1) {
        return true;
    }

    public void empty() {
        items = new ItemStack[getSizeInventory()];
        ArrayUtils.fillArray(items, ItemStack.EMPTY);
    }

    public void openSMPGui(EntityPlayer player, final String name) {
        ServerUtils.openSMPContainer((EntityPlayerMP) player, new ContainerEnderItemStorage(player.inventory, this, false), (player1, windowId) -> {

            PacketCustom packet = new PacketCustom(EnderStorageSPH.channel, 2);
            packet.writeByte(windowId);
            //packet.writeString(owner);
            freq.writeToPacket(packet);
            packet.writeString(name);
            packet.writeByte(size);
            this.send_queue=send_buffer.size();
            this.recv_queue=recv_buffer.size();
            packet.writeInt(this.send_queue);
            packet.writeInt(this.recv_queue);

            packet.sendToPlayer(player1);
        });
    }

    public int getSize() {
        return size;
    }

    public int openCount() {
        return open;
    }

    public void setClientOpen(int i) {
        if (manager.client) {
            open = i;
        }
    }

    @SideOnly (Side.CLIENT)
    public void openClientGui(int windowID, InventoryPlayer playerInv, PacketCustom packet) {
		String name=packet.readString();
		this.size=packet.readUByte();
		this.send_queue=packet.readInt();
		this.recv_queue=packet.readInt();
        empty();
        ClientUtils.openSMPGui(windowID, new GuiEnderItemStorage(playerInv, this, name));
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Override
    public ITextComponent getDisplayName() {
        return null;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {

        return 0;
    }

    @Override
    public void clear() {
    }
}
