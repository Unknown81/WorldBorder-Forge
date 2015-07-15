package com.wimbli.WorldBorder.forge;

import com.wimbli.WorldBorder.WorldBorder;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles the queueing and running of tasks at a later time in the main Forge thread.
 * Modelled by the same Scheduler in Bukkit without using any of its implementation.
 *
 * This uses the server tick event to execute tasks on the main thread. To reduce the
 * overhead of having a tick handler, it has a resolution of 20 ticks (one second).
 */
public class Scheduler
{
    private static final int RESOLUTION = 20;

    private List<ScheduledTask> tasks = new ArrayList<>();

    private int currentTask = -1;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (WorldBorder.server.getTickCounter() % 20 != 0)
            return;

        synchronized (this)
        {
            Iterator<ScheduledTask> iterator = tasks.iterator();
            while ( iterator.hasNext() )
            {
                ScheduledTask task = iterator.next();

                if (task.cancelled)
                {
                    iterator.remove();
                    continue;
                }

                if ( (task.ticksLeft -= RESOLUTION) <= 0 )
                {   // Firing scheduled task
                    currentTask = task.id;

                    try { task.runnable.run(); }
                    catch (Exception e)
                    {
                        WorldBorder.LOGGER.error( "Exception during task: " + e.getMessage() );
                    }

                    currentTask = -1;

                    // Check if repeat task, else remove one-off
                    if (task.periodTicks > 0)
                        task.ticksLeft = task.periodTicks;
                    else
                        iterator.remove();
                }
            }
        }
    }

    public synchronized boolean isCurrentlyRunning(int id)
    {
        return currentTask == id;
    }

    public synchronized boolean isQueued(int id)
    {
        for (ScheduledTask task : tasks)
            if (task.id == id)
                return true;

        return false;
    }

    public synchronized int scheduleSyncDelayedTask(Runnable task, long delayTicks)
    {
        return scheduleSyncRepeatingTask(task, delayTicks, 0);
    }

    public synchronized int scheduleSyncRepeatingTask(Runnable task, long delayTicks, long periodTicks)
    {
        ScheduledTask future = new ScheduledTask(task, delayTicks, periodTicks);
        tasks.add(future);

        return future.id;
    }

    public synchronized void cancelTask(int id)
    {
        ScheduledTask target = null;

        for (ScheduledTask task : tasks)
            if (task.id == id)
            {
                target = task;
                break;
            }

        if (target == null)
            throw new IllegalArgumentException(id + " is not a scheduled task ID");
        else
            target.cancelled = true;
    }

    private static class ScheduledTask
    {
        private static int lastId = 0;

        public final int      id;
        public final Runnable runnable;

        public long    ticksLeft;
        public long    periodTicks;
        public boolean cancelled;

        public ScheduledTask(Runnable runnable, long delay, long period)
        {
            this.id       = lastId;
            this.runnable = runnable;
            lastId++;

            if (lastId < 0)
                lastId = 0;

            ticksLeft   = delay;
            periodTicks = period;
        }
    }
}


