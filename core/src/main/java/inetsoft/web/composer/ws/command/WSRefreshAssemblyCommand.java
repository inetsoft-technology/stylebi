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
package inetsoft.web.composer.ws.command;

import inetsoft.web.composer.ws.assembly.WSAssemblyModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

public class WSRefreshAssemblyCommand implements ViewsheetCommand {
   public String getOldName() {
      return oldName;
   }

   public void setOldName(String oldName) {
      this.oldName = oldName;
   }

   public WSAssemblyModel getAssembly() {
      return assembly;
   }

   public void setAssembly(WSAssemblyModel assembly) {
      this.assembly = assembly;
   }

   /* Names of assembly prior to refresh. Not necessarily changed. */
   private String oldName;
   private WSAssemblyModel assembly;
}
