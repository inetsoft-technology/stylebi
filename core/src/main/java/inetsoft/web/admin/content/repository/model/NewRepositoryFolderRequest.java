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
package inetsoft.web.admin.content.repository.model;

import inetsoft.sree.security.IdentityID;

import javax.annotation.Nullable;

public class NewRepositoryFolderRequest {
   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getParentFolder() {
      return parentFolder;
   }

   public void setParentFolder(String parentFolder) {
      this.parentFolder = parentFolder;
   }

   public int getType() {
      return type;
   }

   public void setType(int type) {
      this.type = type;
   }

   public IdentityID getOwner() {
      return owner;
   }

   public void setOwner(IdentityID owner) {
      this.owner = owner;
   }

   public String getFolderName() {
      return folderName;
   }

   @Nullable
   public void setFolderName(String folderName) {
      this.folderName = folderName;
   }

   private String path = null;
   private String parentFolder = null;
   private int type;
   private IdentityID owner;
   private String folderName;
}
