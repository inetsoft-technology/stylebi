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
package inetsoft.web.admin.cache;
import java.io.Serializable;
/**
 * CacheInfo stores the information of the CacheMonitorService.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class CacheInfo implements Cloneable,Serializable {
   /**
    * The report type.
    */
   public static final int REPORT = 0;
   /**
    * The data type.
    */
   public static final int DATA = 1;
   /**
    * The location of memory.
    */
   public final static String MEMORY = "memory";
   /**
    * The location of disk.
    */
   public final static String DISK = "disk";

   /**
    * Create a cache info.
    * @param type report or data.
    * @param loc memory or disk.
    */
   public CacheInfo(int type, String loc) {
      setType(type);
      setLocation(loc);
   }

   /**
    * Get the type of the cache.
    * @return the type of cache.
    */
   public int getType() {
      return type;
   }

   /**
    * Set the type of the cache.
    * @param type the type of the cache.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the location.
    * @return the location.
    */
   public String getLocation() {
      return location;
   }

   /**
    * Set the location.
    * @param location the location.
    */
   public void setLocation(String location) {
      this.location = location;
   }

   /**
    * Get the count of the report/data.
    * @return the count of the report/data.
    */
   public int getCount() {
      return count;
   }

   /**
    * Add the count of the report/data.
    * @param count the count of the report/data.
    */
   public void addCount(int count) {
      this.count += count;
   }

   private int type;
   private String location;
   private int count;
}
