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
package inetsoft.web.composer.tablestyle;

public class SaveLibraryDialogModelValidator {
   public SaveLibraryDialogModelValidator() {
   }

   public String getAlreadyExists() {
      return alreadyExists;
   }

   public void setAlreadyExists(String alreadyExists) {
      this.alreadyExists = alreadyExists;
   }

   public String getPermissionDenied() {
      return permissionDenied;
   }

   public void setPermissionDenied(String permissionDenied) {
      this.permissionDenied = permissionDenied;
   }

   public boolean isAllowOverwrite() {
      return allowOverwrite;
   }

   public void setAllowOverwrite(boolean allowOverwrite) {
      this.allowOverwrite = allowOverwrite;
   }

   private String alreadyExists;
   private String permissionDenied;
   private boolean allowOverwrite;
}
