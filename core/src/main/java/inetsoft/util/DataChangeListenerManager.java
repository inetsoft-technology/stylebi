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
package inetsoft.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Data change listener manager manages data change listeners.
 * If a class uses the manager to add/remove listeners, it can clear
 * all the added listeners by calling manager.clear. We strongly recommend
 * you to use the manager to manage data change listeners in a class, and
 * when the class object is useless, REMEMBER to call manager.clear to avoid
 * memory leak.
 *
 * @version 6.5
 * @author InetSoft Technology Corp
 */
public class DataChangeListenerManager {
   /**
    * Add a data change listener to a data space.
    * @param space the specified data space to be watched.
    * @param dir the specified directory which contains the target file.
    * @param file the specified file to be watched.
    * @param listener the specified listener to be added.
    */
   public void addChangeListener(DataSpace space, String dir,
      String file, DataChangeListener listener)
   {
      if(space != null) {
         ChangeListenerInfo info = new ChangeListenerInfo(space, dir, file, listener);

         if(infos.add(info)) {
            info.space.addChangeListener(info.dir, info.file, info.listener);
         }
      }
   }

   /**
    * Remove a data space change listener.
    * @param space the specified data space to be watched.
    * @param dir the specified directory which contains the target file.
    * @param file the specified file to be watched.
    * @param listener the specified listener to be added.
    */
   public void removeChangeListener(DataSpace space, String dir,
      String file, DataChangeListener listener)
   {
      if(space != null) {
         ChangeListenerInfo info = new ChangeListenerInfo(space, dir, file, listener);

         if(infos.remove(info)) {
            info.space.removeChangeListener(info.dir, info.file, info.listener);
         }
      }
   }

   /**
    * Tear down the data change listener manager.
    */
   public void clear() {

      for(Object info1 : infos) {
         ChangeListenerInfo info = (ChangeListenerInfo) info1;
         info.space.removeChangeListener(info.dir, info.file, info.listener);
      }

      infos.clear();
   }

   /**
    * Get manager size.
    * @return manager size.
    */
   public int size() {
     return infos.size();
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "DataChangeListenerManager:" + infos;
   }

   /**
    * Change listener info contains data space source and change listener info.
    */
   private static class ChangeListenerInfo {
      /**
       * Constructor.
       */
      ChangeListenerInfo(DataSpace space, String dir, String file, DataChangeListener listener)
      {

         this.space = space;
         this.dir = dir;
         this.file = file;
         this.listener = listener;
      }

      /**
       * Get hashCode.
       */
      public int hashCode() {
         // @by billh, ignore proirity/timestamp to keep consistent with
         // DataSpace logic
         int code = space.hashCode();
         code += dir == null ? 0 : dir.hashCode();
         code += file == null ? 0 : file.hashCode();
         code += listener.hashCode();

         return code;
      }

      /**
       * Check if equals another object.
       */
      public boolean equals(Object obj) {
         if(!(obj instanceof ChangeListenerInfo)) {
            return false;
         }

         ChangeListenerInfo info = (ChangeListenerInfo) obj;

         // @by billh, ignore proirity/timestamp to keep consistent with
         // DataSpace logic
         return space.equals(info.space) && Tool.equals(dir, info.dir) &&
            Tool.equals(file, info.file) && listener.equals(info.listener);
      }

      /**
       * Get the string representation.
       */
      public String toString() {
         return "CLI:[" + space + "," + dir + "," + file + "," + listener + "]";
      }

      private DataSpace space;
      private String dir;
      private String file;
      private DataChangeListener listener;
   }

   private Set<ChangeListenerInfo> infos = new HashSet<>();
}
