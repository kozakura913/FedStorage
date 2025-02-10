package com.github.kozakura913.fedstorage.plugin;

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.api.EnderStoragePlugin;
import com.github.kozakura913.fedstorage.api.Frequency;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.storage.EnderEnergyStorage;
import codechicken.lib.config.ConfigTag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;


import java.util.List;

public class EnderEnergyStoragePlugin implements EnderStoragePlugin {

    @Override
    public AbstractEnderStorage createEnderStorage(EnderStorageManager manager, Frequency freq) {
        return new EnderEnergyStorage(manager, freq);
    }

    public AbstractEnderStorage createEnderStorage(EnderStorageManager manager, ItemStack stack) {
        return new EnderEnergyStorage(manager, stack);
    }

    @Override
    public String identifier() {
        return "energy";
    }

    public void loadConfig(ConfigTag config) {
    }

    @Override
    public void sendClientInfo(EntityPlayer player, List<AbstractEnderStorage> list) {
    }
}
