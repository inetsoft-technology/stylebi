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
package inetsoft.web.composer.vs.objects.command;


import inetsoft.web.viewsheet.command.ViewsheetCommand;

public class RenameVSObjectCommand implements ViewsheetCommand {
   public RenameVSObjectCommand(String oldName, String newName) {
      this.oldName = oldName;
      this.newName = newName;
   }

   public String getOldName() {
      return oldName;
   }

   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   public String getNewName() {
      return newName;
   }

   public void setNewName(String newName) {
      this.newName = newName;
   }

   private String oldName;
   private String newName;
}
