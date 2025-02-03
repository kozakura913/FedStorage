package com.github.kozakura913.fedstorage.api;

import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import codechicken.lib.config.ConfigTag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.List;

public interface EnderStoragePlugin {

    AbstractEnderStorage createEnderStorage(EnderStorageManager manager, Frequency freq);

    AbstractEnderStorage createEnderStorage(EnderStorageManager manager, ItemStack stack);

    String identifier();

    void sendClientInfo(EntityPlayer player, List<AbstractEnderStorage> list);

    void loadConfig(ConfigTag tag);
}
