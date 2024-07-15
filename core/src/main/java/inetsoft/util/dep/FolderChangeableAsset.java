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

import inetsoft.sree.security.IdentityID;

public interface FolderChangeableAsset {
   /**
    * Convert this asset to an identifier under the new root folder.
    *
    * @param newFolder new root folder path.
    * @return an new identifier.
    */
   String getChangeFolderIdentifier(String oldFolder, String newFolder);

   /**
    * Convert this asset to an identifier under the new root folder.
    *
    * @param newFolder new root folder path.
    * @param newUser new root folder user.
    * @return an new identifier.
    */
   String getChangeFolderIdentifier(String oldFolder, String newFolder, IdentityID newUser);

   /**
    * Get the changed path.
    * @param oldPath old path.
    * @param oldFolder change the asset from the old folder.
    * @param newFolder change the asset to the new folder.
    *
    * @return the changed path.
    */
   static String changeFolder(String oldPath, String oldFolder, String newFolder) {
      if(!oldFolder.endsWith("/")) {
         oldFolder += "/";
      }

      if(oldPath.startsWith(oldFolder)) {
         if(oldPath.length() == oldFolder.length()) {
            oldPath = "/";
         }
         else {
            oldPath = oldPath.substring(oldFolder.length());
         }
      }

      if(!"/".equals(newFolder)) {
         if(oldPath.startsWith("/") && oldPath.length() > 1) {
            oldPath = oldPath.substring(1);
         }

         oldPath = newFolder + "/" + oldPath;
      }

      return oldPath;
   }
}
