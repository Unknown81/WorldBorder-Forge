package com.wimbli.WorldBorder.task;

import com.wimbli.WorldBorder.*;
import com.wimbli.WorldBorder.forge.Util;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.WorldServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tick handler that performs a trim task upon request
 */
public class WorldTrimTask
{
    // Per-task shortcut references
	private final WorldServer    world;
	private final WorldFileData  worldData;
	private final BorderData     border;
    private final ICommandSender requester;

    // Per-task state variables
    private List<CoordXZ> regionChunks = new ArrayList<>(1024);
    private List<CoordXZ> trimChunks   = new ArrayList<>(1024);

    private int     chunksPerRun = 1;
    private boolean readyToGo    = false;
    private boolean paused       = false;

	// Per-task state region progress tracking
	private int currentRegion = -1;  // region(file) we're at in regionFiles
    private int currentChunk  = 0;   // chunk we've reached in the current region (regionChunks)

    private int regionX = 0;  // X location value of the current region
    private int regionZ = 0;  // X location value of the current region
	private int counter = 0;

    // Per-task state for progress reporting
	private long lastReport   = Util.now();
	private int  reportTarget = 0;
	private int  reportTotal  = 0;

	private int reportTrimmedRegions = 0;
	private int reportTrimmedChunks  = 0;

	public WorldTrimTask(ICommandSender player, String worldName, int trimDistance, int chunksPerRun)
	{
		this.requester    = player;
		this.chunksPerRun = chunksPerRun;

		this.world = Util.getWorld(worldName);
        if (this.world == null)
            throw new IllegalArgumentException("World \"" + worldName + "\" not found!");

        this.border = (Config.Border(worldName) == null)
                ? null
                : Config.Border(worldName).copy();

        if (this.border == null)
            throw new IllegalStateException("No border found for world \"" + worldName + "\"!");

        this.worldData = WorldFileData.create(world, requester);
        if (worldData == null)
            throw new IllegalStateException("Could not create WorldFileData!");

		this.border.setRadiusX(border.getRadiusX() + trimDistance);
		this.border.setRadiusZ(border.getRadiusZ() + trimDistance);

		// each region file covers up to 1024 chunks; with all operations we might need to do, let's figure 3X that
		this.reportTarget = worldData.regionFileCount() * 3072;

		// queue up the first file
		if (!nextFile())
			return;

		this.readyToGo = true;
	}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
	{
        // Only run at start of tick
        if (event.phase == TickEvent.Phase.END)
            return;

		if (!readyToGo || paused)
			return;

		// this is set so it only does one iteration at a time, no matter how frequently the timer fires
		readyToGo = false;
		// and this is tracked to keep one iteration from dragging on too long and possibly choking the system if the user specified a really high frequency
		long loopStartTime = Util.now();

		counter = 0;
		while (counter <= chunksPerRun)
		{
			// in case the task has been paused while we're repeating...
			if (paused)
				return;

			long now = Util.now();

			// every 5 seconds or so, give basic progress report to let user know how it's going
			if (now > lastReport + 5000)
				reportProgress();

			// if this iteration has been running for 45ms (almost 1 tick) or more, stop to take a breather; shouldn't normally be possible with Trim, but just in case
			if (now > loopStartTime + 45)
			{
				readyToGo = true;
				return;
			}

			if (regionChunks.isEmpty())
				addCornerChunks();
			else if (currentChunk == 4)
			{	// determine if region is completely _inside_ border based on corner chunks
				if (trimChunks.isEmpty())
				{	// it is, so skip it and move on to next file
					counter += 4;
					nextFile();
					continue;
				}
				addEdgeChunks();
				addInnerChunks();
			}
			else if (currentChunk == 124 && trimChunks.size() == 124)
			{	// region is completely _outside_ border based on edge chunks, so delete file and move on to next
				counter += 16;
				trimChunks = regionChunks;
				unloadChunks();
				reportTrimmedRegions++;
				File regionFile = worldData.regionFile(currentRegion);

                try
                {
                    Files.delete(regionFile.toPath());
                }
                catch (Exception e)
                {
                    sendMessage("Error! Region file which is outside the border could not be deleted: "+regionFile.getName());
                    sendMessage("It may be that the world spawn chunks are in this region.");
                    sendMessage("Use /setworldspawn to set the spawn chunks elsewhere, restart the server and try again");
                    sendMessage("Region delete exception: " + e.getMessage().replaceAll("\n", ""));
                    wipeChunks();
                }

                // if DynMap is installed, re-render the trimmed region
                DynMapFeatures.renderRegion(world, new CoordXZ(regionX, regionZ));

				nextFile();
				continue;
			}
			else if (currentChunk == 1024)
			{	// last chunk of the region has been checked, time to wipe out whichever chunks are outside the border
				counter += 32;
				unloadChunks();
				wipeChunks();
				nextFile();
				continue;
			}

			// check whether chunk is inside the border or not, add it to the "trim" list if not
			CoordXZ chunk = regionChunks.get(currentChunk);
			if (!isChunkInsideBorder(chunk))
				trimChunks.add(chunk);

			currentChunk++;
			counter++;
		}

		reportTotal += counter;

		// ready for the next iteration to run
		readyToGo = true;
	}

	// Advance to the next region file. Returns true if successful, false if the next file isn't accessible for any reason
	private boolean nextFile()
	{
		reportTotal = currentRegion * 3072;
		currentRegion++;
		regionX = regionZ = currentChunk = 0;
		regionChunks = new ArrayList<CoordXZ>(1024);
		trimChunks = new ArrayList<CoordXZ>(1024);

		// have we already handled all region files?
		if (currentRegion >= worldData.regionFileCount())
		{	// hey, we're done
			paused = true;
			readyToGo = false;
			finish();
			return false;
		}

		counter += 16;

		// get the X and Z coordinates of the current region
		CoordXZ coord = worldData.regionFileCoordinates(currentRegion);
		if (coord == null)
			return false;

		regionX = coord.x;
		regionZ = coord.z;
		return true;
	}

