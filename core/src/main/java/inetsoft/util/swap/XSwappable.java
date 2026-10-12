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

import inetsoft.sree.SreeEnv;
import inetsoft.util.FileSystemService;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * XSwappable, it is swappable to save memory.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public abstract class XSwappable implements Serializable {
   /**
    * Low swap performance.
    */
   public static final int LOW_PERFORMANCE = 0;
   /**
    * Normal swap performance.
    */
   public static final int NORM_PERFORMANCE = 3;
   /**
    * High swap performance.
    */
   public static final int HIGH_PERFORMANCE = 6;
   /**
    * Highest swap performance.
    */
   public static final int HIGHEST_PERFORMANCE = 9;

   /**
    * Get the swapping priority. Higher value means it should be swapped before
    * the lower value objects.
    * @return a value 0 and above, 0 to not swap.
    */
   public abstract double getSwapPriority();

   /**
    * Check if the swappable is completed (not partial memory state) for swap.
    */
   public abstract boolean isCompleted();

   /**
    * Check if the swappable is swappable. If returns false, this object will be removed
    * from the swapper and will NOT be swapped again. The implementation should setup
    * all variables to return the correct value before complete() is called. To temporarily
    * disable swapping, return 0 from getSwapPriority().
    */
   public abstract boolean isSwappable();

   /**
    * Check if the swappable is in valid state (in memory).
    */
   public abstract boolean isValid();

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   public abstract boolean swap();

   /**
    * Dispose the swappable.
    */
   public abstract void dispose();

   /**
    * Get the swap files of this swappable
    */
   public File[] getSwapFiles() {
      return null;
   }

   /**
    * Create an instance of <tt>XSwappable</tt>.
    */
   protected XSwappable() {
      super();

      initPrefix();
   }

   protected XSwapper getSwapper() {
      if(swapper == null) {
         swapper = XSwapper.getSwapper();
      }

      return swapper;
   }

   private void initPrefix() {
      this.prefix = getSwapper().getPrefix();
   }

   /**
    * Get the file by name. The file is in the cache directory that was in effect when this
    * swappable first resolved a file, so the files it swapped out are still found (and deleted)
    * after <tt>replet.cache.directory</tt> is changed. A changed directory applies to new
    * swappables only.
    * @return the file by name.
    */
   protected final File getFile(String name) {
      // always resolved through the service, which creates the cache directory
      File file = FileSystemService.getInstance().getCacheFile(name);
      File dir = swapDirectory;

      if(dir == null && file != null && file.getParentFile() != null) {
         // set once, so concurrent first calls agree on one directory
         if(SWAP_DIRECTORY.compareAndSet(this, null, file.getParentFile())) {
            return file;
         }

         dir = swapDirectory;
      }

      if(dir != null && (file == null || !dir.equals(file.getParentFile()))) {
         return new File(dir, name);
      }

      return file;
   }

   /**
    * Calculate swap priority according to age.
    * @param age the period the object is last used.
    * @param minAge the minAge to keep object in memory.
    * @return a priority value of > 1. The higher the value the most likely to be swapped.
    */
   protected double getAgePriority(long age, long minAge) {
      if(minAge == 0) {
         minAge = 1000;
      }

      return ((age + minAge) / (double) minAge);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   /*
   public String toString() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      cls = cls.substring(index + 1);
      return cls + "@" + hashCode() + "[priority:" + getSwapPriority() + "]";
   }
   */

   /**
    * This method should be called when the object is ready to be swapped.
    */
   public void complete() {
      getSwapper().register(this);
   }

   @Override
   public Object clone() {
      try {
         XSwappable swap = (XSwappable) super.clone();
         swap.initPrefix();
         // the clone has its own files, which go to the current cache directory
         swap.swapDirectory = null;
         return swap;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * XSwappable comparator.
    */
   public static final class PriorityComparator implements Comparator {
      public PriorityComparator() {
         super();

         ts = XSwapper.getSwapper().cur;
      }

      @Override
      public int compare(Object o1, Object o2) {
         XSwappable s1 = (XSwappable) o1;
         XSwappable s2 = (XSwappable) o2;

         // if priority changes when sorting, the sort result is wrong
         if(s1.comp != this) {
            s1.comp = this;
            s1.priority = s1.getSwapPriority();
         }

         if(s2.comp != this) {
            s2.comp = this;
            s2.priority = s2.getSwapPriority();
         }

         return s2.priority > s1.priority ? 1 : (s2.priority < s1.priority ? -1 : 0);
      }

      private long ts;
   }

   protected static final int alive =
      Integer.parseInt(SreeEnv.getProperty("swappable.alive.period", "1500"));
   protected String prefix;
   protected transient XSwapper swapper;
   // the cache directory of this swappable's files, set by the first getFile() call
   private transient volatile File swapDirectory;
   private static final AtomicReferenceFieldUpdater<XSwappable, File> SWAP_DIRECTORY =
      AtomicReferenceFieldUpdater.newUpdater(XSwappable.class, File.class, "swapDirectory");
   private transient double priority;
   private transient Comparator comp;
}
