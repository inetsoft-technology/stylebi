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
package inetsoft.report.composition.command;

import inetsoft.uql.asset.internal.WSAssemblyInfo;

/**
 * Add worksheet object command.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AddWSObjectCommand extends WorksheetCommand {
   /**
    * Constructor.
    */
   public AddWSObjectCommand() {
      super();
   }

   /**
    * Constructor.
    * @param info the assembly info.
    * @param mode the mode.
    */
   public AddWSObjectCommand(WSAssemblyInfo info, int mode) {
      this();
      put("info", info);
      put("mode", "" + mode);
   }

}
