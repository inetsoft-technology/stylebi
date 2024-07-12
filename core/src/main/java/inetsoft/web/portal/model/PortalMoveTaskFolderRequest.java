/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;

public class PortalMoveTaskFolderRequest {
   public AssetEntry getTarget() {
      return target;
   }

   public void setTarget(AssetEntry target) {
      this.target = target;
   }

   public ScheduleTaskModel[] getTasks() {
      return tasks;
   }

   public void setTasks(ScheduleTaskModel[] tasks) {
      this.tasks = tasks;
   }

   public String[] getFolders() {
      return folders;
   }

   public void setFolders(String[] folders) {
      this.folders = folders;
   }

   private AssetEntry target;
   private ScheduleTaskModel[] tasks;
   private String[] folders;
}
