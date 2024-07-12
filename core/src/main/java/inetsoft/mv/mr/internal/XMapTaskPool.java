/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.mr.internal;

import inetsoft.mv.fs.*;
import inetsoft.mv.mr.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XMapTaskTool, the local pool to execute map tasks. This is designed for
 * a very powerful server with many CPUs.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XMapTaskPool {
   /**
    * Add one map task into this pool.
    */
   public static void add(XMapTask task) {
      if(pool == null) {
         plock.lock();

         try {
            if(pool == null) {
               pool = new XMapTaskPool();
            }
         }
         finally {
            plock.unlock();
         }
      }

      pool.add0(task);

      synchronized(maptasks) {
         maptasks.put(task, "OK");
      }
   }

   /**
    * Cancel all tasks with the specified ID.
    */
   public static void cancel(String id) {
      synchronized(maptasks) {
         for(XMapTask task : maptasks.keySet()) {
            if(task != null && Objects.equals(id, task.getID())) {
               task.cancel();
            }
         }
      }
   }

   /**
    * Create an instance of pool.
    */
   private XMapTaskPool() {
      super();

      FSService.getService();
      XDataNode node = FSService.getDataNode();
      sys = node.getBSystem();

      // default 4 threads per cpu
      tpool = new ThreadPool(
         "XMapTask", 4, "xmapTask.thread.count", 2);
      LOG.info(
         "Max number of map task processors: {}, {}", tpool.getSoftLimit(), tpool.getHardLimit());
   }

   /**
    * Add one map task into this pool.
    */
   private void add0(XMapTask task) {
      boolean streaming = "true".equals(task.getProperty("streaming"));

      // if streaming (detail), don't run the tasks in parallel since the first
      // completed task would make the data available. doing all in parallel
      // could create more contention on the memory.
      if(streaming) {
         streamlock.lock();

         try {
            Queue<XMapTask> tasks = stream_queues.get(task.getID());

            if(tasks != null) {
               tasks.add(task);
            }
            else {
               StreamHandler handler = new StreamHandler(task, sys,
                  ThreadContext.getContextPrincipal());
               tpool.add(handler);
            }
         }
         finally {
            streamlock.unlock();
         }
      }
      else {
         Handler handler = new Handler(task, sys);
         tpool.add(handler);
      }
   }

   /**
    * Handler, handles one map task at local data node.
    */
   private static final class Handler extends ThreadPool.AbstractContextRunnable {
      public Handler(XMapTask task, XBlockSystem sys) {
         super();

         this.task = task;
         this.sys = sys;
      }

      @Override
      public void run() {
         try {
            XMapResult result = task.run(sys);
            XJobPool.addResult(result);
         }
         catch(Throwable ex) {
            LOG.error("Failed to add result to pool", ex);
            XMapFailure failure = XMapFailure.create(task);
            failure.setReason(ex.getMessage());
            XJobPool.addFailure(failure);
         }
      }

      private final XMapTask task;
      private final XBlockSystem sys;
   }

   /**
    * Handler, handles one map task at a time at local data node.
    */
   private static final class StreamHandler implements Runnable {
      public StreamHandler(XMapTask task, XBlockSystem sys, Principal principal) {
         super();

         this.tasks.add(task);
         this.sys = sys;

         taskId = task.getID();
         stream_queues.put(taskId, tasks);
         user = principal;
      }

      @Override
      public void run() {
         streamlock.lock();

         try {
            ThreadContext.setContextPrincipal(user);

            while(!tasks.isEmpty()) {
               XMapTask task = tasks.remove();
               streamlock.unlock();

               try {
                  XMapResult result = task.run(sys);
                  boolean fulfilled = XJobPool.addResult(result);

                  // check if subsequence tasks need to be executed
                  if(fulfilled) {
                     break;
                  }
               }
               catch(Throwable ex) {
                  LOG.error("Failed to add result to stream", ex);
                  XMapFailure failure = XMapFailure.create(task);
                  failure.setReason(ex.toString());
                  XJobPool.addFailure(failure);
               }
               finally {
                  streamlock.lock();
               }
            }
         }
         finally {
            stream_queues.remove(taskId);
            streamlock.unlock();
         }
      }

      private final Queue<XMapTask> tasks = new ConcurrentLinkedQueue<>();
      private final XBlockSystem sys;
      private final Object taskId;
      private final Principal user;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XMapTaskPool.class);
   private static final Lock plock = new ReentrantLock();
   private static final Lock streamlock = new ReentrantLock();
   private static XMapTaskPool pool;
   private static WeakHashMap<XMapTask,Object> maptasks = new WeakHashMap<>();
   private static Map<Object, Queue<XMapTask>> stream_queues = new ConcurrentHashMap<>();

   private final ThreadPool tpool;
   private final XBlockSystem sys;
}
