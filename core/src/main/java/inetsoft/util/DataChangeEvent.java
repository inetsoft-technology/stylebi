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
package inetsoft.util;

/**
 * DataChangeEvent is used to notify interested parties that data has
 * changed.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class DataChangeEvent {
   /**
    * Construct a DataChangeEvent object.
    * @param dir directory name
    * @param file file name
    * @param eventTime event time
    */
   public DataChangeEvent(String dir, String file, long eventTime) {
      this.dir = dir;
      this.file = file;
      this.eventTime = eventTime;
   }

   /**
    * Get the directory name for the source of this event.
    * @return directory name
    */
   public String getDir() {
      return dir;
   }

   /**
    * Get the file name for the source of this event.
    * @return file name
    */
   public String getFile() {
      return file;
   }

   /**
    * Get the time for the source of this event.
    * @return event time
    */
   public long getEventTime() {
      return eventTime;
   }

   /**
    * Get string representation.
    */
   public String toString() {
      return "DataChangeEvent: [" + dir + "," + file + "," + eventTime + "]";
   }

   private String dir;
   private String file;
   private long eventTime;
}
