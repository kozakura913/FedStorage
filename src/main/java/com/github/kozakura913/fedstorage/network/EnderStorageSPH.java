package com.github.kozakura913.fedstorage.network;

import com.github.kozakura913.fedstorage.api.Frequency;
import codechicken.lib.packet.ICustomPacketHandler.IServerPacketHandler;
import codechicken.lib.packet.PacketCustom;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.INetHandlerPlayServer;

public class EnderStorageSPH implements IServerPacketHandler {

    public static final String channel = "FedS";

    @Override
    public void handlePacket(PacketCustom packet, EntityPlayerMP sender, INetHandlerPlayServer handler) {
        switch (packet.getType()) {
            case 1:
                TankSynchroniser.handleVisiblityPacket(sender, packet);
                break;
        }
    }

    public static void sendOpenUpdateTo(EntityPlayer player, Frequency freq, boolean open) {
        PacketCustom packet = new PacketCustom(channel, 3);
        //packet.writeString(owner);
        freq.writeToPacket(packet);
        packet.writeBoolean(open);

        packet.sendToPlayer(player);
    }
    public static void sendItemServerRejects(EntityPlayer player, Frequency freq, int lastServerRejects) {
        PacketCustom packet = new PacketCustom(channel, 10);
        //packet.writeString(owner);
        freq.writeToPacket(packet);
        packet.writeInt(lastServerRejects);

        packet.sendToPlayer(player);
    }
}
