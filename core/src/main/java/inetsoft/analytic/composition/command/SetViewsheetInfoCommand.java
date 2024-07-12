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
import inetsoft.sree.UserEnv;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.FormUtil;

/**
 * Refresh viewsheet object command.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SetViewsheetInfoCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public SetViewsheetInfoCommand() {
      super();
   }

   /**
    * Constructor.
    * @param info the assembly info.
    * @param connectors the connectors.
    */
   public SetViewsheetInfoCommand(RuntimeViewsheet rvs) {
      this();
      Viewsheet vs = rvs.getViewsheet();
      
      if(vs == null) {
         return;
      }

      put("info", vs.getViewsheetInfo());
      put("assemblyInfo", vs.getInfo());
      put("wentry", vs.getBaseEntry());
      put("__annotation__",
          UserEnv.getProperty(rvs.getUser(), "annotation", "true") + "");
      put("__annotated__", AnnotationVSUtil.isAnnotated(vs) + "");
      put("formTable", FormUtil.containsForm(rvs.getViewsheet(), true) + "");
   }
}
