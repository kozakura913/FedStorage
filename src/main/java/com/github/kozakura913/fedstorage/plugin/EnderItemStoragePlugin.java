package com.github.kozakura913.fedstorage.plugin;

import com.github.kozakura913.fedstorage.api.AbstractEnderStorage;
import com.github.kozakura913.fedstorage.api.EnderStoragePlugin;
import com.github.kozakura913.fedstorage.api.Frequency;
import com.github.kozakura913.fedstorage.manager.EnderStorageManager;
import com.github.kozakura913.fedstorage.network.EnderStorageSPH;
import com.github.kozakura913.fedstorage.storage.EnderItemStorage;
import codechicken.lib.config.ConfigTag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.List;

public class EnderItemStoragePlugin implements EnderStoragePlugin {

    public static final int[] sizes = new int[] { 9, 27, 54 };
    public static int configSize;

    @Override
    public AbstractEnderStorage createEnderStorage(EnderStorageManager manager, Frequency freq) {
        return new EnderItemStorage(manager, freq);
    }

    @Override
    public AbstractEnderStorage createEnderStorage(EnderStorageManager manager, ItemStack stack) {
        return new EnderItemStorage(manager, stack);
    }

    @Override
    public String identifier() {
        return "item";
    }

    public void loadConfig(ConfigTag config) {
        configSize = config.getTag("storage-size").setComment("The size of each inventory of EnderStorage. 0 = 3x3, 1 = 3x9, 2 = 6x9").getIntValue(1);
        if (configSize < 0 || configSize > 2) {
            configSize = 1;
        }
    }

    @Override
    public void sendClientInfo(EntityPlayer player, List<AbstractEnderStorage> list) {
        for (AbstractEnderStorage inv : list) {
            if (((EnderItemStorage) inv).openCount() > 0) {
                EnderStorageSPH.sendOpenUpdateTo(player, inv.freq, true);
            }
        }
    }
}
