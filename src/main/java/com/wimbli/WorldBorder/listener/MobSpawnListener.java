package com.wimbli.WorldBorder.listener;

import com.wimbli.WorldBorder.BorderData;
import com.wimbli.WorldBorder.Config;
import com.wimbli.WorldBorder.forge.Worlds;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

// TODO: This requires extensive profiling to ensure least server impact
public class MobSpawnListener
{
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(LivingSpawnEvent.CheckSpawn event)
    {
        if ( isInsideBorder(event) )
            return;

        // CheckSpawn uses event result instead of cancellation
        event.setResult(LivingSpawnEvent.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpecialSpawn(LivingSpawnEvent.SpecialSpawn event)
    {
        if ( isInsideBorder(event) )
            return;

        // SpecialSpawn uses event cancellation instead of result
        event.setCanceled(true);
    }

    private boolean isInsideBorder(LivingSpawnEvent event)
    {
        World      world  = event.entity.worldObj;
        BorderData border = Config.Border( Worlds.getWorldName(world) );

        return border == null
            || border.insideBorder( event.entity.posX, event.entity.posZ, Config.getShapeRound() );
    }
}
