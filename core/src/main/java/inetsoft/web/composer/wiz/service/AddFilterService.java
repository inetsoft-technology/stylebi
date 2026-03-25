/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class AddFilterService {
   public AddFilterService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void addFilter(@ClusterProxyKey String runtimeId,
                         AssetEntry entry,
                         int xOffset, int yOffset, float scale,
                         Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         throw new Exception("Runtime viewsheet not found: " + runtimeId);
      }

      Viewsheet vs = rvs.getViewsheet();
      String tableName = entry.getProperty("assembly");
      String attribute = entry.getProperty("attribute");
      String dtype = entry.getProperty("dtype");

      if(tableName == null || attribute == null) {
         throw new IllegalArgumentException("Filter entry is missing required properties (assembly, attribute)");
      }

      // Build the column DataRef from the entry properties
      AttributeRef attrRef = new AttributeRef(attribute);
      attrRef.setDataType(dtype);
      ColumnRef colRef = new ColumnRef(attrRef);
      colRef.setDataType(dtype);

      // Find all root tables in the worksheet that expose this column (shared filter)
      List<String> matchingTables = findTablesWithColumn(vs, attribute);

      // Ensure the dropped table is first; include any others for shared filtering
      List<String> tableNames = new ArrayList<>();
      tableNames.add(tableName);

      for(String t : matchingTables) {
         if(!t.equals(tableName)) {
            tableNames.add(t);
         }
      }

      // Create the appropriate filter assembly
      AbstractSelectionVSAssembly assembly = createFilterAssembly(vs, dtype, colRef);
      assembly.setTableNames(tableNames);

      // Apply the drop position
      float effectiveScale = scale > 0f ? scale : 1f;
      int x = (int) (xOffset / effectiveScale);
      int y = (int) (yOffset / effectiveScale);
      assembly.setPixelOffset(new Point(x, y));

      vs.addAssembly(assembly);

      return null;
   }

   /**
    * Returns the names of all visible root tables in the base worksheet that
    * contain a column matching the given {@code attribute} name (alias-first lookup).
    */
   private List<String> findTablesWithColumn(Viewsheet vs, String attribute) {
      Worksheet ws = vs.getBaseWorksheet();

      if(ws == null) {
         return Collections.emptyList();
      }

      List<String> result = new ArrayList<>();

      for(Assembly a : ws.getAssemblies()) {
         if(!(a instanceof TableAssembly table) || !table.isVisible() || !table.isVisibleTable()) {
            continue;
         }

         // Only root tables (no other assembly depends on them)
         if(ws.getDependings(a.getAssemblyEntry()).length > 0) {
            continue;
         }

         ColumnSelection cols = table.getColumnSelection(true);

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            if(!(cols.getAttribute(i) instanceof ColumnRef cref)) {
               continue;
            }

            String colName = cref.getAlias() != null && !cref.getAlias().isEmpty()
               ? cref.getAlias() : cref.getAttribute();

            if(colName.equals(attribute)) {
               result.add(a.getName());
               break;
            }
         }
      }

      return result;
   }

   /**
    * Creates a {@link SelectionListVSAssembly} or {@link TimeSliderVSAssembly} based on
    * the column data type:
    * <ul>
    *   <li>date / time / timeInstant → TimeSlider (RangeSlider)</li>
    *   <li>numeric types → TimeSlider with NUMBER range</li>
    *   <li>everything else → SelectionList</li>
    * </ul>
    */
   private AbstractSelectionVSAssembly createFilterAssembly(Viewsheet vs, String dtype,
                                                            ColumnRef colRef)
   {
      if(XSchema.isNumericType(dtype) || XSchema.isDateType(dtype)) {
         String name = uniqueName("RangeSlider", vs);
         TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, name);
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setDataRef(colRef);

         if(XSchema.isNumericType(dtype)) {
            tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         }
         else if(XSchema.TIME.equals(dtype)) {
            tinfo.setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
         }
         else {
            tinfo.setRangeTypeValue(TimeInfo.MONTH);
         }

         slider.getTimeSliderInfo().setTimeInfo(tinfo);
         return slider;
      }
      else {
         String name = uniqueName("SelectionList", vs);
         SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
         list.setDataRef(colRef);
         return list;
      }
   }

   /**
    * Returns the first available name of the form {@code base + N} (N = 1, 2, …) that
    * does not already exist as an assembly in {@code vs}.
    */
   private String uniqueName(String base, Viewsheet vs) {
      int counter = 1;

      while(vs.getAssembly(base + counter) != null) {
         counter++;
      }

      return base + counter;
   }

   private final ViewsheetService viewsheetService;
}
