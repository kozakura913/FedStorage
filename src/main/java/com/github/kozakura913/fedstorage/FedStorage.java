package com.github.kozakura913.fedstorage;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.handler.ConfigurationHandler;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.network.EnderStorageSPH;
import com.github.kozakura913.fedstorage.storage.EnderEnergyStorage;
import com.github.kozakura913.fedstorage.storage.EnderItemStorage;
import com.github.kozakura913.fedstorage.storage.EnderLiquidStorage;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class FedStorage {
	private Socket tcp_socket;
	private DataInputStream tcp_dis;
	private DataOutputStream tcp_dos;
	private ArrayList<ItemStack> reject_buffer=new ArrayList<>();//転送拒否サーバー内
	private ArrayList<ItemStack> recv_queue=new ArrayList<>();//転送処理バッファ
	private static int RECV_BUFFER_LIMIT=10;
	private static FedStorage INSTANCE=null;
	private static long VERSION=7;
	public static synchronized void init() {
		if(INSTANCE!=null)return;
		INSTANCE=new FedStorage();
	}
	private FedStorage(){
		int index=ConfigurationHandler.fedStorageServer.lastIndexOf(':');
		String host0=ConfigurationHandler.fedStorageServer;
		int port0=3030;
		if(index>0) {
			try{
				port0=Integer.parseInt(ConfigurationHandler.fedStorageServer.substring(index+1));
				host0=ConfigurationHandler.fedStorageServer.substring(0,index);
			}catch(Exception e) {
				
			}
		}
		String host=host0;
		int port=port0;
		if(port==0) {
			System.out.println("FedStorage Remote Disabled");
			return;
		}
		new Thread(()->{
			try {
				while(true) {
					connect(host,port);
					if(tcp_socket!=null) {
						try {
							tcp_socket.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					System.err.println("Connection Lost. Retry After 10s");
					Thread.sleep(10*1000);
					if(FMLCommonHandler.instance().getMinecraftServerInstance()==null) {
						INSTANCE=null;
						return;
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		},"FedStorage-ConnectLoop").start();
	}
	private void connect(String host, int port) {
		try {
			System.out.println("Connect to "+host+":"+port);
			tcp_socket=new Socket(host,port);
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
			tcp_dos.writeByte(8);//command
			tcp_dos.writeUTF(ConfigurationHandler.hostName);
			tcp_dos.flush();
			Thread thread=new Thread(this::sync_loop,"FedStorage");
			thread.start();
			thread.join();
		} catch (Exception e) {
			e.printStackTrace();
			if(tcp_socket!=null)try {
				tcp_socket.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	private void sync_loop() {
		try {
			while(true) {
				Thread.sleep(100);
				//tcp_dos.writeByte(-1);//NOP
				tcp_dos.writeByte(9);//sync start
				tcp_dos.flush();
				EnderStorageManager storage = EnderStorageManager.instance(false);
				ArrayList<AbstractEnderStorage> list=new ArrayList<>();
				storage.storageList(list);
				for(AbstractEnderStorage s:list) {
					if(s instanceof AbstractEnderStorage) {
						set_freq(s);
					}
					if(s instanceof EnderItemStorage) {
						sync_item((EnderItemStorage)s);
					}
					if(s instanceof EnderLiquidStorage) {
						sync_fluid((EnderLiquidStorage)s);
					}
					if(s instanceof EnderEnergyStorage) {
						sync_energy((EnderEnergyStorage)s);
					}
				}
				tcp_dos.writeByte(10);//sync end
				tcp_dos.flush();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void set_freq(AbstractEnderStorage s) throws IOException {
		String id=s.freq.left.name()+","+s.freq.middle.name()+","+s.freq.right.name();
		tcp_dos.writeByte(1);//command
		tcp_dos.writeUTF(id);
		tcp_dos.flush();
	}
	private synchronized void sync_energy(EnderEnergyStorage s) throws IOException {
		if(s.isPull) {
			long max=Integer.MAX_VALUE-s.local_buffer;
			tcp_dos.writeByte(7);
			tcp_dos.writeLong(max);
			tcp_dos.flush();
			long energy=tcp_dis.readLong();
			synchronized(s) {
				s.local_buffer+=energy;
			}
		}else {
			tcp_dos.writeByte(6);
			long send=0;
			synchronized(s) {
				send=s.local_buffer;
				s.local_buffer=0;
			}
			long reject=send;
			try {
				tcp_dos.writeLong(send);
				tcp_dos.flush();
				reject=tcp_dis.readLong();
			}finally {
				synchronized(s) {
					s.local_buffer+=reject;
				}
			}
		}
	}
	private synchronized void sync_item(EnderItemStorage s) throws IOException {
		try{
			s.lastServerRejects=s.send_buffer.size();
			s.lastServerRejects=send_item(s.send_buffer);
			try{
				EnderStorageSPH.sendItemServerRejects(null,s.freq,s.lastServerRejects);
			}catch(Exception e) {
				e.printStackTrace();
				if(FMLCommonHandler.instance().getMinecraftServerInstance()==null) {
					throw new IOException();
				}
			}
		}finally {
			//何らかの理由で拒絶された場合にローカル待機列に戻す
			if(!reject_buffer.isEmpty()) {
				synchronized(s.send_buffer){
					s.send_buffer.addAll(reject_buffer);
				}
				reject_buffer.clear();
			}
		}
		if(s.isPull) {
			recv_item(s.recv_buffer);
		}
	}
	private synchronized void sync_fluid(EnderLiquidStorage s) throws IOException {
		{
			FluidStack copy=null;
			synchronized(s) {
				if(s.send_buffer!=null) {
					copy=s.send_buffer.copy();
				}
			}
			if(copy!=null&&copy.amount>0) {
				send_fluid(copy);
			}
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
		tcp_dos.writeByte(4);//command
		tcp_dos.writeUTF(fluid_name);
		tcp_dos.writeLong(send_buffer.amount);
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
			if(fluid_name==null||recv_buffer.amount==0)fluid_name="";
		}
		if(available<=0)return recv_buffer;
		tcp_dos.writeByte(5);//command
		tcp_dos.writeUTF(fluid_name);
		tcp_dos.writeLong(available);
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
		long amount=pack_dis.readLong();
		//NBTは必ず存在するわけではない
		//NBTサイズ
		int nbt_length=pack_dis.readShort();
		NBTTagCompound nbt=readNBT(pack_dis,nbt_length);
		if(fluid_name.isEmpty()&&name!=null&&!name.isEmpty()) {
			fluid_name=name;
			Fluid f=FluidRegistry.getFluid(fluid_name);
			recv_buffer=new FluidStack(f, (int)amount,nbt);
		}
		recv_buffer.amount=(int)amount;
		return recv_buffer;
	}
	private synchronized void recv_item(ArrayList<ItemStack> recv_buffer) throws IOException {
		if(recv_buffer.isEmpty()) {
			RECV_BUFFER_LIMIT=Math.max(RECV_BUFFER_LIMIT+1,1000);
		}else {
			RECV_BUFFER_LIMIT=Math.max(0,RECV_BUFFER_LIMIT-1);
		}
		int available=RECV_BUFFER_LIMIT-recv_buffer.size();
		if(available<=0)return;
		tcp_dos.writeByte(3);//command
		tcp_dos.writeInt(available);
		tcp_dos.flush();
		int packet_length=tcp_dis.readInt();
		if(packet_length<=0)return;
		byte[] bb=new byte[packet_length];
		tcp_dis.readFully(bb);
		ByteArrayInputStream pack_bis = new ByteArrayInputStream(bb);
		DataInputStream pack_dis = new DataInputStream(new GZIPInputStream(pack_bis));
		int items_count=pack_dis.readInt();
		for(int i=0;i<items_count;i++) {
			pack_dis.readUTF();//アイテムID
			pack_dis.readInt();//ダメージ値
			int stack_size=pack_dis.readInt();//スタックサイズ
			//NBTにはアイテム名など含まれるのでこれだけでもいい
			//NBTサイズ
			int nbt_length=pack_dis.readShort();
			if(nbt_length==-1) {
				recv_queue.add(null);
			}else {
				NBTTagCompound nbt = readNBT(pack_dis,nbt_length);
				ItemStack is=new ItemStack(nbt);
				is.setCount(stack_size);
				recv_queue.add(is);
			}
		}
		if(recv_queue.isEmpty())return;
		for(int i=0;i<items_count;i++) {
			ItemStack is=recv_queue.get(i);
			if(is==null) {
				int nbt_length=tcp_dis.readInt();
				System.out.println("big nbt "+nbt_length);
				byte[] nbt_bytes=new byte[nbt_length];
				tcp_dis.readFully(nbt_bytes);
				ByteArrayInputStream nbt_bis = new ByteArrayInputStream(nbt_bytes);
				DataInputStream nbt_dis = new DataInputStream(new GZIPInputStream(nbt_bis));
				NBTTagCompound nbt = CompressedStreamTools.read(nbt_dis);
				is=new ItemStack(nbt);
				recv_queue.set(i,is);
			}
		}
		synchronized(recv_buffer){
			recv_buffer.addAll(recv_queue);
			recv_queue.clear();
		}
	}
	private synchronized int send_item(ArrayList<ItemStack> send_buffer) throws IOException {
		ArrayList<ItemStack> copy;
		synchronized(send_buffer){
			if(send_buffer.isEmpty())return 0;
			copy=(ArrayList<ItemStack>) send_buffer.clone();
			send_buffer.clear();
		}
		//通信が成功する前はすべて失敗した事にする
		reject_buffer.addAll(copy);
		//アイテム数
		int item_count=0;
		for(ItemStack stack : copy) {
			if(stack==null)continue;
			Item item = stack.getItem();
			if(item==Items.AIR||item==null)continue;
			item_count++;
		}
		tcp_dos.writeByte(2);//command
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream pack_dos = new DataOutputStream(new GZIPOutputStream(baos));
		pack_dos.writeInt(item_count);
		ArrayList<byte[]> big_nbt=new ArrayList<>();
		for(ItemStack stack : copy) {
			if(stack==null)continue;
			Item item = stack.getItem();
			if(item==Items.AIR||item==null)continue;
			ResourceLocation nameId = item.getRegistryName();
			pack_dos.writeUTF(nameId.getResourceDomain()+":"+nameId.getResourcePath());//アイテムID
			pack_dos.writeInt(stack.getItemDamage());//ダメージ値
			pack_dos.writeInt(stack.getCount());//スタックサイズ
			NBTTagCompound nbt = stack.serializeNBT();
			//NBTにはアイテム名など含まれるのでこれだけでもいい
			byte[] extra_nbt=writeNBT(nbt,pack_dos);
			if(extra_nbt!=null) {
				big_nbt.add(extra_nbt);
			}
		}
		pack_dos.close();
		byte[] pack_bb = baos.toByteArray();
		tcp_dos.writeInt(pack_bb.length);
		tcp_dos.write(pack_bb);
		for(byte[] nbt:big_nbt) {
			tcp_dos.writeInt(nbt.length);
			tcp_dos.write(nbt);
		}
		tcp_dos.flush();
		int packet_length=tcp_dis.readInt();
		if(packet_length<=0)return 0;
		byte[] bb=new byte[packet_length];
		tcp_dis.readFully(bb);
		ByteArrayInputStream bis = new ByteArrayInputStream(bb);
		DataInputStream dis = new DataInputStream(new GZIPInputStream(bis));
		int reject_count=dis.readInt();
		int[] reject_index=null;
		if(reject_count>0) {
			reject_index=new int[reject_count];
			for(int i=0;i<reject_count;i++) {
				reject_index[i]=dis.readInt();
			}
		}
		//通信が無事に終了した
		reject_buffer.clear();
		if(reject_index!=null) {
			//拒絶されたアイテムがある場合拒絶リストを更新
			for(int index:reject_index) {
				reject_buffer.add(copy.get(index));
			}
		}
		return reject_count;
	}
	private NBTTagCompound readNBT(DataInputStream dis,int nbt_length) throws IOException {
		if(nbt_length<1)return null;
		byte[] nbt_bytes=new byte[nbt_length];
		//NBTタグ
		dis.readFully(nbt_bytes);
		ByteArrayInputStream nbt_bis = new ByteArrayInputStream(nbt_bytes);
		DataInputStream nbt_dis = new DataInputStream(nbt_bis);
		return CompressedStreamTools.read(nbt_dis);
	}
	private byte[] writeNBT(NBTTagCompound nbt,DataOutputStream dos) throws IOException {
		if(nbt==null) {
			dos.writeShort(0);
			return null;
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream nbt_dos = new DataOutputStream(bos);
		CompressedStreamTools.write(nbt,nbt_dos);
		byte[] bb= bos.toByteArray();
		int send_length=bb.length;
		if(send_length>32767) {
			dos.writeShort(-1);
			bos.reset();
			GZIPOutputStream gz = new GZIPOutputStream(bos);
			gz.write(bb);
			gz.close();
			return bos.toByteArray();
		}
		//NBTサイズ
		dos.writeShort(send_length);
		if(send_length<1)return null;
		//NBTタグ
		dos.write(bb);
		return null;
	}
}
