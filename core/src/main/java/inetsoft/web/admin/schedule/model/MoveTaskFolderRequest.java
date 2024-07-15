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
package inetsoft.web.admin.schedule.model;

import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;

public class MoveTaskFolderRequest {
   public ContentRepositoryTreeNode getTarget() {
      return target;
   }

   public void setTarget(ContentRepositoryTreeNode target) {
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

   private ContentRepositoryTreeNode target;
   private ScheduleTaskModel[] tasks;
   private String[] folders;
}
