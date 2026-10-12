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

import inetsoft.report.StylePage;
import inetsoft.report.internal.ObjectCache;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Group a set of pages for swapping.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PageGroup extends XSwappable {
   /**
    * Create a page group.
    */
   public PageGroup(int from, byte max_pages, File swapfile,
                    PagingProcessor processor, int offset) {
      super();

      this.max_pages = max_pages;
      this.swapfile = swapfile;
      this.proc = processor;
      this.pages = new StylePage[max_pages];
      XSwapper s = getSwapper();
      s.cur = System.currentTimeMillis();
      this.accessed = s.cur;
      this.valid = true;
      this.ioneoff = proc == null ? false : proc.isInitialOneOff();
      this.interactive = false;// proc == null ? true : proc.isInteractive();
      this.batch = proc == null ? false : proc.isBatchWaiting();
      this.lcompleted = UNKNOWN;
      this.monitor = s.getMonitor();

      if(this.monitor != null) {
         isCountHM = monitor.isLevelQualified(XSwappableMonitor.HITS);
         isCountRW = monitor.isLevelQualified(XSwappableMonitor.READ);
      }
   }

   /**
    * Access the page group.
    */
   private synchronized void access(int n) {
      accessed = getSwapper().cur;

      if(disposed) {
         return;
      }

      validate();
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || !isSwappable()) {
         return 0;
      }

      // if the page group is not available for paging is not completed,
      // do not let it survive for a long period when memory state is bad
      long livePeriod = !interactive && proc != null && proc.isBatchWaiting() &&
         !proc.isCompleted() ? 200 : alive;

      return getAgePriority(getSwapper().cur - accessed, livePeriod);
   }

   /**
    * Complete this page group and register it as a swappable object.
    */
   @Override
   public void complete() {
      if(disposed || completed) {
         return;
      }

      if(!batch && proc != null) {
         batch = proc.isBatchWaiting();
      }

      completed = true;

      // we only register this swappable for style pages being used for times
      if(proc != null && !proc.isOneOff()) {
         super.complete();
      }
   }

   /**
    * Check if the swappable is completed for swap.
    * @return <tt>true</tt> if completed for swap, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Check if the generated style pages are for interactive usage.
    */
   public boolean isInteractive() {
      return interactive;
   }

   /**
    * Check if the generated style pages are one off.
    */
   public boolean isOneOff() {
      return ioneoff;
   }

   /**
    * Check if is sequential.
    */
   public boolean isSequential() {
      return !interactive && !batch;
   }

   /**
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return !disposed && swapfile != null;
   }

   /**
    * Check if the page group is valid.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return valid;
   }

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   @Override
   public synchronized boolean swap() {
      if(getSwapPriority() == 0) {
         return false;
      }

      return swap0(false);
   }

   /**
    * Swap the swappable. The pages are written to the swap file, and a later swap reuses the
    * file if the completed state is unchanged. The pages are freed only after the whole file is
    * written. A failed write deletes the file and keeps the pages in memory, so the file is
    * rewritten by the next swap instead of being reused (bug #77962).
    * @param fast true to not check the completed state.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   private synchronized boolean swap0(boolean fast) {
      valid = false;
      // if the paging process is not batch waiting, the swap process does not
      // care complete or not, so for this case, completed is always true
      byte completed = fast && !rewriteRequired ? lcompleted :
         (proc != null && (!proc.isBatchWaiting() || proc.isCompleted()) ?
            COMPLETED : UNCOMPLETED);
      boolean write = rewriteRequired || !swapfile.exists() || lcompleted != completed;
      FileOutputStream fout = null;
      ObjectOutputStream out = null;
      boolean done = false;

      try {
         if(!write) {
            for(int i = 0; i < count; i++) {
               pages[i].swap(null, completed == COMPLETED);
            }

            userObj = null;
            done = true;
            return true;
         }

         // the file is created or truncated here, so it is incomplete until it is closed
         rewriteRequired = true;
         fout = new FileOutputStream(swapfile);
         out = new ObjectOutputStream(
            new BufferedOutputStream(Tool.createCompressOutputStream(fout)));
         boolean[] full = new boolean[count];

         // the pages keep their data in memory until the file is complete
         for(int i = 0; i < count; i++) {
            if(testBeforeWrite != null) {
               testBeforeWrite.run();
            }

            full[i] = pages[i].swap(out, completed == COMPLETED);
         }

         if(testBeforeWrite != null) {
            testBeforeWrite.run();
         }

         out.writeObject(userObj);
         out.close();
         lcompleted = completed;
         rewriteRequired = false;
         done = true;

         // the file is complete, drop the data of the pages that are in it
         for(int i = 0; i < count; i++) {
            pages[i].swapCompleted(full[i]);
         }

         userObj = null;

         if(isCountRW) {
            monitor.countWrite(swapfile.length(), XSwappableMonitor.SHEET);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to swap pages to disk", ex);
      }
      finally {
         // an incomplete file must not be reused by a later swap. the pages and the user
         // object are all still in memory, so the group stays valid
         if(!done) {
            if(out != null) {
               try {
                  out.close();
               }
               catch(Throwable ex) {
                  // ignore it
               }
            }

            if(fout != null) {
               try {
                  fout.close();
               }
               catch(Throwable ex) {
                  // ignore it
               }

               if(!swapfile.delete() && swapfile.exists()) {
                  LOG.warn("Failed to delete incomplete page swap file: " + swapfile);
               }
            }

            valid = true;
         }
      }

      return true;
   }

   /**
    * Restore the page group.
    */
   private void validate() {
      if(disposed) {
         return;
      }

      if(isCountHM) {
         if(valid && !lastValid) {
            monitor.countHits(XSwappableMonitor.SHEET, 1);
            lastValid = true;
         }
         else if(!valid) {
            monitor.countMisses(XSwappableMonitor.SHEET, 1);
            lastValid = false;
         }
      }

      if(valid) {
         return;
      }

      getSwapper().waitForMemory();
      ObjectInputStream in = null;
      InputStream input = null;
      boolean done = false;

      // the group is valid only after the whole file is read. a failed read leaves it invalid,
      // so swap() can not rewrite the swap file from the pages that were not read (bug #77984)
      try {
         input = new FileInputStream(swapfile);
         input = Tool.createUncompressInputStream(input);
         in = new ObjectInputStream(new BufferedInputStream(input));

         for(int i = 0; i < count; i++) {
            pages[i].restore(in, false);
         }

         userObj = in.readObject();

         if(isCountRW) {
            monitor.countRead(swapfile.length(), XSwappableMonitor.SHEET);
         }

         valid = true;
         done = true;
      }
      catch(Exception ex) {
         throw new SwapFileReadException(swapfile, ex);
      }
      finally {
         try {
            if(in != null) {
               in.close();
            }
            else if(input != null) {
               input.close();
            }
         }
         catch(Throwable ex) {
            // ignore it
         }

         // put every page back to its swapped state, so a retry reads the whole file again
         // in step with the pages
         if(!done) {
            resetRestored();
         }

         // clear object cache when reading objects over
         ObjectCache.clear();
      }
   }

   /**
    * Drop the data restored by a failed read. The pages and the user object are all in the
    * swap file.
    */
   private void resetRestored() {
      for(int i = 0; i < count; i++) {
         try {
            // clears a swapped page that is in memory, see the reuse path of swap0()
            pages[i].swap(null, false);
         }
         catch(Throwable ex) {
            LOG.warn("Failed to clear a page after a failed read", ex);
         }
      }

      userObj = null;
   }

   /**
    * Dispose the swappable.
    */
   @Override
   public void dispose() {
      dispose(true);
   }

   /**
    * Dispose the swappable.
    */
   public synchronized void dispose(boolean removal) {
      if(disposed) {
         return;
      }

      Throwable failure = null;

      // if not to dispose style pages, we need to restore them first
      if(!removal) {
         try {
            access(-1);
         }
         catch(Throwable ex) {
            // still dispose the group and delete the swap file
            failure = ex;
         }
      }

      disposed = true;

      for(int i = 0; i < count; i++) {
         pages[i].reset(removal);
      }

      pages = null;
      userObj = null;

      if(swapfile != null && swapfile.exists()) {
         swapfile.delete();
      }

      swapfile = null;

      if(failure instanceof Error) {
         throw (Error) failure;
      }
      else if(failure != null) {
         LOG.warn("Failed to restore pages before disposing them", failure);
      }
   }

   /**
    * Add a page to the page group.
    */
   public void addPage(StylePage page) {
      pages[count++] = page;
   }

   /**
    * Get a page from the page group.
    */
   public synchronized StylePage getPage(int n, boolean clone) {
      access(n);
      boolean last = n == count - 1;
      StylePage page = pages[n] == null || !clone ? pages[n] :
         (StylePage) pages[n].clone();

      if(last && !interactive && !disposed && swapfile != null && swapfile.exists()) {
         swap0(true);
      }

      return page;
   }

   /**
    * Get the number of pages in the page group.
    */
   public int getPageCount() {
      return count;
   }

   /**
    * Check if the group is full.
    */
   public boolean isFull() {
      return count >= max_pages;
   }

   /**
    * Get the maximum number of pages to hold in a group.
    */
   public int getMaxPageCount() {
      return max_pages;
   }

   /**
    * Get the swap file.
    */
   public File getSwapFile() {
      return swapfile;
   }

   /**
    * Set the swap file.
    */
   public void setSwapFile(File file) {
      swapfile = file;
   }

   /**
    * Set an object to be associated with this page group.
    */
   public synchronized void setUserObject(Object obj) {
      this.userObj = obj;
   }

   /**
    * Get the object associated with this page group.
    */
   public synchronized Object getUserObject() {
      access(-1);
      return userObj;
   }

   /**
    * Finalize the paged group.
    */
   @Override
   protected void finalize() throws Throwable {
      try {
         dispose();
      }
      catch(Exception ex) {
         // ignore it
      }

      super.finalize();
   }

   private static final byte UNKNOWN = 0; // completed or not?
   private static final byte UNCOMPLETED = 1; // uncompleted
   private static final byte COMPLETED = 2; // completed

   private byte max_pages; // max page count
   private File swapfile; // swap file
   private StylePage[] pages; // style pages
   private Object userObj; // user object, must be serializable
   private short count; // actual page count
   private boolean interactive; // interactive
   private boolean batch; // batch
   private boolean ioneoff; // one off pages
   private boolean completed; // whether this page group is completed
   private PagingProcessor proc; // shared paging processor
   private boolean valid; // valid flag
   private boolean lastValid;
   private byte lcompleted; // last completed flag
   // the swap file is incomplete (a write failed), it must be rewritten and not reused
   private boolean rewriteRequired;
   private long accessed; // last accessed timestamp
   private boolean disposed; // disposed flag
   private transient XSwappableMonitor monitor;
   private transient boolean isCountHM; // is hit/misses level qualified
   private transient boolean isCountRW; // is read/write level qualified
   // test-only hook, run before each page and the user object is written to the swap file
   transient Runnable testBeforeWrite;

   private static final Logger LOG = LoggerFactory.getLogger(PageGroup.class);
}
