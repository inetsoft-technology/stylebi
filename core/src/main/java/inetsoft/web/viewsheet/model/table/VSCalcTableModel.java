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
package inetsoft.web.viewsheet.model.table;

import inetsoft.report.LayoutTool;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.web.viewsheet.model.VSObjectModelFactory;
import org.springframework.stereotype.Component;

public class VSCalcTableModel extends BaseTableModel<CalcTableVSAssembly> {
   public VSCalcTableModel(CalcTableVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      empty = empty && !LayoutTool.hasScriptBinding(assembly.getTableLayout()) &&
         !LayoutTool.hasStaticTextBinding(assembly.getTableLayout());
   }

   @Component
   public static final class VSCalcTableModelFactory
      extends VSObjectModelFactory<CalcTableVSAssembly, VSCalcTableModel>
   {
      public VSCalcTableModelFactory() {
         super(CalcTableVSAssembly.class);
      }

      @Override
      public VSCalcTableModel createModel(CalcTableVSAssembly assembly, RuntimeViewsheet rvs) {
         try {
            return new VSCalcTableModel(assembly, rvs);
         }
         catch(RuntimeException e) {
            throw e;
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to get runtime viewsheet instance", e);
         }
      }
   }
}
