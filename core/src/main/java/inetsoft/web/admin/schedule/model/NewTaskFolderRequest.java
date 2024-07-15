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
package inetsoft.web.admin.schedule.model;

import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;

public class NewTaskFolderRequest {
   public ContentRepositoryTreeNode getParent() {
      return parent;
   }

   public void setParent(ContentRepositoryTreeNode parent) {
      this.parent = parent;
   }

   public String getFolderName() {
      return folderName;
   }

   public void setFolderName(String folderName) {
      this.folderName = folderName;
   }

   private ContentRepositoryTreeNode parent;
   private String folderName;
}
