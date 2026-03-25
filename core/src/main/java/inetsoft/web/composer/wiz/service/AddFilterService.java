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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class AddFilterService {
   public AddFilterService(ViewsheetService viewsheetService,
                           AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
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

      if(dtype == null) {
         dtype = "";
      }

      if(tableName == null || attribute == null) {
         throw new IllegalArgumentException("Filter entry is missing required properties (assembly, attribute)");
      }

      // Build the column DataRef from the entry properties
      AttributeRef attrRef = new AttributeRef(attribute);
      attrRef.setDataType(dtype);
      ColumnRef colRef = new ColumnRef(attrRef);
      colRef.setDataType(dtype);

      // Find all root tables in the worksheet that expose this column (shared filter).
      // Load from assetRepository so we see the latest saved state of the wiz temp
      // worksheet — vs.getBaseWorksheet() is stale after AddVisualizationService merges
      // tables into the worksheet and saves but does not reload the runtime object.
      List<String> matchingTables = findTablesWithColumn(vs, attribute, principal);

      if(!matchingTables.contains(tableName)) {
         // tableName was not among the root tables found for this column. This can happen
         // if the filter tree was built before the latest merge was saved, or if a non-root
         // table column was somehow included. Log a warning; the filter will still be created
         // with tableName as its primary binding, but shared-filter resolution may be incomplete.
         LOG.warn("Dropped table '{}' was not found among root tables for column '{}'; " +
                  "filter binding may be incorrect.", tableName, attribute);
      }

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
    * Returns the names of all visible root tables in the base worksheet that contain a column
    * matching the given {@code attribute} name (alias-first lookup).
    *
    * <p>The worksheet is loaded fresh from {@code assetRepository} rather than from
    * {@code vs.getBaseWorksheet()}, because {@link inetsoft.web.composer.wiz.service.AddVisualizationService}
    * saves the merged wiz temp worksheet to the repository without reloading the runtime
    * viewsheet object. Using the runtime object would return stale data that does not yet
    * include tables merged by subsequent visualization additions.</p>
    *
    * <p>The wiz temp worksheet contains both the original source
    * {@link BoundTableAssembly} instances and wiz-internal {@link MirrorTableAssembly}
    * instances. Only root tables (those whose {@code getDependeds} set is empty) are
    * considered, which correctly selects only the source tables.</p>
    */
   private List<String> findTablesWithColumn(Viewsheet vs, String attribute,
                                             Principal principal)
      throws Exception
   {
      AssetEntry baseEntry = vs.getBaseEntry();
      Worksheet ws = null;

      if(baseEntry != null && baseEntry.isWorksheet()) {
         ws = (Worksheet) assetRepository.getSheet(baseEntry, principal, false, AssetContent.ALL);
      }

      if(ws == null) {
         return Collections.emptyList();
      }

      List<String> result = new ArrayList<>();

      for(Assembly a : ws.getAssemblies()) {
         if(!(a instanceof TableAssembly table) || !table.isVisible() || !table.isVisibleTable()) {
            continue;
         }

         // Only root tables: skip tables that depend on another assembly (e.g. mirror/join tables).
         // Uses getDependeds() — the set of assemblies this table itself depends on — which is
         // non-empty for derived tables and empty for source/root tables.
         // Note: getDependings() returns the reverse (who depends on this table) and must NOT
         // be used here because it is non-empty for root tables, which is the opposite of what
         // we want.
         Set<AssemblyRef> dependeds = new HashSet<>();
         a.getDependeds(dependeds);

         if(!dependeds.isEmpty()) {
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
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(AddFilterService.class);
}
