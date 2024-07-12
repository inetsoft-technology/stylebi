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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;

/**
 * Set layout info command.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class SetLayoutInfoCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public SetLayoutInfoCommand() {
      super();
   }

   /**
    * Constructor.
    * @param layoutInfo the layout info.
    * @param connectors the connectors.
    */
   public SetLayoutInfoCommand(RuntimeViewsheet rvs) {
      this();
      Viewsheet vs = rvs.getViewsheet();
      LayoutInfo layoutInfo = vs.getLayoutInfo();
      put("layoutInfo", layoutInfo);
   }
}
