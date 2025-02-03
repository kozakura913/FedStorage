package com.github.kozakura913.fedstorage.proxy;

import com.github.kozakura913.fedstorage.EnderStorage;
import com.github.kozakura913.fedstorage.init.ModBlocks;
import com.github.kozakura913.fedstorage.init.ModItems;
import com.github.kozakura913.fedstorage.item.ItemEnderPouch;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.network.EnderStorageSPH;
import com.github.kozakura913.fedstorage.network.TankSynchroniser;
import com.github.kozakura913.fedstorage.plugin.EnderItemStoragePlugin;
import com.github.kozakura913.fedstorage.plugin.EnderLiquidStoragePlugin;
import codechicken.lib.packet.PacketCustom;
import net.minecraftforge.common.MinecraftForge;

/**
 * Created by covers1624 on 4/11/2016.
 */
public class Proxy {

    public void preInit() {
        EnderStorageManager.registerPlugin(new EnderItemStoragePlugin());
        EnderStorageManager.registerPlugin(new EnderLiquidStoragePlugin());
        ModBlocks.init();
        ModItems.init();
        //MinecraftForge.EVENT_BUS.register(EnderStorageRecipe.init());
        MinecraftForge.EVENT_BUS.register(new EnderStorageManager.EnderStorageSaveHandler());
        MinecraftForge.EVENT_BUS.register(new TankSynchroniser());
        MinecraftForge.EVENT_BUS.register(new ItemEnderPouch());
    }

    public void init() {
        PacketCustom.assignHandler(EnderStorageSPH.channel, new EnderStorageSPH());
    }

}
