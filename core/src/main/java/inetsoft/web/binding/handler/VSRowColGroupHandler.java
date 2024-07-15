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

import inetsoft.report.LayoutTool;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.CalcAttr;
import inetsoft.uql.XNode;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.springframework.stereotype.Component;

@Component
public class VSRowColGroupHandler {
   /** CheckRowColGroupEvent */
   public boolean hasRowGroup(RuntimeViewsheet rvs, VSAssemblyInfo info,
      int row, int col)
   {
      return checkRowColGroup(rvs, info, row, col, true);
   }

   public boolean hasColGroup(RuntimeViewsheet rvs, VSAssemblyInfo info,
      int row, int col)
   {
      return checkRowColGroup(rvs, info, row, col, false);
   }

   private boolean checkRowColGroup(RuntimeViewsheet rvs, VSAssemblyInfo info,
      int row, int col, boolean isRow) {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null ||
         !(info instanceof CalcTableVSAssemblyInfo))
      {
         return false;
      }

      CalcTableVSAssemblyInfo info0 = (CalcTableVSAssemblyInfo) info;
      TableLayout layout = info0.getTableLayout();
      CalcAttr attr = new CalcAttr(row, col);
      XNode tree = LayoutTool.buildTree(layout);
      XNode rroot = tree.getChild(0);
      XNode rparent = getParent(rroot, attr);
      boolean hasRowGroup = rparent != null && rparent != rroot;

      if(isRow) {
         return hasRowGroup;
      }

      XNode croot = tree.getChild(1);
      XNode cparent = getParent(croot, attr);
      boolean hasColGroup = cparent != null && cparent != croot;

      return hasColGroup;
   }

   private XNode getParent(XNode node, CalcAttr self) {
      for(int i = 0; i < node.getChildCount(); i++) {
         XNode child = node.getChild(i);
         CalcAttr attr = (CalcAttr) child.getValue();

         if(attr == null) {
            continue;
         }

         if(attr.getRow() == self.getRow() && attr.getCol() == self.getCol()) {
            return node;
         }

         XNode p = getParent(child, self);

         if(p != null) {
            return p;
         }
      }

      return null;
   }
}