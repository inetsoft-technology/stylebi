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
package inetsoft.mv.mr;

import inetsoft.mv.MVExecutionException;
import inetsoft.mv.fs.*;
import inetsoft.mv.mr.internal.XJobStatus;
import inetsoft.sree.SreeEnv;
import inetsoft.util.GroupedThread;
import inetsoft.util.TimedQueue;
import inetsoft.mv.MVJob;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * XJobPool, the pool manages executing jobs.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XJobPool extends GroupedThread {
   /**
    * Add one map result to the associated job.
    * @return true if the task is fulfilled.
    */
   public static boolean addResult(XMapResult result, String orgID) throws Exception {
      return getPool(orgID).addResult0(result);
   }

   /**
    * Add one map failure to the associated job.
    */
   public static void addFailure(XMapFailure failure, String orgID) {
      getPool(orgID).addFailure0(failure);
   }

   /**
    * Execute one XJob.
    */
   public static Object execute(XJob job, String orgID) throws Exception {
      try {
         return getPool(orgID).execute0(job);
      }
      catch(Exception ex) {
         throw new MVExecutionException(ex);
      }
   }

   public static void resetOrgCache(String orgID) {
      if(pool != null) {
         plock.lock();

         try {
            if(pool != null) {
               pool.fsystemOrgMap.remove(orgID);
            }
         }
         finally {
            plock.unlock();
         }
      }
   }

   /**
    * Cancel the job.
    */
   public static void cancel(String id, String orgID) {
      XJobStatus status = getPool(orgID).smap.get(id);

      if(status != null) {
         status.cancel();
      }
   }

   /**
    * Get the job pool.
    */
   private static final XJobPool getPool(String orgID) {
      if(pool == null) {
         plock.lock();

         try {
            if(pool == null) {
               pool = new XJobPool(orgID);
               pool.start(); // start sanity check thread
            }
         }
         finally {
            plock.unlock();
         }
      }

      return pool;
   }

   /**
    * Create an instance of XJobPool.
    */
   private XJobPool(String curOrg) {
      super();

      if(FSService.getServer(curOrg) == null) {
         throw new RuntimeException("This host is not server node!");
      }

      String periodValue = SreeEnv.getProperty("fs.job.check.period");
      period = periodValue != null ? Integer.parseInt(periodValue) : 500;
   }

   /**
    * Add one map result to the associated job.
    * @return true if the task is fulfilled.
    */
   private boolean addResult0(XMapResult result) throws Exception {
      String id = result.getID();
      XJobStatus status = smap.get(id);

      if(status == null) {
         lock.lock();

         try {
            status = smap.get(id);
         }
         finally {
            lock.unlock();
         }
      }

      if(status != null) {
         try {
            status.addResult(result);
         }
         catch(Exception ex) {
            throw new MVExecutionException(ex);
         }

         return status.isFulfilled();
      }

      return false;
   }

   /**
    * Add one map failure to the associated job.
    */
   private void addFailure0(XMapFailure failure) {
      String id = failure.getID();
      XJobStatus status = smap.get(id);

      if(status == null) {
         lock.lock();

         try {
            status = smap.get(id);
         }
         finally {
            lock.unlock();
         }
      }

      if(status != null) {
         status.addFailure(failure);
      }
   }

   /**
    * Execute one XJob.
    */
   private Object execute0(XJob job) throws Exception {
      String fname = job.getXFile();
      String orgID = ((MVJob) job).getOrgId();
      XFile fobj = getOrgFSystem(orgID).get(fname);

      if(fobj == null) {
         throw new Exception("File not found: " + fname);
      }

      XJobStatus status = null;

      try {
         status = new XJobStatus(job);
         lock.lock();

         try {
            smap.put(status.getID(), status);
         }
         finally {
            lockcnd.signalAll();
            lock.unlock();
         }

         // perform read-lock on the file
         fobj.getReadLock().lock();

         try {
            status.startJob();
         }
         finally {
            fobj.getReadLock().unlock();
         }

         return status.getResult();
      }
      finally {
         if(status != null) {
            removeJob(status);
         }
      }
   }

   /**
    * Remove the completed job.
    */
   private void removeJob(final XJobStatus status) {
      lock.lock();

      try {
         if(status.isCompleted()) {
            smap.remove(status.getID());
         }
         else {
            TimedQueue.add(new TimedQueue.TimedRunnable(2000) {
               @Override
               public void run() {
                  removeJob(status);
               }
            });
         }
      }
      finally {
         lock.unlock();
      }
   }
   
   /**
    * Perform sanity check on the executing jobs.
    */
   @Override
   protected void doRun() {
      outer:
      while(!isCancelled()) {
         // wait until at least one job is added
         try {
            lock.lock();

            while(smap.size() == 0) {
               try {
                  lockcnd.await(15000, TimeUnit.MILLISECONDS);
               }
               catch(InterruptedException ex) {
                  // ignore it
               }

               if(isCancelled()) {
                  break outer;
               }
            }
         }
         finally {
            lock.unlock();
         }

         // prepare job status array, so that we will not lock smap too long
         XJobStatus[] arr = new XJobStatus[0];
         lock.lock();

         try {
            arr = new XJobStatus[smap.size()];
            smap.values().toArray(arr);
         }
         finally {
            lock.unlock();
         }

         // update job status one by one
         for(int i = 0; i < arr.length; i++) {
            arr[i].update();
         }

         // sleep a while not to occupy too much resource
         try {
            Thread.sleep(period);
         }
         catch(InterruptedException ex) {
            // ignore it
         }
      }
   }

   private XFileSystem getOrgFSystem(String org) {
      plock.lock();

      try {
         return fsystemOrgMap.computeIfAbsent(org, k -> FSService.getServer(k).getFSystem());
      }
      finally {
         plock.unlock();
      }
   }

   private static final Lock plock = new ReentrantLock();
   private static XJobPool pool;
   private final int period;
   private final Map<String, XFileSystem> fsystemOrgMap = new HashMap<>();
   private final Map<String, XJobStatus> smap = new HashMap<>();
   private final Lock lock = new ReentrantLock();
   private final Condition lockcnd = lock.newCondition();
}
