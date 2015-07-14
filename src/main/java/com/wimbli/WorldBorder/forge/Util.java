package com.wimbli.WorldBorder.forge;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * Static class for utility functions (syntactic sugar) to help transition from Bukkit
 * to Forge conventions
 */
public class Util
{
    public static String getWorldName(World world)
    {
        return world.provider.getSaveFolder();
    }

    /**
     * Case-sensitive search in global DimensionManager for a world of a given name
     * @param name Name of world to find
     * @return World if found, else null
     */
    public static WorldServer getWorld(String name)
    {
        for ( WorldServer world : DimensionManager.getWorlds() )
            if ( world.provider.getSaveFolder().equals(name) )
                return world;

        return null;
    }

    /**
     * Gets the ID of the block type of the given block position
     */
    public static int getBlockID(World world, int x, int y, int z)
    {
        Block block = world.getBlock(x, y, z);

        return Block.getIdFromBlock(block);
    }

    /**
     * Attempts to a translate a given string/key using the local language, and then
     * using the fallback language.
     * @param msg String or language key to translate
     * @return Translated or same string
     */
    public static String translate(String msg)
    {
        return StatCollector.canTranslate(msg)
            ? StatCollector.translateToLocal(msg)
            : StatCollector.translateToFallback(msg);
    }

    /**
     * Broadcasts an auto. translated, formatted encapsulated message to all players
     * @param server Server instance to broadcast to
     * @param msg String or language key to broadcast
     * @param parts Optional objects to add to formattable message
     */
    public static void broadcast(MinecraftServer server, String msg, Object... parts)
    {
        server.getConfigurationManager()
            .sendChatMsg( prepareText(msg, parts) );
    }

    /**
     * Sends an automatically translated, formatted & encapsulated message to a player
     * @param sender Target to send message to
     * @param msg String or language key to broadcast
     * @param parts Optional objects to add to formattable message
     */
    public static void chat(ICommandSender sender, String msg, Object... parts)
    {
        sender.addChatMessage(prepareText(msg, parts));
    }

    private static IChatComponent prepareText(String msg, Object... parts)
    {
        String translated = translate(msg);
        String formatted  = String.format(translated, parts);

        return new ChatComponentText(formatted);
    }

    public static void saveWorld(WorldServer world)
    {
        try
        {
            Boolean saveFlag  = world.levelSaving;
            world.levelSaving = false;
            world.saveAllChunks(true, null);
            world.levelSaving = saveFlag;
        }
        catch (MinecraftException e)
        {
            e.printStackTrace();
        }
    }
}