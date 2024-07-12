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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.rmi.RemoteException;

@Component
public class VSCalcTableBindingHandler {
   /**
    * Process the drag & drop action.
    *
    * @param assembly the vsassembly.
    */
   public void addRemoveColumns(CalcTableVSAssembly assembly,
      Rectangle removeRect, Rectangle addRect) throws Exception
   {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      layoutHandler.dropCell(info, removeRect, addRect, true);
   }

   /**
    * Process the drag & drop from tree action.
    *
    * @param assembly the vsassembly.
    */
   public void addColumns(CalcTableVSAssembly assembly, AssetEntry[] entries,
                          Rectangle rect, RuntimeViewsheet rvs)
      throws Exception
   {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      AssetEntry entry = entries[0];
      DataRef ref = createDataRef(entry, rvs, info.getSourceInfo());
      boolean isDim = isDimension(entry);
      layoutHandler.replaceCellBindings(info, (int)rect.getY(), (int)rect.getX(),
         ref, isDim);
   }

   /**
    * Process the drag & drop to tree action.
    *
    * @param assembly the vsassembly.
    * @param transfer the Dnd transfer.
    */
   public void removeColumns(CalcTableVSAssembly assembly,
      Rectangle rect) throws Exception
   {
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      layoutHandler.removeCell(info, rect);
   }

   /**
    * Create a dataref by assetentry.
    */
   private DataRef createDataRef(AssetEntry entry, RuntimeViewsheet rvs, SourceInfo sinfo)
      throws RemoteException
   {
      return isDimension(entry) ? createDim(entry) : createAgg(entry, rvs, sinfo);
   }

   /**
    * Check if the entry is dimension.
    */
   private boolean isDimension(AssetEntry entry) {
      String refType = entry.getProperty("refType");
      int rtype = refType == null ? AbstractDataRef.NONE : Integer.parseInt(refType);
      String cubeTypeStr = entry.getProperty(AssetEntry.CUBE_COL_TYPE);
      int ctype = cubeTypeStr == null ? 0 : Integer.parseInt(cubeTypeStr);

      return (rtype & AbstractDataRef.DIMENSION) != 0 || (ctype & 1) == 0;
   }

   /**
    * Create a dimension ref.
    */
   private VSDimensionRef createDim(AssetEntry entry) {
      VSDimensionRef dim = new VSDimensionRef();
      dim.setGroupColumnValue(getColumnValue(entry));
      dim.setDataType(entry.getProperty("dtype"));
      dim.setRefType(Integer.parseInt(entry.getProperty("refType")));

      return dim;
   }

   /**
    * Create a aggregate ref.
    */
   private VSAggregateRef createAgg(AssetEntry entry, RuntimeViewsheet rvs, SourceInfo sinfo)
      throws RemoteException
   {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(getColumnValue(entry));
      //agg.setDataType(entry.getProperty("dtype"));
      agg.setRefType(Integer.parseInt(entry.getProperty("refType")));
      String formula = VSBindingHelper.getModelDefaultFormula(entry, sinfo, rvs,
         analyticRepository);

      if(formula != null) {
         agg.setFormulaValue(formula);
      }
      else {
         agg.setFormula(AggregateFormula.getDefaultFormula(entry.getProperty("dtype")));
      }

      return agg;
   }

   /**
    * Get column value from entry.
    */
   private String getColumnValue(AssetEntry entry) {
      if(entry == null) {
         return "";
      }

      String cvalue = entry.getName();
      String attribute = entry.getProperty("attribute");

      // normal chart entry not set entity and attribute properties,
      // cube entry set, the name should use entity + attribute
      if(attribute != null) {
         String entity = entry.getProperty("entity");
         cvalue = (entity != null ?  entity + "." : "") + attribute;
      }

     return cvalue;
   }

   @Autowired
   private TableLayoutHandler layoutHandler;
   @Autowired
   private AnalyticRepository analyticRepository;
}