/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class supports running a task at specified time in a separate
 * thread.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TimedQueue {
   /**
    * Add a runnable to be run at a future time.
    * @param runnable the specified runnable.
    */
   public static TimedRunnable add(TimedRunnable runnable) {
      synchronized(queue) {
         queue.add(runnable);
         sorted = false;

         if(worker == null) {
            worker = new WorkerThread();
            worker.start();
         }

         queue.notifyAll();
         return runnable;
      }
   }

   /**
    * Replace a runnable to be run at a future time.
    * @param runnable the specified runnable.
    */
   public static void addSingleton(TimedRunnable runnable) {
      if(runnable == null) {
         return;
      }

      remove(runnable.getClass());
      add(runnable);
   }

   /**
    * Remove runnables.
    */
   public static void remove(Class cls) {
      synchronized(queue) {
         for(int i = queue.size() - 1; i >= 0; i--) {
            Object obj = queue.get(i);

            if(obj.getClass().equals(cls)) {
               queue.remove(i);
            }
         }

         queue.notifyAll();
      }
   }

   /**
    * Remove a runnable.
    * @param runnable the specified runnable.
    */
   public static void remove(TimedRunnable runnable) {
      synchronized(queue) {
         queue.remove(runnable);
         queue.notifyAll();
      }
   }

   /**
    * Get the status of timed queue.
    */
   public static String getStatus() {
      return "TimedQueue[" + queue + "]";
   }

   /**
    * Scheduled task.
    */
   public abstract static class TimedRunnable implements Runnable, Comparable {
      // Create a default runnable. Should call after() to set the timer.
      public TimedRunnable() {
      }

      /**
       * Create a timed runnable that will be run after the specified interval.
       * @param time interval in milliseconds.
       */
      public TimedRunnable(long time) {
         after(time);
      }

      /**
       * Get the priority.
       * @return the priority of this timed runnable.
       */
      public int getPriority() {
         return Thread.NORM_PRIORITY;
      }

      /**
       * Check if is recurring.
       * @return true if is recurring, false otherwise.
       */
      public boolean isRecurring() {
         return false;
      }

      /**
       * Set the scheduled time to be the interval after current time.
       * @param time interval in milliseconds.
       */
      public TimedRunnable after(long time) {
         if(time < 0) {
            throw new RuntimeException("invalid time value found: " + time);
         }

         if(isRecurring()) {
            this.interval = time;
         }

         this.time = System.currentTimeMillis() + time;
         sorted = false;
         return this;
      }

      /**
       * Get the scheduled run time.
       */
      public long getScheduledTime() {
         return time;
      }

      /**
       * Compare two timed runnable based on scheduled time.
       */
      @Override
      public int compareTo(Object o) {
         try {
            return (int) (time - ((TimedRunnable) o).time);
         }
         catch(Exception ex) {
            return -1;
         }
      }

      private long time;
      private long interval;
   }

   // thread that process the queue
   private static class WorkerThread extends GroupedThread {
      {
         setDaemon(true);
         setName("TimedQueue");
      }

      @Override
      protected void doRun() {
         while(!isCancelled()) {
            TimedRunnable task = null;
            long nap = Integer.MAX_VALUE;

            synchronized(queue) {
               while(queue.isEmpty() && !isCancelled()) {
                  try {
                     queue.wait(1000);
                  }
                  catch(Exception ignore) {
                  }
               }

               if(isCancelled()) {
                  break;
               }

               if(!sorted) {
                  Collections.sort(queue);
                  sorted = true;
               }

               long now = System.currentTimeMillis();
               TimedRunnable runnable = queue.get(0);
               long scheduled = runnable.getScheduledTime();

               if(scheduled <= now) {
                  task = runnable;

                  if(runnable.isRecurring()) {
                     runnable.after(runnable.interval);
                  }
                  else {
                     queue.remove(0);
                  }
               }
               else {
                  nap = Math.min(nap, scheduled - now);
               }

               queue.notifyAll();
            }

            int priority = getPriority();

            try {
               if(task != null) {
                  setPriority(task.getPriority());
                  task.run();
               }
               else {
                  synchronized(queue) {
                     try {
                        queue.wait(nap);
                     }
                     catch(InterruptedException ex) {
                        break;
                     }
                  }
               }
            }
            catch(Throwable ex) {
               LOG.error("Failed to execute a timed queue task: " + task, ex);
            }
            finally {
               setPriority(priority);
            }
         }
      }
   }

   private static WorkerThread worker; // worker thread
   private static boolean sorted = false;

   private static final List<TimedRunnable> queue = new ArrayList<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(TimedQueue.class);
}
