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

public class ChangeVSSelectionTitleCommand implements ViewsheetCommand {

   public ChangeVSSelectionTitleCommand(String oldTitle, String newTitle) {
      this.oldTitle = oldTitle;
      this.newTitle = newTitle;
   }

   public String getOldTitle() {
      return oldTitle;
   }

   public void setOldTitle(String oldTitle) {
      this.oldTitle = oldTitle;
   }

   public String getNewTitle() {
      return newTitle;
   }

   public void setNewTitle(String newTitle) {
      this.newTitle = newTitle;
   }

   private String oldTitle;
   private String newTitle;
}
