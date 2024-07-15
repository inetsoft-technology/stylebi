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
package inetsoft.web.vswizard.command;

import inetsoft.web.viewsheet.command.ViewsheetCommand;

import java.util.List;

public class ReplaceColumnCommand implements ViewsheetCommand {
   public ReplaceColumnCommand() {
   }

   public ReplaceColumnCommand(List<String> paths, List<String> oldPaths) {
      this.paths = paths;
      this.oldPaths = oldPaths;
   }

   public List<String> getPaths() {
      return paths;
   }

   public void setPaths(List<String> paths) {
      this.paths = paths;
   }

   public List<String> getOldPaths() {
      return oldPaths;
   }

   public void setOldPaths(List<String> oldPaths) {
      this.oldPaths = oldPaths;
   }

   private List<String> paths;
   private List<String> oldPaths;
}
