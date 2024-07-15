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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The formula table assembly scriptable in viewsheet scope.
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public class CalcTableVSAScriptable extends TableDataVSAScriptable {
   /**
    * Create formula table assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public CalcTableVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   @Override
   protected void addProperties() {
      super.addProperties();

      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) getVSAssemblyInfo();
      addProperty("layoutInfo", layoutInfo = new VSTableLayoutInfo(this));
      addProperty("fillBlankWithZero", "isFillBlankWithZero",
                  "setFillBlankWithZero", boolean.class, info.getClass(), info);
      addProperty("sortOthersLast", "isSortOthersLast",
         "setSortOthersLast", boolean.class, info.getClass(), info);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "FormulaTableVSA";
   }

   /**
    * Set the size.
    * @param dim the dimension of size.
    */
   @Override
   public void setSize(Dimension dim) {
      if(getVSAssemblyInfo() instanceof CalcTableVSAssemblyInfo) {
         CalcTableVSAssemblyInfo calcInfo =
            (CalcTableVSAssemblyInfo) getVSAssemblyInfo();
         int width = calcInfo.getHeaderColCount() + 1;
         int height = calcInfo.getHeaderRowCount() + 2;

         if(dim.width < width || dim.height < height) {
            dim.width = width;
            dim.height = height;
         }
      }

      super.setSize(dim);
   }

   @Override
   protected Map<String, Object> getVarMap() {
      return varMap.computeIfAbsent(Thread.currentThread().getId(), (k) -> new HashMap<>());
   }

   @Override
   protected boolean isCrosstabOrCalc() {
      return true;
   }

   /**
    * Get the layout info of the cross tab.
    * @return the layoutInfo scriptable.
    */
   public VSTableLayoutInfo getLayoutInfo() {
      return layoutInfo;
   }

   private VSTableLayoutInfo layoutInfo;
   // Thread-scoped map for the script's local variables if this Scriptable is used as the scope.
   private final Map<Long, Map<String, Object>> varMap = new ConcurrentHashMap<>();
}
