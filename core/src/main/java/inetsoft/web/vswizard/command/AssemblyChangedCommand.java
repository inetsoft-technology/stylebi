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
package inetsoft.web.vswizard.command;

import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Assembly name maybe changed if switch assembly type in vs wizard, this command is used to
 * send the latest assembly name to gui to refresh the right assembly in viewsheet pane, and use the old assembly name to
 */
public class AssemblyChangedCommand implements ViewsheetCommand {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getOname() {
      return oname;
   }

   public void setOname(String oname) {
      this.oname = oname;
   }

   private String name;
   private String oname;
}
