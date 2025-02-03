package com.github.kozakura913.fedstorage;

import com.github.kozakura913.fedstorage.command.EnderStorageCommand;
import com.github.kozakura913.fedstorage.handler.ConfigurationHandler;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.proxy.Proxy;
import com.github.kozakura913.fedstorage.util.EnderHooks;
import codechicken.lib.CodeChickenLib;
import codechicken.lib.internal.ModDescriptionEnhancer;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import static com.github.kozakura913.fedstorage.EnderStorage.*;
import static codechicken.lib.CodeChickenLib.MC_VERSION;
import static codechicken.lib.CodeChickenLib.MC_VERSION_DEP;

@Mod (modid = MOD_ID, name = MOD_NAME, dependencies = DEPENDENCIES, acceptedMinecraftVersions = MC_VERSION_DEP)
public class EnderStorage {

    public static final String MOD_ID = "fedstorage";
    public static final String MOD_NAME = "FedStorage";
    public static final String VERSION = "${mod_version}";
    public static final String DEPENDENCIES = "required-after:forge@[14.23.4,);" + CodeChickenLib.MOD_VERSION_DEP;

    @SidedProxy (clientSide = "com.github.kozakura913.fedstorage.proxy.ProxyClient", serverSide = "com.github.kozakura913.fedstorage.proxy.Proxy")
    public static Proxy proxy;

    @Mod.Instance (EnderStorage.MOD_ID)
    public static EnderStorage instance;
    public static EnderHooks hooks = new EnderHooks();

    public EnderStorage() {
        instance = this;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        hooks.hookPreInit();
        ConfigurationHandler.init(event.getSuggestedConfigurationFile());
        proxy.preInit();
        ModMetadata metadata = event.getModMetadata();
        metadata.description = modifyDesc(metadata.description);
        ModDescriptionEnhancer.registerEnhancement(MOD_ID, MOD_NAME);
        ConfigurationHandler.loadConfig();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new EnderStorageCommand());
    }

    @Mod.EventHandler
    public void preServerStart(FMLServerStartedEvent event) {
        EnderStorageManager.reloadManager(false);
    }

    private static String modifyDesc(String desc) {
        desc += "\n";
        desc += "    Credits: Ecu - original idea, design, chest and pouch texture\n";
        desc += "    Rosethorns - tank model\n";
        desc += "    Soaryn - tank texture\n";
        return desc;
    }
}
