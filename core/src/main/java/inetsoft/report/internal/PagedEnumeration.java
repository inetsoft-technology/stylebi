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
package inetsoft.report.internal;

import inetsoft.report.StylePage;
import inetsoft.report.internal.paging.*;

import java.util.Enumeration;

/**
 * This class implements the IndexedEnumeration and supports swapping of
 * style pages to disk. All pages are fully generated before they are returned
 * by the enumeration.
 *
 * @version 6.1, 11/9/2004
 * @author InetSoft Technology Corp
 */
public class PagedEnumeration implements IndexedEnumeration {
   /**
    * Create an instance of paged enumeration.
    */
   public PagedEnumeration(Enumeration pages) {
      this(pages, true);
   }

   /**
    * Create an instance of paged enumeration.
    */
   public PagedEnumeration(Enumeration pages, boolean removal) {
      this(pages, removal, false);
   }

   /**
    * Create an instance of paged enumeration.
    */
   public PagedEnumeration(Enumeration pages, boolean removal, boolean batch) {
      this.removal = removal;
      this.id = "report_"+ hashCode() + "_" + System.currentTimeMillis();
      cache.put(id, pages, false, false, false);

      for(int i = 0; i < lastIdx.length; i++) {
         lastIdx[i] = -1;
         lastPage[i] = null;
      }

      this.title = (pages instanceof SwappedEnumeration) ?
         ((SwappedEnumeration) pages).getTitle() : null;

      if(batch) {
         long sleeptime = 10;

         while(true) {
            try {
               if(cache.getProcessStatus(id) == ReportCache.COMPLETED) {
                  break;
               }

               Thread.sleep(sleeptime);
               sleeptime = Math.min(500, sleeptime * 2);
            }
            catch(Exception ex) {
               // ignore it
               break;
            }
         }
      }
   }

   @Override
   public boolean hasMoreElements() {
      return get(idx) != null;
   }

   @Override
   public synchronized Object nextElement() {
      return get(idx++);
   }

   @Override
   public synchronized void reset() {
      idx = 0;
      lastIdx = new int[5];
      lastPage = new StylePage[5];

      for(int i = 0; i < lastIdx.length; i++) {
         lastIdx[i] = -1;
         lastPage[i] = null;
      }
   }

   /**
    * Get the item at specified index.
    */
   @Override
   public synchronized Object get(int idx) {
      int index = idx % lastIdx.length;

      if(lastIdx[index] != idx) {
         lastIdx[index] = idx;
         lastPage[index] = cache.getPage(id, idx);
      }

      return lastPage[index];
   }

   /**
    * Get the number of items in the enumeration.
    */
   @Override
   public int size() {
      return cache.getPageCount(id);
   }

   /**
    * This method must be called to destroy the threads in this cache if the
    * cache is no longer needed.
    */
   @Override
   public void dispose() {
      for(int i = 0; i < lastIdx.length; i++) {
         lastIdx[i] = -1;
         lastPage[i] = null;
      }

      cache.remove(id, removal);
      cache.dispose();
   }

   /**
    * Get the title.
    */
   @Override
   public String getTitle() {
      return title;
   }

   /**
    * Clean up memory and threads.
    */
   @Override
   public void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   private StylePage[] lastPage = new StylePage[5];
   private int[] lastIdx = new int[5];
   private ReportCache cache = new ReportCache(1);
   private String id;
   private int idx = 0; // current enumeration index
   private boolean removal = true; // true to dispose pages when dispose cache
   private String title;
}
