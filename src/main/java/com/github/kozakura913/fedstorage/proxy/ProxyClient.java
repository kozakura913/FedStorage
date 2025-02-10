package com.github.kozakura913.fedstorage.proxy;

import com.github.kozakura913.fedstorage.client.EnderPouchBakery;
import com.github.kozakura913.fedstorage.client.ParticleDummyModel;
import com.github.kozakura913.fedstorage.client.render.entity.TankLayerRenderer;
import com.github.kozakura913.fedstorage.client.render.tile.RenderTileEnderChest;
import com.github.kozakura913.fedstorage.client.render.tile.RenderTileEnderTank;
import com.github.kozakura913.fedstorage.client.render.tile.RenderTileEnderEnergy;
import com.github.kozakura913.fedstorage.handler.ConfigurationHandler;
import com.github.kozakura913.fedstorage.init.ModBlocks;
import com.github.kozakura913.fedstorage.init.ModItems;
import com.github.kozakura913.fedstorage.network.EnderStorageCPH;
import com.github.kozakura913.fedstorage.tile.TileEnderChest;
import com.github.kozakura913.fedstorage.tile.TileEnderEnergy;
import com.github.kozakura913.fedstorage.tile.TileEnderTank;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.texture.TextureUtils;
import codechicken.lib.util.ResourceUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraftforge.fml.client.registry.ClientRegistry;

/**
 * Created by covers1624 on 4/11/2016.
 */
public class ProxyClient extends Proxy {

    @Override
    public void preInit() {
        super.preInit();
        TextureUtils.addIconRegister(EnderPouchBakery.INSTANCE);
        ModBlocks.registerModels();
        ModItems.registerModels();
        RenderTileEnderTank.loadModel();
        ResourceUtils.registerReloadListener(ParticleDummyModel.INSTANCE);
    }

    @Override
    public void init() {
        super.init();
        PacketCustom.assignHandler(EnderStorageCPH.channel, new EnderStorageCPH());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEnderChest.class, new RenderTileEnderChest());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEnderTank.class, new RenderTileEnderTank());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEnderEnergy.class, new RenderTileEnderEnergy());

        if (!ConfigurationHandler.disableCreatorVisuals) {
            for (RenderPlayer renderPlayer : Minecraft.getMinecraft().getRenderManager().getSkinMap().values()) {
                renderPlayer.addLayer(new TankLayerRenderer());
            }
        }
    }
}
