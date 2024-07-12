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
package inetsoft.web.binding.command;

import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Command that instructs the client to refresh an assembly object.
 *
 * @since 12.3
 */
public class GetPredefinedNamedGroupCommand implements ViewsheetCommand {
   /**
    * Construstor.
    */
   public GetPredefinedNamedGroupCommand(AssetNamedGroupInfo[] infos) {
      this.ngs = new String[infos.length];

      for(int i = 0; i < infos.length; i++) {
         this.ngs[i] = infos[i].getName();
      }
   }

   /**
    * Get named group names.
    * @return the names of named group.
    */
   public String[] getNamedGroups() {
      return ngs;
   }

   /**
    * Set named group names.
    * @param ngs the named group names.
    */
   public void setNamedGroups(String[] ngs) {
      this.ngs = ngs;
   }

   private String[] ngs;
}
