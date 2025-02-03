package com.github.kozakura913.fedstorage;

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

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.storage.EnderItemStorage;
import com.github.kozakura913.fedstorage.storage.EnderLiquidStorage;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class FedStorage {
    private Socket tcp_socket;
	private DataInputStream tcp_dis;
	private DataOutputStream tcp_dos;
	private ArrayList<ItemStack> reject_buffer=new ArrayList<>();//転送拒否サーバー内
	private ArrayList<ItemStack> recv_queue=new ArrayList<>();//転送処理バッファ
	private static int RECV_BUFFER_LIMIT=10;
	private static FedStorage INSTANCE=null;
	private static long VERSION=1;
	public static synchronized void init() {
		if(INSTANCE!=null)return;
		INSTANCE=new FedStorage();
	}
	private FedStorage(){
    	try {
			tcp_socket=new Socket("127.0.0.1",3030);
			tcp_socket.setSoTimeout(10000);//10s
			tcp_dis = new DataInputStream(tcp_socket.getInputStream());
			tcp_dos = new DataOutputStream(new BufferedOutputStream(tcp_socket.getOutputStream()));
			long server_version=tcp_dis.readLong();
			if(server_version!=VERSION) {
				System.err.println("ClientVersion="+VERSION+",ServerVersion="+server_version);
				tcp_socket.close();
				tcp_socket=null;
				return;
			}
			new Thread(()->{
				try {
					while(true) {
						Thread.sleep(1000);
						try {
							EnderStorageManager storage = EnderStorageManager.instance(false);
							ArrayList<AbstractEnderStorage> list=new ArrayList<>();
							storage.storageList(list);
							for(AbstractEnderStorage s:list) {
								if(s instanceof EnderItemStorage) {
									sync_item((EnderItemStorage)s);
								}
								if(s instanceof EnderLiquidStorage) {
									sync_fluid((EnderLiquidStorage)s);
								}
							}
						} catch (IOException e) {
							e.printStackTrace();
							Thread.sleep(1000);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			},"FedStorage").start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    private synchronized void sync_item(EnderItemStorage s) throws IOException {
		String id=s.freq.left.name()+","+s.freq.middle.name()+s.freq.right.name();
		tcp_dos.writeInt(1);//command
		tcp_dos.writeUTF(id);
		tcp_dos.flush();
		send_item(s.send_buffer);
		if(s.isPull) {
			recv_item(s.recv_buffer);
		}
    }
    private synchronized void sync_fluid(EnderLiquidStorage s) throws IOException {
		String id=s.freq.left.name()+","+s.freq.middle.name()+s.freq.right.name();
		tcp_dos.writeInt(1);//command
		tcp_dos.writeUTF(id);
		tcp_dos.flush();
		{
			FluidStack copy=null;
			synchronized(s) {
				if(s.send_buffer!=null) {
					copy=s.send_buffer.copy();
				}
			}
			send_fluid(copy);
			synchronized(s) {
				if(copy==null||copy.amount<=0) {
					s.send_buffer=null;
				}
			}
		}
		if(s.isPull) {
			FluidStack copy=null;
			synchronized(s) {
				if(s.recv_buffer!=null) {
					copy=s.recv_buffer.copy();
				}
			}
			copy=recv_fluid(copy);
			synchronized(s) {
				if(s.recv_buffer!=null) {
					if(s.recv_buffer.isFluidEqual(copy)) {
						s.recv_buffer.amount=copy.amount;
						copy=null;
					}
				}else {
					s.recv_buffer=copy;
					copy=null;
				}
			}
			if(copy!=null) {
				send_fluid(copy);
			}
		}
    }
    private synchronized void send_fluid(FluidStack send_buffer) throws IOException {
    	if(send_buffer==null)return;
    	String fluid_name=FluidRegistry.getFluidName(send_buffer);
		if(fluid_name==null)return;
		tcp_dos.writeInt(4);//command
		tcp_dos.writeUTF(fluid_name);
		tcp_dos.writeInt(send_buffer.amount);
		writeNBT(send_buffer.tag,tcp_dos);
		tcp_dos.flush();
		send_buffer.amount=0;
    }
    private synchronized FluidStack recv_fluid(FluidStack recv_buffer) throws IOException {
    	int available=Integer.MAX_VALUE;
    	String fluid_name="";
    	if(recv_buffer!=null) {
    		available=Integer.MAX_VALUE-recv_buffer.amount;
    		fluid_name=recv_buffer.getFluid().getName();
    		if(fluid_name==null)fluid_name="";
    	}
    	if(available<=0)return recv_buffer;
		tcp_dos.writeInt(5);//command
		tcp_dos.writeUTF(fluid_name);
		tcp_dos.writeInt(available);
		if(recv_buffer!=null) {
			writeNBT(recv_buffer.tag,tcp_dos);
		}else {
			writeNBT(null,tcp_dos);
		}
		tcp_dos.flush();
		int packet_length=tcp_dis.readInt();
		if(packet_length<=0)return recv_buffer;
		byte[] bb=new byte[packet_length];
		tcp_dis.readFully(bb);
		ByteArrayInputStream pack_bis = new ByteArrayInputStream(bb);
		DataInputStream pack_dis = new DataInputStream(pack_bis);
		String name=pack_dis.readUTF();
		int amount=pack_dis.readInt();
    	//NBTは必ず存在するわけではない
		NBTTagCompound nbt=readNBT(pack_dis);
		if(fluid_name.isEmpty()&&name!=null&&!name.isEmpty()) {
			fluid_name=name;
			if(recv_buffer==null) {
	        	Fluid f=FluidRegistry.getFluid(fluid_name);
				recv_buffer=new FluidStack(f, amount,nbt);
			}
		}
		recv_buffer.amount=amount;
		return recv_buffer;
    }
    private synchronized void recv_item(ArrayList<ItemStack> recv_buffer) throws IOException {
    	int available=RECV_BUFFER_LIMIT-recv_buffer.size();
    	if(available<=0)return;
		tcp_dos.writeInt(3);//command
		tcp_dos.writeInt(available);
		tcp_dos.flush();
		int packet_length=tcp_dis.readInt();
		if(packet_length<=0)return;
		byte[] bb=new byte[packet_length];
		tcp_dis.readFully(bb);
		ByteArrayInputStream pack_bis = new ByteArrayInputStream(bb);
		DataInputStream pack_dis = new DataInputStream(pack_bis);
		int items_count=pack_dis.readInt();
		for(int i=0;i<items_count;i++) {
        	pack_dis.readUTF();//アイテムID
        	pack_dis.readInt();//ダメージ値
        	int stack_size=pack_dis.readInt();//スタックサイズ
        	//NBTにはアイテム名など含まれるのでこれだけでもいい
        	NBTTagCompound nbt = readNBT(pack_dis);
        	ItemStack is=new ItemStack(nbt);
        	is.setCount(stack_size);
        	recv_queue.add(is);
		}
		if(recv_queue.isEmpty())return;
    	synchronized(recv_buffer){
        	recv_buffer.addAll(recv_queue);
        	recv_queue.clear();
    	}
    }
    private synchronized void send_item(ArrayList<ItemStack> send_buffer) throws IOException {
    	ArrayList<ItemStack> copy;
    	synchronized(send_buffer){
    		if(send_buffer.isEmpty())return;
        	copy=(ArrayList<ItemStack>) send_buffer.clone();
        	send_buffer.clear();
    	}
		reject_buffer.addAll(copy);
    	//アイテム数
    	int item_count=0;
    	for(ItemStack stack : copy) {
    		if(stack==null)continue;
    		Item item = stack.getItem();
    		if(item==Items.AIR||item==null)continue;
    		item_count++;
    		System.out.println(item);
    	}
    	tcp_dos.writeByte(2);//command
    	tcp_dos.writeInt(item_count);
    	for(ItemStack stack : copy) {
    		if(stack==null)continue;
    		Item item = stack.getItem();
    		if(item==Items.AIR||item==null)continue;
    		ResourceLocation nameId = item.getRegistryName();
    		tcp_dos.writeUTF(nameId.getResourceDomain()+":"+nameId.getResourcePath());//アイテムID
    		tcp_dos.writeInt(stack.getItemDamage());//ダメージ値
    		tcp_dos.writeInt(stack.getCount());//スタックサイズ
        	NBTTagCompound nbt = stack.serializeNBT();
        	//NBTにはアイテム名など含まれるのでこれだけでもいい
        	writeNBT(nbt,tcp_dos);
    	}
    	tcp_dos.flush();
		int packet_length=tcp_dis.readInt();
		if(packet_length<=0)return;
		byte[] bb=new byte[packet_length];
		tcp_dis.readFully(bb);
		ByteArrayInputStream bis = new ByteArrayInputStream(bb);
		DataInputStream dis = new DataInputStream(bis);
		int reject_count=dis.readInt();
		reject_buffer.clear();
		if(reject_count>0) {
    		for(int i=0;i<reject_count;i++) {
    			int index=dis.readInt();
    			reject_buffer.add(copy.get(index));
    		}
		}
    	if(reject_buffer.isEmpty())return;
    	synchronized(send_buffer){
    		send_buffer.addAll(reject_buffer);
    	}
    }
    private NBTTagCompound readNBT(DataInputStream dis) throws IOException {
    	//NBTサイズ
    	int nbt_length=dis.readShort();
    	if(nbt_length<1)return null;
    	byte[] nbt_bytes=new byte[nbt_length];
		//NBTタグ
    	dis.readFully(nbt_bytes);
    	ByteArrayInputStream nbt_bis = new ByteArrayInputStream(nbt_bytes);
    	DataInputStream nbt_dis = new DataInputStream(nbt_bis);
		return CompressedStreamTools.read(nbt_dis);
    }
    private void writeNBT(NBTTagCompound nbt,DataOutputStream dos) throws IOException {
    	if(nbt==null) {
    		dos.writeShort(0);
    		return;
    	}
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
    	DataOutputStream nbt_dos = new DataOutputStream(bos);
    	CompressedStreamTools.write(nbt,nbt_dos);
    	byte[] bb= bos.toByteArray();
    	int send_length=bb.length;
    	//NBTサイズ
		dos.writeShort(send_length);
		if(send_length<1)return;
		//NBTタグ
		dos.write(bb);
    }
}
