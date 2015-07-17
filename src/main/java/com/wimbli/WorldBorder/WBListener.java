package com.wimbli.WorldBorder;


import com.wimbli.WorldBorder.forge.Location;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;

public class WBListener
{
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlayerPearl(EnderTeleportEvent event)
	{
        if ( !(event.entityLiving instanceof EntityPlayerMP) )
            return;

		// if knockback is set to 0, simply return
		if (Config.getKnockBack() == 0.0)
			return;

		if (Config.isDebugMode())
			Config.log("Teleport cause: Enderpearl");

        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;

        Location target = new Location(event, player);
		Location newLoc = BorderCheck.checkPlayer(player, target, true, true);

		if (newLoc != null)
		{
			if( Config.getDenyEnderpearl() )
			{
				event.setCanceled(true);
				return;
			}

            event.targetX = newLoc.posX;
            event.targetY = newLoc.posY;
            event.targetZ = newLoc.posZ;
		}
	}

    // FORGE: Forge is missing a general teleport event. Revisit this later if security
    // holes arise
//	@SubscribeEvent(priority = EventPriority.LOWEST)
//	public void onPlayerTeleport(PlayerTeleportEvent event)
//	{
//		// if knockback is set to 0, simply return
//		if (Config.getKnockBack() == 0.0)
//			return;
//
//		if (Config.Debug())
//			Config.log("Teleport cause: " + event.getCause().toString());
//
//		Location newLoc = BorderCheckTask.checkPlayer(event.getPlayer(), event.getTo(), true, true);
//		if (newLoc != null)
//		{
//			if(event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && Config.getDenyEnderpearl())
//			{
//				event.setCancelled(true);
//				return;
//			}
//
//			event.setTo(newLoc);
//		}
//	}

    // FORGE: This is a very hacky attempt at emulating teleport event. May not work as
    // intended.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        // if knockback is set to 0, simply return
        if (Config.getKnockBack() == 0.0)
            return;

        if (Config.isDebugMode())
            Config.log("Teleport cause: Respawn");

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        Location target = new Location(event.player);
        Location newLoc = BorderCheck.checkPlayer(player, target, true, true);

        if (newLoc != null)
            event.player.setPositionAndUpdate(newLoc.posX, newLoc.posY, newLoc.posZ);
    }

    // FORGE: This is a very hacky attempt at emulating portal event. May not work as
    // intended.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlayerPortal(PlayerEvent.PlayerChangedDimensionEvent event)
	{
		// if knockback is set to 0, or portal redirection is disabled, simply return
		if (Config.getKnockBack() == 0.0 || !Config.doPortalRedirection())
			return;

        if (Config.isDebugMode())
            Config.log("Teleport cause: Dimension change");

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        Location target = new Location(event.player);
		Location newLoc = BorderCheck.checkPlayer(player, target, true, false);

		if (newLoc != null)
			event.player.setPositionAndUpdate(newLoc.posX, newLoc.posY, newLoc.posZ);
	}
}
