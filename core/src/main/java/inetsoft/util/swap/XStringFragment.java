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
package inetsoft.util.swap;

import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * XStringFragment, the swappable string fragment.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public final class XStringFragment extends XSwappable {
   /**
    * Create an instance of <tt>XStringFragment</tt>.
    */
   public XStringFragment(String val) {
      this(val, 0);
   }

   public XStringFragment(String val, long interval) {
      super();
      this.interval = interval;
      XSwapper.cur = System.currentTimeMillis();
      this.iaccessed = XSwapper.cur;
      value = val;
      len = value == null ? 0 : value.length();
      this.valid = true;
      this.monitor = XSwapper.getMonitor();

      if(monitor != null) {
         isCountHM = monitor.isLevelQualified(XSwappableMonitor.HITS);
         isCountRW = monitor.isLevelQualified(XSwappableMonitor.READ);
      }
   }

   /**
    * Get current data.
    */
   public String getCurrentData() {
      return value;
   }

   /**
    * Get really data.
    */
   public synchronized String getData() {
      access();
      return value;
   }

   /**
    * Add swap listener.
    */
   public void addListener(DataSwapListener listener) {
      if(listeners == null) {
         listeners = new ArrayList<>();
      }

      removeListener(listener);
      listeners.add(listener);
   }

   /**
    * Remove swap listener.
    */
   public boolean removeListener(DataSwapListener listener) {
      return listeners.remove(listener);
   }

   /**
    * Complete this int fragment.
    */
   @Override
   public void complete() {
      if(disposed || completed) {
         return;
      }

      completed = true;
      super.complete();
   }

   /**
    * Access the int fragment.
    */
   public final void access() {
      iaccessed = XSwapper.cur;

      if(isCountHM) {
         if(valid && !lastValid) {
            monitor.countHits(XSwappableMonitor.DATA, 1);
            lastValid = true;
         }
         else if(!valid) {
            monitor.countMisses(XSwappableMonitor.DATA, 1);
            lastValid = false;
         }
      }

      if(!valid) {
         DEBUG_LOG.debug("Validate swapped data: %s", this);
         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            if(!valid) {
               access0();
            }
         }
      }
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

      valid = false;
      swap0();
      return true;
   }

   /**
    * Dispose the swappable.
    */
   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      XSwapper.deregister(this);
      disposed = true;
      value = null;
      File file = getFile(prefix + ".tdat");

      if(file.exists()) {
         boolean result = file.delete();

         if(!result) {
            FileSystemService.getInstance().remove(file, 30000);
         }
      }

      if(listeners != null) {
         listeners.clear();
      }

      listeners = null;
   }

   /**
    * Get data length.
    */
   public int getLength() {
      return len;
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || !isSwappable()) {
         return 0;
      }

      return getAgePriority(XSwapper.cur - iaccessed, interval);
   }

   /**
    * Check if this int fragment is completed for swap.
    * @return <tt>true</tt< if completed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return !disposed;
   }

   /**
    * Check if the int fragment is valid.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return valid;
   }

   /**
    * Validate the int fragment internally.
    */
   private void access0() {
      valid = true;
      File file = getFile(prefix + ".tdat");

      RandomAccessFile fin = null;

      try {
         fin = new RandomAccessFile(file, "r");
         byte[] buf = new byte[(int) file.length()];

         fin.readFully(buf);
         value = new String(buf, "UTF-8");
         fireEvent(false);

         if(isCountRW) {
            monitor.countRead(buf.length, XSwappableMonitor.DATA);
         }
      }
      catch(FileNotFoundException ex) {
         return;
      }
      catch(Exception ex) {
         LOG.error("Failed to read swap file: " + file, ex);
      }
      finally {
         try {
            if(fin != null) {
               fin.close();
               fin = null;
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      file = null;
   }

   /**
    * Swap the int fragment internally.
    */
   private void swap0() {
      File file = getFile(prefix + ".tdat");
      RandomAccessFile fout = null;

      try {
         if(!file.exists()) {
            fout = new RandomAccessFile(file, "rw");
         }

         if(disposed) {
            return;
         }

         int len = 0;

         if(fout != null) {
	    XSwapper.getSwapper().waitForMemory();
            byte[] buf = value.getBytes("UTF-8");
            len = buf.length;
            fout.write(buf);
         }

         value = null;
         fireEvent(true);

         if(isCountRW && fout != null) {
            monitor.countWrite(len, XSwappableMonitor.DATA);
         }

         file = null;
      }
      catch(Exception ex) {
         LOG.error("Failed to write swap file: " + file, ex);
      }
      finally {
         try {
            if(fout != null) {
               fout.close();
               fout = null;
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }
   }

   /**
    * Fire event.
    */
   private void fireEvent(boolean swapped) {
      if(listeners != null) {
         DataSwapEvent event = null;

         for(int i = 0; i < listeners.size(); i++) {
            if(event == null) {
               event = new DataSwapEvent(this, swapped);
            }

            listeners.get(i).dataSwapped(event);
         }
      }
   }

   /**
    * Finalize the object.
    */
   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XStringFragment.class);
   private static final Logger DEBUG_LOG =
      LoggerFactory.getLogger("inetsoft.swap_data");
   private long iaccessed;
   private long interval;
   private int len = 0;
   private String value;
   private boolean valid; // valid flag
   private boolean lastValid;
   private boolean completed; // completed flag
   private boolean disposed; // disposed flag
   private List<DataSwapListener> listeners;
   private transient XSwappableMonitor monitor;
   private transient boolean isCountHM;
   private transient boolean isCountRW;
}
