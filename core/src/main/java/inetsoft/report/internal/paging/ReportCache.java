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
package inetsoft.report.internal.paging;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.license.ClaimedLicenseListener;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * This class handles the report caching. The caching is the result of three
 * main cooperating threads: paging thread, swapper thread and the consumer
 * thread that calls ReportCache methods.
 * Paging thread: create StylePages and put them into page group(correspond
 *  to a swapping file) for swapping.
 * Swapper thread: swaps StylePages when memory condition is poor. It is
 *  whether or not memory condition is poor.
 * Consumer thread: consumes StylePages, and remove them when StylePages are
 *  no longer used.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ReportCache implements Serializable {
   /**
    * Pending process status.
    */
   public static final int PENDING = 1;
   /**
    * Executing process status.
    */
   public static final int EXECUTING = 2;
   /**
    * Paging process status.
    */
   public static final int PAGING = 4;
   /**
    * Process completed status.
    */
   public static final int COMPLETED = 8;
   /**
    * Process failed status.
    */
   public static final int FAILED = -1;

   /**
    * Create a ReportCache object.
    */
   public ReportCache() {
      this(0);
   }

   /**
    * Create a ReportCache object with max paging processor count.
    */
   public ReportCache(int maxgen) {
      FileSystemService fileSystemService = FileSystemService.getInstance();

      try {
         cdir = fileSystemService.getFile(
            Tool.convertUserFileName(fileSystemService.getCacheDirectory()));
      }
      catch(IOException ignore) {
      }

      cache_workset = (byte) Integer.parseInt(
         SreeEnv.getProperty("replet.cache.workset"));

      int scount = calculateMaxActiveThreads();
      int hcount = calculateMaxThreads();

      threadpool = new ThreadPool(scount, hcount, "ReportCache");
      LOG.debug("Maximum number of report execution threads: " + scount + "," + hcount);
   }

   /**
    * Resize the thread pool based on the current license key.
    */
   public void resize() {
      int scount = calculateMaxActiveThreads();
      int hcount = calculateMaxThreads();
      threadpool.resize(scount, hcount);
   }

   /**
    * Calculates the number of maximum active threads allowed within the Report
    * Cache.
    *
    * @return  maximum number of active threads in the report cache
    */
   public static int calculateMaxActiveThreads() {
      // default 4 threads per cpu
      int scount = Tool.getAvailableCPUCores() * 4;
      String val = SreeEnv.getProperty("replet.cache.concurrency");

      if(val != null) {
         int temp = Integer.parseInt(val);
         scount = temp > 0 ? temp : scount;
      }

      scount = Math.max(scount, 1);
      return scount;
   }

   /**
    * Calculates the number of threads (active and not active) within the Report
    * Cache.
    *
    * @return  maximum number of threads
    */
   public static int calculateMaxThreads() {
      int hcount = 0;
      int scount = calculateMaxActiveThreads();
      String val = SreeEnv.getProperty("reportCache.thread.count",
                                       (scount * 2) + "");
      try {
         hcount = Integer.parseInt(val);
      }
      catch(Throwable exc) {
         LOG.warn("Invalid value for the maximum number of " +
                     "report execution threads property " +
                     "(reportCache.thread.count): " + val, exc);
      }

      return hcount;
   }

   /**
    * Get the cache directory.
    */
   public File getCacheDirectory() {
      return cdir;
   }

   /**
    * Add a report (StylePage objects) to the cache. If page streaming is on,
    * the pages are added to the cache in a separate thread and the function
    * returns before it is completed.
    * @param id the specified replet id.
    * @param pages the specidied style pages.
    * @param interactive true if the report sheet is created for interactive.
    * @param batch if of the report must wait for the entire report to be
    * processed.
    * @param oneoff true if the generated style pages will only be used once.
    */
   public Runnable put(Object id, Enumeration<?> pages, boolean interactive,
                       boolean batch, boolean oneoff) {
      return put0(id, null, pages, Thread.NORM_PRIORITY, interactive, batch,
                  oneoff);
   }

   /**
    * Add a report (StylePage objects) to the cache. If page streaming is on,
    * the pages are added to the cache in a separate thread and the function
    * returns before it is completed.
    * @param id the specified replet id.
    * @param report the specified report sheet.
    * @param pages the specidied style pages.
    * @param priority the specified thread priority.
    * @param interactive true if the report sheet is created for interactive.
    * @param batch if of the report must wait for the entire report to be
    * processed.
    * @param oneoff true if the generated style pages will only be used once.
    */
   private Runnable put0(Object id, ReportSheet report, Enumeration<?> pages,
                         int priority, boolean interactive, boolean batch,
                         boolean oneoff)
   {
      fireProcessEvent(id, PENDING);
      PagingProcessor0 proc = null;

      if(pnmap.get(id) != null || threadmap.get(id) != null) {
         remove0(id, true, report);
      }

      try {
         proclock.lock();
         proc = new PagingProcessor0(id, report, pages, priority,
                                     interactive, batch, oneoff);
         procs.put(id, ProcState.RUNNING);

         try {
            threadlock.lock();
            threadmap.put(id, proc);

            if(proc instanceof ThreadPool.ContextRunnable) {
               ((ThreadPool.ContextRunnable) proc)
                  .addRecord("report:" + ProfileUtils.getReportSheetName(report));
            }
         }
         finally {
            threadcond.signalAll();
            threadlock.unlock();
         }
      }
      finally {
         proccond.signalAll();
         proclock.unlock();
      }

      pendings.add(id);
      threadpool.add(proc);
      return proc;
   }

   /**
    * Remove a report from the cache.
    * @param id the specified replet id.
    */
   public void remove(Object id) {
      remove(id, true);
   }

   /**
    * Remove a report from the cache.
    * @param id the specified replet id.
    * @param removal true to dispose style pages.
    */
   public void remove(Object id, boolean removal) {
      remove0(id, removal, null);
   }

   /**
    * Remove a report from the cache.
    * @param id the specified replet id.
    * @param removal true to dispose style pages.
    * @param put if it's called from put, the report to be added.
    */
   private void remove0(Object id, boolean removal, ReportSheet put) {
      PagingProcessor0 gen = threadmap.get(id);
      emap.remove(id);
      proclock.lock();

      try {
         if(put == null) {
            listenlock.lock();

            try {
               listenmap.remove(id);
            }
            finally {
               listenlock.unlock();
            }
         }

         pendings.remove(id);
         procs.replace(id, ProcState.CANCELLED);

         // @by billh, the generator might be queued in thread pool, to avoid
         // its being executed, we MUST try removing it from thread pool
         if(gen != null) {
            boolean removed = threadpool.remove(gen);

            if(removed) {
               gen.completed = true;
            }
         }

         pglock.lock();

         try {
            pgcond.signalAll();
         }
         finally {
            pglock.unlock();
         }

         pnlock.lock();

         try {
            pncond.signalAll();
         }
         finally {
            pnlock.unlock();
         }

         proccond.signalAll();
      }
      finally {
         proclock.unlock();
      }

      try {
         // cancel any report generation if not finished
         if(gen != null && gen.getReport() != null && gen.getReport() != put) {
            XSessionManager.getSessionManager().cancel(gen.getReport());
            gen.cancelled = true;
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to cancel report execution", ex);
      }

      // wait until the paging process is stopped
      if(gen != null && !gen.isCompleted()) {
         try {
            gen.join(true);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      Integer cnt = pnmap.get(id);

      if(cnt != null) {
         String lname = null;

         try {
            for(int i = 0; i < Math.abs(cnt.intValue()); i++) {
               String fname = getSwapFile(id, i);

               if(fname.equals(lname)) {
                  continue;
               }

               lname = fname;

               // @by yanie: add lock to prevent concurrent modification
               // to pgmap
               PageGroup pages = null;
               pglock.lock();

               try {
                  pages = pgmap.remove(fname);
               }
               finally {
                  pglock.unlock();
               }

               if(pages != null) {
                  pages.dispose(removal);
               }
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to dispose of report pages", ex);
         }
      }

      threadlock.lock();

      try {
         threadmap.remove(id);
         threadcond.signalAll();
      }
      finally {
         threadlock.unlock();
      }

      pnlock.lock();

      try {
         pnmap.remove(id);
         dataTsMap.remove(id);
         pncond.signalAll();
      }
      finally {
         pnlock.unlock();
      }
   }

   /**
    * Get the specified page of the report.
    */
   public StylePage getPage(Object id, int n) {
      return getPageResult(id, n).getPage();
   }

   /**
    * Get the specified page of the report.
    */
   public StylePageResult getPageResult(Object id, int n) {
      PageGroup group = null;
      String fname = getSwapFile(id, n);
      pglock.lock();
      boolean returnCachedFirstPage = n == 0 && page_streaming && min_streaming < 2;
      boolean cancelled = false;

      try {
         // first page: wait 500ms, else 10s
         int ms = returnCachedFirstPage ? 500 : 10000;
         ProcState procState = null;

         while((group = pgmap.get(fname)) == null &&
            (procState = procs.get(id)) == ProcState.RUNNING)
         {
            // @by davidd feature1308841945990, Provide special handling
            // for first page requests to improve response time.
            if(returnCachedFirstPage) {
               PagingProcessor0 proc = getProcessor(id);

               if(proc != null && proc.firstPage != null) {
                  return new StylePageResult(proc.firstPage, false);
               }
            }

           // try {
               pgcond.await(ms, TimeUnit.MILLISECONDS);
//            }
//            catch(InterruptedException exc) {
//            }
         }

         cancelled = procState == ProcState.CANCELLED;
      }
      catch(Exception ex) {
         LOG.error("Failed to get style page", ex);
      }
      finally {
         pglock.unlock();
      }

      try {
         // no page group found
         if(group == null) {
            if(!cancelled && !pnmap.containsKey(id)) {
               LOG.error("Report does not exist: {} {}", id, fname);
            }

            return new StylePageResult(null, cancelled);
         }

         if(n > 0 && (n % cache_workset == 0)) {
            boolean oneoff = group.isOneOff();

            // if style pages are only used once, dispose them in time to
            // release resource in time
            if(oneoff) {
               String pfile = getSwapFile(id, n - cache_workset);
               PageGroup pgroup = null;
               // @by yanie: add lock to prevent concurrent modification to pgmap
               pglock.lock();

               try {
                  pgroup = pgmap.remove(pfile);
               }
               finally {
                  pglock.unlock();
               }

               if(pgroup != null) {
                  pgroup.dispose();
               }
            }

            if(oneoff || group.isSequential()) {
               PagingProcessor0 proc = threadmap.get(id);

               // update the accessed page index in paging processor, so that
               // paging processor can prepare more for them
               if(proc != null) {
                  proc.access(n);
               }
            }
         }

         StylePage page = null;

         try {
            ProcState procState = null;

            // if page group is found with null page, wait until page isn't null
            // or process is over.
            while((page = group.getPage(n % cache_workset, true)) == null &&
               (procState = procs.get(id)) == ProcState.RUNNING)
            {
               proclock.lock();

               try {
                  proccond.await(10, TimeUnit.SECONDS);
               }
               catch(InterruptedException exc) {
               }
               finally {
                  proclock.unlock();
               }
            }

            cancelled = procState == ProcState.CANCELLED || page == null;
         }
         catch(Exception ex) {
            LOG.error("An error occurred while waiting for page to be available: {}", id, ex);
         }

         return new StylePageResult(page, cancelled);
      }
      catch(Exception ex) {
         LOG.error("Failed to get style page", ex);
      }

      return new StylePageResult(null, false);
   }

   /**
    * Get the number of pages of the specified report.
    * @return number of pages. Return a negative number of the available
    * pages if the report is not finished processing.
    */
   public int getPageCount(Object id) {
      return getPageCount(id, true);
   }

   /**
    * Get the number of pages of the specified report.
    * @param id the id of the report.
    * @param wait if wait is true, wait until at least one page is generated.
    * @return number of pages. Return a negative number of the available
    * pages if the report is not finished processing.
    */
   public int getPageCount(Object id, boolean wait) {
      Integer pn = null;
      boolean pending = false;
      boolean finish = false;
      pnlock.lock();

      try {
         while((pn = pnmap.get(id)) == null && (pending = procs.get(id) == ProcState.RUNNING) &&
            !finish && wait)
         {
            if(page_streaming) {
               try {
                  pncond.await(300, TimeUnit.MILLISECONDS);
               }
               catch(InterruptedException exc) {
                  // do nothing
               }

               finish = true;
            }
            else {
               try {
                  pncond.await(10, TimeUnit.SECONDS);
               }
               catch(InterruptedException exc) {
                  // do nothing
               }
            }
         }
      }
      finally {
         pnlock.unlock();
      }

      // should return 0 if pn is null(5.1). -1 indicates one page.
      // returning 0 is considered as non-existent report, so should return -1 if pending.
      return (pn == null) ? (pending ? -1 : 0) : pn;
   }

   /**
    * Check if the report is already in the cache.
    */
   public boolean contains(Object id) {
      return pnmap.get(id) != null;
   }

   /**
    * Get the PagingProcessor which is executing or paging the replet.
    * @param id the replet id.
    * @return the PagingProcessor0
    */
   private PagingProcessor0 getProcessor(Object id) {
      threadlock.lock();

      try {
         return threadmap.get(id);
      }
      finally {
         threadlock.unlock();
      }
   }

   @Override
   public void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * This method must be called to destroy the threads in this cache if the
    * cache is no longer needed.
    */
   public void dispose() {
      if(!disposed) {
         disposed = true;
         threadpool.dispose();
      }
   }

   /**
    * Get the swap file name of a report page.
    * @param id report id.
    * @param n page number.
    */
   protected String getSwapFile(Object id, int n) {
      return Tool.toFileName(id.toString()) + "." + (n / cache_workset);
   }

   /**
    * Get page processor.
    * @param id report id.
    * @return page processor.
    */
   protected PageProcessor getPageProcessor(Object id) {
      return new PageProcessor(id);
   }

   /**
    * Get all process listeners of a report id.
    * @param id the specified report id.
    * @return all listeners of the specified report id.
    */
   public Set<ActionListener> getProcessListeners(Object id) {
      listenlock.lock();

      try {
         Set<ActionListener> set = listenmap.get(id);

         if(set == null) {
            return new HashSet<>();
         }

         return new HashSet<>(set);
      }
      finally {
         listenlock.unlock();
      }
   }

   /**
    * Get the process status of a report.
    * @param id the specified report id.
    */
   public int getProcessStatus(Object id) {
      proclock.lock();

      try {
         if(procs.get(id) != ProcState.RUNNING) {
            return COMPLETED;
         }
      }
      finally {
         proclock.unlock();
      }

      pnlock.lock();

      try {
         if(pnmap.containsKey(id)) {
            return PAGING;
         }
      }
      finally {
         pnlock.unlock();
      }

      if(!pendings.contains(id)) {
         return EXECUTING;
      }
      else {
         return PENDING;
      }
   }

   /**
    * Fire process event.
    * @param id the specified report id.
    * @param status the specified process status.
    */
   private void fireProcessEvent(Object id, int status) {
      Set<ActionListener> listeners = getProcessListeners(id);

      synchronized(allListeners) {
         listeners.addAll(allListeners);
      }

      for(ActionListener listener : listeners) {
         try {
            ActionEvent event = new ActionEvent(this, status, id.toString());
            listener.actionPerformed(event);
         }
         catch(Exception ex) {
            LOG.warn("Failed to handle process event", ex);
         }
      }
   }

   /*
    * PageProcessor, it is ignored in Pro, but called in EE for TOC.
    */
   protected class PageProcessor {
      /**
       * A page processed is created for every new report.
       */
      public PageProcessor(Object id) {
      }

      /**
       * This method is called on every page of a report as it's generated.
       */
      public void process(StylePage page, int pn) {
      }

      /**
       * This method is called after all pages in a report has been processed.
       * @param lastGroup last page group.
       */
      public void complete(PageGroup lastGroup) {
      }
   }

   /**
    * Paging processor implementation.
    */
   private class PagingProcessor0
      extends ThreadPool.AbstractPoolRunnable implements PagingProcessor
   {
      public PagingProcessor0(Object id, ReportSheet report,
                              Enumeration<?> pages, int priority,
                              boolean interactive, boolean batch,
                              boolean oneoff)
      {
         this.id = id;
         this.report = report;
         this.pages = pages;
         this.priority = priority;
         this.interactive = interactive;
         this.batchwait = batch;
         this.ioneoff = oneoff;
         this.oneoff = oneoff;

         // @by larryl, optimization, share the properties across all pages
         // build the page properties
         if(pages == null) {
            addProperty("report.title");
            addProperty("report.subject");
            addProperty("report.author");
            addProperty("report.keywords");
            addProperty("report.comments");
            addProperty("report.created");
            addProperty("report.modified");
            addProperty("report.modifiedUser");
            addProperty("pdf.password.owner");
            addProperty("pdf.password.user");
            addProperty("pdf.permission.print");
            addProperty("pdf.permission.copy");
            addProperty("pdf.permission.change");
            addProperty("pdf.permission.add");
         }
      }

      /**
       * Get the priority of the thread.
       */
      @Override
      public int getPriority() {
         return priority;
      }

      /**
       * Check if the generated style pages will only be used once.
       */
      @Override
      public boolean isInitialOneOff() {
         return !interactive && ioneoff;
      }

      /**
       * Check if the generated style pages will only be used once, and need not
       * been swapped.
       */
      @Override
      public boolean isOneOff() {
         return !interactive && !batchwait && oneoff;
      }

      /**
       * Check if the report sheet is created for interactive.
       */
      @Override
      public boolean isInteractive() {
         return interactive;
      }

      /**
       * Check if is sequential.
       */
      public boolean isSequential() {
         return !interactive && !batchwait;
      }

      /**
       * Check if the report must wait for the entire report to be processed.
       */
      @Override
      public boolean isBatchWaiting() {
         return batchwait;
      }

      /**
       * Get the current available page count.
       */
      @Override
      public int getPageCount() {
         return pgcnt;
      }

      /**
       * Check if the paging process is completed.
       */
      @Override
      public boolean isCompleted() {
         return completed;
      }

      /**
       * Wait for the runnable to finish.
       */
      @Override
      public void join(boolean internal) throws Exception {
         if(!internal && isSequential()) {
            LOG.warn(
               "Join is not supported for one-off report " + id +
               ", for style pages need to be consumed in time!");
            return;
         }

         threadlock.lock();

         try {
            while(threadmap.containsKey(id)) {
               try {
                  threadcond.await(10, TimeUnit.SECONDS);
               }
               catch(InterruptedException exc) {
                  // ignore it
               }
            }
         }
         finally {
            threadlock.unlock();
         }
      }

      /**
       * Get context id, used for log(log_pending), most case, it is the
       * report id or viewsheet id.
       */
      @Override
      public String getContextID() {
         return id == null ? null : id.toString();
      }

      @Override
      public void run() {
         try {
            ThreadContext.setLocale(null);

            if(report != null) {
               final ReportSheet locked = report;

               synchronized(locked.getPagingLock()) {
                  run0();
               }
            }
            else {
               run0();
            }
         }
         catch(Throwable t) {
            LOG.error("Failed to execute report: " + id, t);
         }
      }

      @Override
      public Throwable getError() {
         return error;
      }

      /**
       * Set the new accessed page index.
       */
      private void access(int aidx) {
         if(this.aidx < aidx) {
            this.aidx = aidx;
         }
      }

      private void run0() {
         StylePage pg;
         boolean singlePage = false;
         ArrayList<PageGroup> groups = new ArrayList<>();
         int lpgcnt = 0;

         try {
            pendings.remove(id);
            fireProcessEvent(id, EXECUTING);
            emap.addObject(id);
            PageProcessor proc = getPageProcessor(id);
            Enumeration<?> pages = this.pages;
            this.pages = null;

            fireProcessEvent(id, PAGING);
            emap.addObject(id);

            if(report != null) {
               String prop = report.getProperty("singlePage");
               singlePage = prop != null && prop.equals("true");
               pages = ReportGenerator.generate(report, !singlePage, false);
            }

            LOG.debug("Start generating pages: " + id);
            PageGroup group = null; // page group for swapping
            String fname = null; // swap file name
            String gname = null; // page group name
            File swapfile = null; // swap file if need to swap

            // set page number to 1 if the report is a single page report
            if(report != null && singlePage) {
               pnlock.lock();

               try {
                  pnmap.put(id, 1);
                  pncond.signalAll();
               }
               finally {
                  pnlock.unlock();
               }
            }

            int offset = 50;

            // generate the pages, make sure it's not deleted yet
            while(pages.hasMoreElements() && procs.get(id) == ProcState.RUNNING) {
               // @by jasons, moved to beginning of loop so that a page group
               // can be placed into service as soon as it is complete without
               // waiting for the next page to be generated.
               // start a new page group
               boolean newgroup = (pgcnt % cache_workset) == 0;
               fname = getSwapFile(id, pgcnt);

               if(newgroup) {
                  if(!isOneOff()) {
                     swapfile = FileSystemService.getInstance().getFile(cdir, fname);
                  }

                  if(group != null) {
                     group.complete();
                     pglock.lock();

                     try {
                        pgmap.put(gname, group);
                        lpgcnt = pgcnt;
                        pgcond.signalAll();
                     }
                     finally {
                        pglock.unlock();
                     }
                  }

                  group = new PageGroup(pgcnt, cache_workset, swapfile,
                                        this, (offset--) * 20);
                  offset = offset < 0 ? 0 : offset;
                  gname = fname;
                  groups.add(group);
               }

               int times = 0;

               // check whether should wait for a while not to generate or
               // accumulate too many style pages for sequential report sheet
               while(isSequential() && (pgcnt > aidx + 10 * cache_workset) &&
                     (times++ < 600) && procs.get(id) == ProcState.RUNNING) {
                  try {
                     Thread.sleep(100);
                  }
                  catch(InterruptedException ex) {
                     // ignore it
                  }
               }

               // if pages are not consumed in time during the waiting
               // period, something is wrong outside, here break the
               // paing process, and give callers a warning
               if(isSequential() && (pgcnt > aidx + 10 * cache_workset) &&
                  procs.get(id) == ProcState.RUNNING)
               {
                  LOG.warn(
                     "The one-off style pages for " + id +
                     " are not consumed in time. Break the paging process!");
                  break;
               }

               XSwapper.getSwapper().waitForMemory();

               pg = (StylePage) pages.nextElement();
               pg.setProperties(props);

               if(procs.get(id) != ProcState.RUNNING) {
                  break;
               }

               // @by davidd feature1308841945990,Cache 1st page for performance
               if(pgcnt == 0) {
                  firstPage = pg;
               }

               // refix batch wait
               if(!batchwait && pg.isBatchWaiting()) {
                  batchwait = true;
               }

               // make page available
               proc.process(pg, pgcnt);

               group.addPage(pg);
               pgcnt++;

               if(interactive) {
                  // don't do streaming until cross minimum streaming pages
                  if(page_streaming && pgcnt >= min_streaming) {
                     // set page number map. For partial generated, use negative
                     // number. Interactive report showing partial count is
                     // OK because "next page" will keep update the count.
                     pnlock.lock();

                     try {
                        pnmap.put(id, -pgcnt);
                        pncond.signalAll();
                     }
                     finally {
                        pnlock.unlock();
                     }
                  }
               }
               else {
                  if(!batchwait) {
                     pnlock.lock();

                     try {
                        pnmap.put(id, -pgcnt);
                        pncond.signalAll();
                     }
                     finally {
                        pnlock.unlock();
                     }
                  }
               }
            }

            // @by larryl, complete must be called before group being swapped
            if(group != null) {
               proc.complete(group);
               group.complete();
               pglock.lock();

               // add the last group
               try {
                  pgmap.put(gname, group);
                  pgcond.signalAll();
               }
               finally {
                  pglock.unlock();
               }
            }

            // set page number map
            pnlock.lock();

            try {
               pnmap.put(id, pgcnt);
               pncond.signalAll();
            }
            finally {
               pnlock.unlock();
            }

            LOG.debug("Generating pages[" + id + "]: " + pgcnt);
         }
         catch(Throwable e) {
            error = e;

            // ignore the exception if the report is cancelled
            if(procs.get(id) == ProcState.RUNNING && !cancelled) {
               LOG.error("Failed to generate report pages: " + id, e);
            }

            pnlock.lock();

            try {
               if(pnmap.get(id) == null) {
                  pnmap.put(id, lpgcnt);
               }

               pncond.signalAll();
            }
            finally {
               pnlock.unlock();
            }
         }
         finally {
            try {
               if(report != null) {
                  // notify report that the paging process is done,
                  // so temporary resource can be freed
                  report.complete();
                  ObjectCache.clear();
                  report = null;
               }
            }
            catch(Throwable ex) {
               LOG.warn("Failed to dispose of report: " + id, ex);
            }

            proclock.lock();

            try {
               procs.computeIfPresent(id, (k, v) -> v == ProcState.RUNNING ? null : v);
               threadlock.lock();

               try {
                  threadmap.remove(id);
                  threadcond.signalAll();
               }
               finally {
                  threadlock.unlock();
               }

               proccond.signalAll();
               pnlock.lock();

               try {
                  pncond.signalAll();
               }
               finally {
                  pnlock.unlock();
               }

               pglock.lock();

               try {
                  pgcond.signalAll();
               }
               finally {
                  pglock.unlock();
               }
            }
            finally {
               proclock.unlock();
            }

            completed = true;
            fireProcessEvent(id, COMPLETED);
            emap.setCompleted(id);
         }
      }

      @Override
      public ReportSheet getReport() {
         return report;
      }

      private void addProperty(String name) {
         if(report != null) {
            String val = report.getProperty(name);

            if(val != null) {
               props.put(name, val);
            }
         }
      }

      @Override
      public String toString() {
         return "Replet:" + id + " [priority:" + priority + "]";
      }

      private Object id; // report id
      private ReportSheet report; // report sheet if any
      private StylePage firstPage; // first StylePage
      private Enumeration<?> pages; // style pages if any
      private int priority; // thread priority
      private boolean interactive; // interactive report
      private boolean batchwait; // batch waiting report
      private boolean oneoff; // one-off style pages
      private boolean ioneoff; // initial value for one-off style pages
      private boolean completed; // paging process over
      private boolean cancelled; // paging process cancelled
      private int pgcnt; // page count
      private int aidx; // access page index
      // report properties
      private Map<String, String> props = new HashMap<>();
      private Throwable error; // exception
   }

   protected enum ProcState {
      RUNNING, CANCELLED
   }

   protected boolean page_streaming = false; // stream paging or not
   protected int min_streaming = 20; // min pages for streaming

   protected Lock proclock = new ReentrantLock();
   private Condition proccond = proclock.newCondition();
   private Lock listenlock = new ReentrantLock();
   private Lock threadlock = new ReentrantLock();
   private Condition threadcond = threadlock.newCondition();
   protected Lock pglock = new ReentrantLock();
   protected Condition pgcond = pglock.newCondition();
   protected Lock pnlock = new ReentrantLock();
   protected Condition pncond = pnlock.newCondition();

   // id.# -> PageGroup
   protected HashMap<String, PageGroup> pgmap = new HashMap<>();
   // id of report in processing
   protected Map<Object, ProcState> procs = new ConcurrentHashMap<>();
   // id -> #page
   private HashMap<Object, Integer> pnmap = new HashMap<>();
   // id -> data update timestamp
   private HashMap<Object, Long> dataTsMap = new HashMap<>();
   // id -> listener set
   private HashMap<Object, Set<ActionListener>> listenmap = new HashMap<>();
   private final Set<ActionListener> allListeners = new HashSet<>();
   // id -> paging processor
   private HashMap<Object, PagingProcessor0> threadmap = new HashMap<>();
   private HashSet<Object> pendings = new HashSet<>(); // pending id set
   private ThreadPool threadpool = null;
   private ExecutionMap emap = new ExecutionMap(); // the executing report
   private byte cache_workset; // max page count in a page group
   private File cdir; // cache directory
   private boolean disposed = false; // destroy this object

   private static final Logger LOG = LoggerFactory.getLogger(ReportCache.class);
}
