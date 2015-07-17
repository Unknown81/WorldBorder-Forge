package com.wimbli.WorldBorder.forge;

import com.mojang.realmsclient.gui.ChatFormatting;
import com.wimbli.WorldBorder.WorldBorder;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.dedicated.DedicatedServer;
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
    /** Gets the internal name (e.g. DIM-1) of a given world */
    public static String getWorldName(World world)
    {
        if (world.provider.dimensionId == 0)
            return "world";
        else
            return world.provider.getSaveFolder();
    }

    /**
     * Case-sensitive search in global DimensionManager for a world of a given name
     * @param name Name of world to find
     * @return World if found, else null
     */
    public static WorldServer getWorld(String name)
    {
        if ( name == null || name.isEmpty() )
            throw new IllegalArgumentException("World name cannot be empty or null");

        for ( WorldServer world : DimensionManager.getWorlds() )
        {
            String saveFolder = world.provider.getSaveFolder();

            if (saveFolder == null)
            {   // Special case for dimension 0 (overworld)
                if ( WorldBorder.SERVER.getFolderName().equals(name) )
                    return world;
            }
            else if ( saveFolder.equals(name) )
                return world;
        }

        return null;
    }

    /** Safely saves a given world to disk */
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

    /** Gets the ID of the block type of the given block position in a world */
    public static int getBlockID(World world, int x, int y, int z)
    {
        return Block.getIdFromBlock( world.getBlock(x, y, z) );
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
     * Sends an automatically translated, formatted & encapsulated message to a player
     * @param sender Target to send message to
     * @param msg String or language key to broadcast
     * @param parts Optional objects to add to formattable message
     */
    public static void chat(ICommandSender sender, String msg, Object... parts)
    {
        sender.addChatMessage( prepareText(sender instanceof DedicatedServer, msg, parts) );
    }

    private static IChatComponent prepareText(boolean strip, String msg, Object... parts)
    {
        String translated = translate(msg);
        String formatted  = String.format(translated, parts);

        if (strip)
            formatted = ChatFormatting.stripFormatting(formatted);

        return new ChatComponentText(formatted);
    }

    /** Shortcut for java.lang.System.currentTimeMillis */
    public static long now()
    {
        return System.currentTimeMillis();
    }
}