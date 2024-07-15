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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("folder")
public class Folder extends AssetItem {
   /**
    * Creates a new instance of <tt>Folder</tt>.
    */
   public Folder() {
      super(TYPE);
   }

   public String getParentPath() {
      return parentPath;
   }

   public void setParentPath(String parentPath) {
      this.parentPath = parentPath;
   }

   public int getParentFolderCount() {
      return parentFolderCount;
   }

   public void setParentFolderCount(int parentFolderCount) {
      this.parentFolderCount = parentFolderCount;
   }

   private String parentPath;
   private int parentFolderCount = 0;
   public static final String TYPE = "folder";
}
