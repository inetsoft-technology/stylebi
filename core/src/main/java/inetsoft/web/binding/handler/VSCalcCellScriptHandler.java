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
package inetsoft.web.binding.handler;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.springframework.stereotype.Component;

@Component
public class VSCalcCellScriptHandler {
   /**
    * GetCalcCellScriptEvent
    */
   public String get(RuntimeViewsheet rvs, VSAssemblyInfo info, int row, int col) {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null ||
         !(info instanceof CalcTableVSAssemblyInfo))
      {
         return "";
      }

      String name = info.getAbsoluteName2();
      CalcTableVSAssembly assembly = (CalcTableVSAssembly) vs.getAssembly(name);
      assembly = assembly == null ? null : (CalcTableVSAssembly) assembly.clone();
      VSLayoutTool.createCalcLens(assembly, null, new VariableTable(), false);
      TableLens table = assembly.getBaseTable();
      String script = "";

      if(table instanceof CalcTableLens) {
         script = ((CalcTableLens) table).getFormula(row, col);

         SourceInfo sinfo = assembly.getSourceInfo();
         String source = sinfo == null ? null : sinfo.getSource();
         script = removeSourceFromScript(source, script);
      }

      return (script != null) ? script : "";
   }

   private String removeSourceFromScript(String source, String script) {
      if(source == null || script == null) {
         return script;
      }

      String prefix = source + ".";
      return script.replace(prefix, "");
   }
}
