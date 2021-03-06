package com.wimbli.WorldBorder.forge;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.world.WorldServer;

/** Handles creation and sending of particle effect packets for server-side emission */
public class Particles
{
    public static void showWhooshEffect(EntityPlayerMP player)
    {
        WorldServer world = player.getServerForPlayer();
        Particles.emitEnder(world, player.posX, player.posY, player.posZ);
        Particles.emitSmoke(world, player.posX, player.posY, player.posZ);
        world.playSoundAtEntity(player, "mob.ghast.fireball", 1.0F, 1.0F);
    }

    private static void emitSmoke(WorldServer world, double x, double y, double z)
    {
        S2APacketParticles packet = new S2APacketParticles(
            "largesmoke", (float) x, (float) y, (float) z,
            0f, 0.5f, 0f, 0.0f, 10
        );

        dispatch(world, packet);
    }

    private static void emitEnder(WorldServer world, double x, double y, double z)
    {
        S2APacketParticles packet = new S2APacketParticles(
            "portal", (float) x, (float) y, (float) z,
            0.5f, 0.5f, 0.5f, 1.0f, 50
        );

        dispatch(world, packet);
    }

    private static void dispatch(WorldServer world, S2APacketParticles packet)
    {
        for (Object o : world.playerEntities)
            ((EntityPlayerMP) o).playerNetServerHandler.sendPacket(packet);
    }
}
