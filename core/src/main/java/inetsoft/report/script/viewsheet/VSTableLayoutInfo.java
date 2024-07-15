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
package inetsoft.report.script.viewsheet;

import inetsoft.report.TableLayout;
import inetsoft.report.script.TableLayoutInfo;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;

import java.awt.Dimension;

/**
 * TableLayoutInfo, scriptable to operate table layout info.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class VSTableLayoutInfo extends TableLayoutInfo {
   public VSTableLayoutInfo(CalcTableVSAScriptable tableScriptable) {
      this.tableScriptable = tableScriptable;
   }

   @Override
   protected void resetCalc() {
   }

   @Override
   protected String getID() {
      return getInfo().getName();
   }

   @Override
   protected TableLayout getTableLayout(boolean createIfNone) {
      return getInfo().getTableLayout();
   }

   @Override
   protected void setCellSpan(int row, int col, int spanw, int spanh) {
      getTableLayout(true).setSpan(row, col, new Dimension(spanw, spanh));
   }

    /**
    * Get element.
    */
   private CalcTableVSAssemblyInfo getInfo() {
      return tableScriptable.getCalcInfo();
   }

   /**
    * Get the object for getting and setting properties.
    */
   @Override
   protected Object getObject() {
      return tableScriptable.getCalcInfo();
   }

   private CalcTableVSAScriptable tableScriptable;
}
