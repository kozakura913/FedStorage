package com.github.kozakura913.fedstorage.init;

import com.github.kozakura913.fedstorage.block.BlockEnderStorage;
import com.github.kozakura913.fedstorage.block.BlockEnderStorage.Type;
import com.github.kozakura913.fedstorage.client.ParticleDummyModel;
import com.github.kozakura913.fedstorage.client.render.item.EnderChestItemRender;
import com.github.kozakura913.fedstorage.client.render.item.EnderTankItemRender;
import com.github.kozakura913.fedstorage.item.ItemEnderStorage;
import com.github.kozakura913.fedstorage.tile.TileEnderChest;
import com.github.kozakura913.fedstorage.tile.TileEnderEnergy;
import com.github.kozakura913.fedstorage.tile.TileEnderTank;
import codechicken.lib.model.ModelRegistryHelper;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Created by covers1624 on 4/11/2016.
 */
public class ModBlocks {

    public static BlockEnderStorage blockEnderStorage;
    public static ItemEnderStorage itemEnderStorage;

    public static void init() {
        blockEnderStorage = new BlockEnderStorage();
        itemEnderStorage = new ItemEnderStorage(blockEnderStorage);
        ForgeRegistries.BLOCKS.register(blockEnderStorage.setRegistryName("ender_storage"));
        ForgeRegistries.ITEMS.register(itemEnderStorage.setRegistryName("ender_storage"));
        GameRegistry.registerTileEntity(TileEnderChest.class, "Fed Chest");
        GameRegistry.registerTileEntity(TileEnderTank.class, "Fed Tank");
        GameRegistry.registerTileEntity(TileEnderEnergy.class, "Fed Energy");
    }

    @SideOnly (Side.CLIENT)
    public static void registerModels() {
        for (int i = 0; i < Type.VALUES.length; i++) {
            Type variant = Type.VALUES[i];
            ModelResourceLocation location = new ModelResourceLocation("fedstorage:ender_storage", "type=" + variant.getName());
            ModelLoader.setCustomModelResourceLocation(itemEnderStorage, i, location);
        }

        ModelRegistryHelper.register(new ModelResourceLocation("fedstorage:ender_storage", "type=fed_chest"), new EnderChestItemRender());
        ModelRegistryHelper.register(new ModelResourceLocation("fedstorage:ender_storage", "type=fed_tank"), new EnderTankItemRender());
        ModelRegistryHelper.register(new ModelResourceLocation("fedstorage:ender_storage", "type=fed_energy"), new EnderChestItemRender());

        ModelLoader.setCustomStateMapper(blockEnderStorage, new StateMap.Builder().ignore(BlockEnderStorage.VARIANTS).build());
        ModelRegistryHelper.register(new ModelResourceLocation("fedstorage:ender_storage", "normal"), ParticleDummyModel.INSTANCE);
    }

}
