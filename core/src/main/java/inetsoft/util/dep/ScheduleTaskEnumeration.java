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
package inetsoft.util.dep;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.SecurityEngine;

import java.util.*;

/**
 * ScheduleTaskEnumeration implements the XAssetEnumeration interface,
 * generates a series of ScheduleTaskAssets, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class ScheduleTaskEnumeration implements XAssetEnumeration<ScheduleTaskAsset> {
   /**
    * Constructor.
    */
   public ScheduleTaskEnumeration() {
      ScheduleManager manager = ScheduleManager.getScheduleManager();
      tasks = new ArrayList<>(manager.getScheduleTasks());
      boolean isSecurityEnabled = false;

      try {
         isSecurityEnabled = !SecurityEngine.getSecurity().getSecurityProvider().isVirtual();
      }
      catch(Exception ex) {
         // ignore
      }

      for(Iterator<ScheduleTask> it = tasks.iterator(); it.hasNext();) {
         ScheduleTask task = it.next();

         // remove illegal tasks under disabled security enviroment
         // or enabled security enviroment
         if(task.getName().startsWith("MV Task: "))
         {
            it.remove();
         }
      }
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return tasks != null && currentIndex < tasks.size();
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   @Override
   public ScheduleTaskAsset nextElement() {
      ScheduleTask task = tasks.get(currentIndex++);
      return new ScheduleTaskAsset(task.getName(), task.getOwner());
   }

   private List<ScheduleTask> tasks;
   private int currentIndex;
}