	// add just the 4 corner chunks of the region; can determine if entire region is _inside_ the border 
	private void addCornerChunks()
	{
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX), CoordXZ.regionToChunk(regionZ)));
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX) + 31, CoordXZ.regionToChunk(regionZ)));
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX), CoordXZ.regionToChunk(regionZ) + 31));
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX) + 31, CoordXZ.regionToChunk(regionZ) + 31));
	}

	// add all chunks along the 4 edges of the region (minus the corners); can determine if entire region is _outside_ the border 
	private void addEdgeChunks()
	{
		int chunkX = 0, chunkZ;

		for (chunkZ = 1; chunkZ < 31; chunkZ++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		chunkX = 31;
		for (chunkZ = 1; chunkZ < 31; chunkZ++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		chunkZ = 0;
		for (chunkX = 1; chunkX < 31; chunkX++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		chunkZ = 31;
		for (chunkX = 1; chunkX < 31; chunkX++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		counter += 4;
	}

	// add the remaining interior chunks (after corners and edges)
	private void addInnerChunks()
	{
		for (int chunkX = 1; chunkX < 31; chunkX++)
		{
			for (int chunkZ = 1; chunkZ < 31; chunkZ++)
			{
				regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
			}
		}
		counter += 32;
	}

	// make sure chunks set to be trimmed are not currently loaded by the server
	private void unloadChunks()
	{
		for (CoordXZ unload : trimChunks)
		{
			if (world.theChunkProviderServer.chunkExists(unload.x, unload.z))
				world.theChunkProviderServer.unloadChunksIfNotNearSpawn(unload.x, unload.z);
		}
		counter += trimChunks.size();
	}

	// edit region file to wipe all chunk pointers for chunks outside the border
	private void wipeChunks()
	{
		File regionFile = worldData.regionFile(currentRegion);
		if (!regionFile.canWrite())
		{
			if (!regionFile.setWritable(true))
				throw new RuntimeException();

			if (!regionFile.canWrite())
			{
				sendMessage("Error! region file is locked and can't be trimmed: "+regionFile.getName());
				return;
			}
		}

		// since our stored chunk positions are based on world, we need to offset those to positions in the region file
		int offsetX = CoordXZ.regionToChunk(regionX);
		int offsetZ = CoordXZ.regionToChunk(regionZ);
		long wipePos = 0;
		int chunkCount = 0;

		try
		{
			RandomAccessFile unChunk = new RandomAccessFile(regionFile, "rwd");
			for (CoordXZ wipe : trimChunks)
			{
				// if the chunk pointer is empty (chunk doesn't technically exist), no need to wipe the already empty pointer
				if (!worldData.doesChunkExist(wipe.x, wipe.z))
					continue;

				// wipe this extraneous chunk's pointer... note that this method isn't perfect since the actual chunk data is left orphaned,
				// but Minecraft will overwrite the orphaned data sector if/when another chunk is created in the region, so it's not so bad
				wipePos = 4 * ((wipe.x - offsetX) + ((wipe.z - offsetZ) * 32));
				unChunk.seek(wipePos);
				unChunk.writeInt(0);
				chunkCount++;
			}
			unChunk.close();

			// if DynMap is installed, re-render the trimmed chunks
			// TODO: check if this now works
			DynMapFeatures.renderChunks(world, trimChunks);

			reportTrimmedChunks += chunkCount;
		}
		catch (FileNotFoundException ex)
		{
			sendMessage("Error! Could not open region file to wipe individual chunks: "+regionFile.getName());
		}
		catch (IOException ex)
		{
			sendMessage("Error! Could not modify region file to wipe individual chunks: "+regionFile.getName());
		}
		counter += trimChunks.size();
	}

	private boolean isChunkInsideBorder(CoordXZ chunk)
	{
		return border.insideBorder(CoordXZ.chunkToBlock(chunk.x) + 8, CoordXZ.chunkToBlock(chunk.z) + 8);
	}

	// for successful completion
	public void finish()
	{
		reportTotal = reportTarget;
		reportProgress();
		sendMessage("task successfully completed!");
		this.stop();
	}

	// for cancelling prematurely
	public void cancel()
	{
		this.stop();
	}

	// we're done, whether finished or cancelled
	private void stop()
	{
        readyToGo = false;
        FMLCommonHandler.instance().bus().unregister(this);
	}

    public void start()
    {
        FMLCommonHandler.instance().bus().register(this);
    }

	// handle pausing/unpausing the task
	public void pause()
	{
		pause(!this.paused);
	}
	public void pause(boolean pause)
	{
		this.paused = pause;
		if (pause)
			reportProgress();
	}
	public boolean isPaused()
	{
		return this.paused;
	}

	// let the user know how things are coming along
	private void reportProgress()
	{
		lastReport = Util.now();
		double perc = ((double)(reportTotal) / (double)reportTarget) * 100;
		sendMessage(reportTrimmedRegions + " entire region(s) and " + reportTrimmedChunks + " individual chunk(s) trimmed so far (" + Config.COORD_FORMAT.format(perc) + "% done" + ")");
	}

	// send a message to the server console/log and possibly to an in-game player
	private void sendMessage(String text)
	{
		Config.log("[Trim] " + text);
		if (requester != null)
			Util.chat(requester, "[Trim] " + text);
	}
}