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
package inetsoft.mv.mr.internal;

import inetsoft.mv.fs.*;
import inetsoft.mv.mr.*;
import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * XJobStatus, the status of one job.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XJobStatus {
   /**
    * Constant indicates that job is started.
    */
   private static final int STARTED = 1;
   /**
    * Constant indicates that job is partially done.
    */
   private static final int STREAMING = 16;
   /**
    * Constant indicates that job is completed.
    */
   private static final int COMPLETED = 8;
   /**
    * Constant indicates that job is failed.
    */
   private static final int FAILED = 2 | COMPLETED;
   /**
    * Constant indicates that job is successful.
    */
   private static final int SUCCESSFUL = 4 | COMPLETED;

   /**
    * Create an instance of XJobStatus.
    * @param id the specified job id.
    */
   public XJobStatus(String id) {
      super();

      this.id = id;
      mmap = new HashMap<>();
   }

   /**
    * Create an instance of XJobStatus.
    * @param job the specified job.
    */
   public XJobStatus(XJob job) {
      super();

      this.id = job.getID();
      this.file = job.getXFile();
      this.job = job;
      this.started = System.currentTimeMillis();
      this.state = STARTED;
      this.reducer = job.createReducer();
      this.server = FSService.getServer();
      this.wperiod = FSService.getConfig().getJobTimeout();
      mmap = new HashMap<>();
   }

   /**
    * Start the job.
    */
   public void startJob() {
      // read lock of the file is already locked in pool
      XFile fobj = server.getFSystem().get(file);

      // collect all ids
      List<SBlock> blocks = fobj.getBlocks();

      if(blocks == null || blocks.size() <= 0) {
         throw new RuntimeException("File \"" + fobj.getName() +
            "\" contains no blocks.");
      }

      bset = new HashSet<>();
      dset = new HashSet<>();
      fmap = new HashMap<>();
      completed = false;

      for(SBlock block : blocks) {
         bset.add(block.getID());
      }

      if(job.isCancelled()) {
         return;
      }

      // dispatch map tasks
      for(int i = 0; i < blocks.size(); i++) {
         SBlock block = blocks.get(i);
         String bid = block.getID();
         // MV data grid is no longer used so it's always local mode (spark is used for
         // distributed mode). hardcode to localhost to avoid problem from multiple ip.

         String host = "localhost";

         if(job.isCancelled()) {
            return;
         }

         XMapTask task = job.createMapper(host, bid);
         task.setProperty("blockIndex", i + "");
         XMapStatus status = new XMapStatus(task);
         startTask(status);
      }
   }

   /**
    * Get the job id.
    */
   public String getID() {
      return id;
   }

   /**
    * Get the name of the XFile to be accessed.
    */
   public String getXFile() {
      return file;
   }

   /**
    * Get the reason why failed to execute the job.
    */
   public String getReason() {
      return reason;
   }

   /**
    * Get the state of the job.
    */
   public int getState() {
      return state;
   }

   /**
    * Check if the job is executed successfully.
    */
   private boolean isSuccessful() {
      return state == SUCCESSFUL;
   }

   /**
    * Check if the data is ready.
    */
   private boolean isReady() {
      return (state & COMPLETED) == COMPLETED || state == STREAMING;
   }

   /**
    * Check if the job is completed.
    */
   public boolean isCompleted() {
      return (state & COMPLETED) == COMPLETED;
   }

   /**
    * Mark the job to be completed.
    * @param all true if all blocks are added, false if still streaming.
    */
   private void complete(boolean success, boolean all, String reason) {
      if(!success) {
         // avoid excessive logging as this may be called many times.
         if(!warned) {
            LOG.warn("MV creation job failed: " + reason);
            warned = true;
         }

         if("true".equals(SreeEnv.getProperty("mv_debug"))) {
            LOG.debug("MV job detail: {}", job);
         }
      }

      waitlock.lock();

      try {
         if(job.isCancelled()) {
            return;
         }

         if(isCompleted()) {
            return;
         }

         if(success) {
            // catch exception to make sure that job is completed,
            // then the blocked thread could be notified properly
            try {
               if(reducer != null) {
                  reducer.complete(all || reducer.isFulfilled());
               }
            }
            catch(Throwable ex) {
               LOG.error("Failed to complete job", ex);
            }
         }

         // no matter success or not, there might be pending map task
         mlock.lock();
         List<XMapStatus> keys = new ArrayList<>(mmap.keySet());
         mlock.unlock();

         for(XMapStatus status : keys) {
            XMapTask task = status.getTask();

            if(!status.isCompleted()) {
               status.complete(false, "The map task is expired: " + task);
            }
         }

         if(!success) {
            this.state = FAILED;
         }
         else if(all) {
            this.state = SUCCESSFUL;
         }
         else {
            this.state = STREAMING;
         }

         this.reason = reason;

         // notify result consumer
         lockcnd.signalAll();
      }
      finally {
         waitlock.unlock();
      }
   }

   /**
    * Cancel the job.
    */
   public void cancel() {
      waitlock.lock();

      try {
         lockcnd.signalAll();

         if(reducer != null) {
            reducer.cancel();
         }

         if(job != null && !job.isCancelled()) {
            job.cancel();
         }
      }
      finally {
         waitlock.unlock();
      }
   }

   /**
    * Get the final result.
    */
   public Object getResult() {
      long now = System.currentTimeMillis();
      long elapsed = now - this.started;
      waitlock.lock();

      try {
         while(!isReady() && elapsed < wperiod) {
            try {
               lockcnd.await(10000, TimeUnit.MILLISECONDS);
            }
            catch(InterruptedException ex) {
               // ignore it
            }

            if(job.isCancelled()) {
               return null;
            }

            if(!isReady()) {
               now = System.currentTimeMillis();
               elapsed = now - started;
            }
         }
      }
      finally {
         waitlock.unlock();
      }

      if(!isSuccessful() && !isReady()) {
         // time out?
         if(!isCompleted()) {
            complete(false, false, "The job has expired (" + wperiod + "): " + job.getXFile());
         }

         throw new RuntimeException("Failed to execute job: " + job + ", reason: " + reason);
      }

      try {
         return reducer.getResult();
      }
      catch(RuntimeException ex) {
         String reason = mmap.keySet().stream()
            .map(s -> s.getReason())
            .filter(r -> r != null && !r.isEmpty())
            .findFirst().orElse(null);

         if(reason != null && !reason.isEmpty()) {
            throw new RuntimeException("Failed to execute job: " + reason);
         }
         else {
            throw ex;
         }
      }
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      return id.hashCode();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof XJobStatus)) {
         return false;
      }

      XJobStatus status = (XJobStatus) obj;
      return id.equals(status.id);
   }

   /**
    * Add one more map status.
    */
   private void startTask(XMapStatus status) {
      mlock.lock();

      try {
         if(mmap.containsKey(status)) {
            // if the map task is in process, ignore it
            return;
         }
      }
      finally {
         mlock.unlock();
      }

      if(job.isCancelled()) {
         return;
      }

      dset.add(status.getXBlock());
      mlock.lock();
      mmap.put(status, status);
      mlock.unlock();
      status.start();
      XMapTask task = status.getTask();
      XMapTaskPool.add(task);
   }

   /**
    * Add one map result to this reduce task.
    */
   public void addResult(XMapResult result) {
      if(job.isCancelled()) {
         return;
      }

      String id = result.getXBlock();
      waitlock.lock();

      try {
         // mark the block id as completed
         bset.remove(id);
         // mark the map task as completed
         String host = result.getHost();
         XMapStatus status = new XMapStatus(id, host);
         mlock.lock();
         status = mmap.get(status);
         mlock.unlock();
         status.complete(true, null);
         // add result to reducer
         reducer.add(result);
      }
      finally {
         waitlock.unlock();

         if(!completed && (bset.size() == 0 || job.isStreaming())) {
            completed = bset.size() == 0;
            complete(true, completed, null);
         }
      }
   }

   /**
    * Check if the entire task (all sub-tasks) are completed. For example,
    * if the number of rows in the result reaches max rows, no additional
    * tasks need to be executed.
    */
   public boolean isFulfilled() {
      return reducer.isFulfilled();
   }

   /**
    * Set the host to be failed for the given block.
    */
   private void setFailed(String bid, String host) {
      flock.lock();

      try {
         Set<String> fset = fmap.get(bid);

         if(fset == null) {
            fset = new HashSet<>();
            fmap.put(bid, fset);
         }

         fset.add(host);
      }
      finally {
         flock.unlock();
      }
   }

   /**
    * Add one map failure to this status.
    */
   public void addFailure(XMapFailure failure) {
      String id = failure.getXBlock();
      // mark the map task as completed
      String host = failure.getHost();
      XMapStatus status = new XMapStatus(id, host);
      mlock.lock();
      status = mmap.get(status);
      mlock.unlock();
      status.complete(false, failure.getReason());
      XMapTask task = status.getTask();
      String bid = task.getXBlock();
      setFailed(bid, host);

      complete(false, false,
               "Failed to dispatch job, could not create replacement " +
                  "task for failed task: " + job.getXFile() + " at block: " + bid);
   }

   /**
    * Update this job status. This method will be called periodically.
    * @return true if completed, false otherwise.
    */
   public boolean update() {
      if(isCompleted()) {
         return true;
      }

      long now = System.currentTimeMillis();

      // check if the job is expired
      if(now - started > wperiod) {
         complete(false, false, "The job has expired (" + wperiod + "): " + job.getXFile());
         return true;
      }

      mlock.lock();
      List<XMapStatus> keys = new ArrayList<>(mmap.keySet());
      mlock.unlock();
      XMapTask ftask = null; // failed task

      for(XMapStatus status : keys) {
         if(status.isCompleted()) {
            continue;
         }

         // expired status? try re-running task
         if(status.isExpired()) {
            XMapTask task = status.getTask();
            status.complete(false, "The map task is expired: " + task);
            setFailed(task.getXBlock(), task.getHost());
            XMapTask ntask = createMapper(task);

            if(ntask == null) {
               ftask = task;
               break;
            }

            XMapStatus nstatus = new XMapStatus(ntask);
            startTask(nstatus);
         }
      }

      // failed map task could not be re-dispatched? complete this job
      if(ftask != null) {
         complete(false, false,
                  "Failed to dispatch job, failed task could not be " +
                  "re-dispatched: " + job.getXFile() + " at block: " + ftask.getXBlock());
         return true;
      }

      return false;
   }

   /**
    * Create a new map task as replacement for the failed task.
    */
   private XMapTask createMapper(XMapTask task) {
         return null;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "JobStatus-" + job + "(state:" + state+ ",reason:"+ reason +
         ",map:" + mmap + ",pending:" + bset + ')';
   }

   private static final Logger LOG = LoggerFactory.getLogger(XJobStatus.class);
   private String id;
   private String file;
   private XJob job;
   private XServerNode server;
   private volatile int state;
   private String reason;
   private int wperiod; // max job period
   private long started; // started moment
   private Map<XMapStatus, XMapStatus> mmap; // map status map
   private Map<String, Set<String>> fmap;
   private Lock flock = new ReentrantLock(); // fmap lock
   private Lock mlock = new ReentrantLock(); // mmap lock
   private Set<String> bset; // block id set
   private Set<String> dset; // dispatched id set
   private boolean completed = false;
   private XReduceTask reducer; // reduce task
   private Lock waitlock = new ReentrantLock(); // wait lock
   private Condition lockcnd = waitlock.newCondition(); // lock condition
   private boolean warned = false;
}
