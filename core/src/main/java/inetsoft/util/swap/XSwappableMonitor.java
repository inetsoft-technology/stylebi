/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.swap;

/**
 * XSwappableMonitor is used to monitor XSwappables on the count of hits, misses
 * read and write.
 */
public interface XSwappableMonitor {
   /**
    * The report type.
    */
   int REPORT = 0;
   /**
    * The data type.
    */
   int DATA = 1;

   /**
    * The location of memory.
    */
   String MEMORY = "memory";
   /**
    * The location of disk.
    */
   String DISK = "disk";
   /**
    * Hits.
    */
   String HITS = "hits";
   /**
    * Misses.
    */
   String MISSES = "misses";
   /**
    * Type.
    */
   String TYPE = "type";
   /**
    * Location.
    */
   String LOCATION = "location";
   /**
    * Count.
    */
   String COUNT = "count";
   /**
    * Read.
    */
   String READ = "read";
   /**
    * Written.
    */
   String WRITTEN = "written";

   /**
    * Count the hits.
    * @param hits the hits.
    */
   void countHits(int type, int hits);

   /**
    * Count the misses.
    * @param misses the misses.
    */
   void countMisses(int type, int misses);

   /**
    * Add the number of bytes which has been restored.
    * @param num the number of bytes.
    * @param type report or data.
    */
   void countRead(long num, int type);

   /**
    * Add the number of bytes which has been swapped out.
    * @param num the number of bytes.
    * @param type report or data.
    */
   void countWrite(long num, int type);

   /**
    * Check if the level of the attribute qualified.
    * @param attr the attribute.
    * @return true if the monitor level is not lower than the attribute level.
    */
   boolean isLevelQualified(String attr);
}
