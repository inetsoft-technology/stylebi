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

package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.LayoutGraphService;
import inetsoft.web.composer.ws.event.WSLayoutGraphEvent;
import inetsoft.web.wiz.model.WorksheetColumnInfo;
import inetsoft.web.wiz.model.osi.*;

import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.service.GenerateWsService.WORKSHEET_ROOT_FOLDER_PATH;

/**
 * Shared worksheet-building helpers used by both {@link GenerateWsService}
 * and {@link WorksheetTableService}.
 */
final class WsServiceHelper {

   private WsServiceHelper() {}

   static String extractFieldType(ObjectMapper objectMapper, OsiField osiField) {
      if(osiField.getCustomExtensions() == null) {
         return null;
      }

      for(OsiCustomExtension ext : osiField.getCustomExtensions()) {
         if("COMMON".equals(ext.getVendorName()) && ext.getData() != null) {
            try {
               @SuppressWarnings("unchecked")
               Map<String, Object> data = objectMapper.readValue(ext.getData(), Map.class);
               Object type = data.get("type");
               return type != null ? type.toString() : null;
            }
            catch(Exception e) {
               // ignore parse errors
            }
         }
      }

      return null;
   }

   /**
    * Describe the columns of the worksheet's primary (binding) table for the visualization layer.
    * <p>
    * For each column:
    * <ul>
    *   <li>name  – the underlying DB column name (via {@code ColumnRef.getDataRef()}, bypassing the
    *       alias override of {@code getAttribute()}).</li>
    *   <li>alias – the explicitly-set alias when it differs from the DB column name, else null.</li>
    *   <li>type  – the column data type (XSchema type), used to classify dimension vs measure.</li>
    * </ul>
    * {@code alias ?? name} always equals what {@code c.getAttribute()} returns, the key used by
    * {@code WizAutoBindingService.autoBinding}'s column filter.
    * <p>
    * Note: {@code type} may be null when a column's data type was never set (e.g. metadata with no
    * type); consumers must handle null types. Mirror-table primaries yield an empty list (their
    * columns carry no sub-table entity name).
    *
    * @param worksheet owning worksheet, used to resolve sub-table source info for composite primary
    *        tables; may be null only for a physical primary table (it is not dereferenced in that
    *        path). A null worksheet with a composite primary table leaves source fields unresolved.
    * @param physicalDbTableNameOverride when non-null, used as the DB table name for a physical
    *        primary table (the flat path resolves this from the construction model); null falls back
    *        to the assembly name.
    */
   static List<WorksheetColumnInfo> extractPrimaryTableFields(
      Worksheet worksheet,
      AbstractTableAssembly primaryTable,
      String physicalDbTableNameOverride)
   {
      if(primaryTable == null) {
         return Collections.emptyList();
      }

      ColumnSelection cs = primaryTable.getColumnSelection(true);
      List<WorksheetColumnInfo> result = new ArrayList<>(cs.getAttributeCount());

      if(primaryTable instanceof PhysicalBoundTableAssembly physTable) {
         String dbTableName = physicalDbTableNameOverride != null
            ? physicalDbTableNameOverride : physTable.getName();

         SourceInfo si = physTable.getSourceInfo();
         String path = si != null && si.getPrefix() != null ? si.getPrefix() : "";
         String schema = si != null && si.getProperty(SourceInfo.SCHEMA) != null ? si.getProperty(SourceInfo.SCHEMA) : "";
         String catalog = si != null && si.getProperty(SourceInfo.CATALOG) != null ? si.getProperty(SourceInfo.CATALOG) : "";

         for(int i = 0; i < cs.getAttributeCount(); i++) {
            DataRef attr = cs.getAttribute(i);

            // ColumnRef.getAttribute() returns the alias when one is set (StyleBI override).
            // Go through getDataRef() to reach the underlying AttributeRef and get the true DB column name.
            DataRef underlying = attr instanceof ColumnRef cr && cr.getDataRef() != null
               ? cr.getDataRef() : attr;
            String name = underlying.getAttribute();

            String colRefAlias = attr instanceof ColumnRef cr && !Tool.isEmptyString(cr.getAlias())
               ? cr.getAlias() : null;
            String alias = colRefAlias != null && !colRefAlias.equals(name) ? colRefAlias : null;
            String description = attr instanceof ColumnRef cr && !Tool.isEmptyString(cr.getDescription())
               ? cr.getDescription() : null;
            WorksheetColumnInfo info = new WorksheetColumnInfo(
               name, alias, attr.getDataType(), dbTableName, schema, catalog, path, description);
            result.add(info);
         }
      }
      else {
         // Non-physical primary table (RelationalJoinTableAssembly and other composites):
         // each column's entity is the sub-table assembly name, used to resolve the DB table name.
         // Note: MirrorTableAssembly columns typically lack an entity name and will be skipped
         // by the Tool.isEmptyString(subTableName) check below, so the result is empty for mirror
         // primaries.
         for(int i = 0; i < cs.getAttributeCount(); i++) {
            DataRef attr = cs.getAttribute(i);
            String worksheetColName = attr.getAttribute(); // base alias or DB column name
            String subTableName = attr.getEntity();

            if(Tool.isEmptyString(subTableName)) {
               continue;
            }

            String dbColumnName = worksheetColName; // fallback if sub-table is not a physical table
            String path = "";
            String schema = "";
            String catalog = "";
            String description = attr instanceof ColumnRef cr && !Tool.isEmptyString(cr.getDescription())
               ? cr.getDescription() : null;

            // worksheet may be null only if a caller passes a composite primary table without its
            // owning worksheet; treat the sub-table as unresolvable and fall back to the column name.
            Assembly subAssembly = worksheet != null ? worksheet.getAssembly(subTableName) : null;

            if(subAssembly instanceof PhysicalBoundTableAssembly subPhys) {
               SourceInfo si = subPhys.getSourceInfo();
               path = si != null && si.getPrefix() != null ? si.getPrefix() : "";
               schema = si != null && si.getProperty(SourceInfo.SCHEMA) != null ? si.getProperty(SourceInfo.SCHEMA) : "";
               catalog = si != null && si.getProperty(SourceInfo.CATALOG) != null ? si.getProperty(SourceInfo.CATALOG) : "";

               ColumnSelection subCS = subPhys.getColumnSelection(false);

               for(int j = 0; j < subCS.getAttributeCount(); j++) {
                  DataRef subAttr = subCS.getAttribute(j);
                  String subAlias = subAttr instanceof ColumnRef sc && !Tool.isEmptyString(sc.getAlias())
                     ? sc.getAlias() : null;
                  String subColName = subAlias != null ? subAlias : subAttr.getAttribute();

                  if(worksheetColName.equals(subColName)) {
                     DataRef subUnderlying = subAttr instanceof ColumnRef sc && sc.getDataRef() != null
                        ? sc.getDataRef() : subAttr;
                     dbColumnName = subUnderlying.getAttribute();
                     break;
                  }
               }
            }

            String alias = dbColumnName.equals(worksheetColName) ? null : worksheetColName;

            WorksheetColumnInfo info = new WorksheetColumnInfo(
               dbColumnName, alias, attr.getDataType(), subTableName, schema, catalog, path, description);
            result.add(info);
         }
      }

      return result;
   }

   static AssetEntry persistWorksheet(ViewsheetService viewsheetService,
                                      Worksheet worksheet, Principal user)
      throws Exception
   {
      AssetRepository repo = viewsheetService.getAssetRepository();
      AssetEntry folder = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, WORKSHEET_ROOT_FOLDER_PATH, null);

      if(!repo.containsEntry(folder)) {
         try {
            repo.addFolder(folder, user);
         }
         catch(Exception e) {
            if(!repo.containsEntry(folder)) {
               throw e;
            }
         }
      }

      IdentityID pId = IdentityID.getIdentityIDFromKey(user.getName());
      String path = WORKSHEET_ROOT_FOLDER_PATH + "/" + UUID.randomUUID();
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, pId);
      viewsheetService.setWorksheet(worksheet, entry, user, true, true);
      return entry;
   }

   static void layoutGraph(LayoutGraphService layoutGraphService, Worksheet worksheet)
      throws Exception
   {
      WSLayoutGraphEvent.Builder builder = new WSLayoutGraphEvent.Builder();
      Assembly[] assemblies = worksheet.getAssemblies();
      int[] heights = new int[assemblies.length];
      int[] widths = new int[assemblies.length];
      String[] tables = new String[assemblies.length];

      for(int i = 0; i < assemblies.length; i++) {
         heights[i] = 62;
         widths[i] = 150;
         tables[i] = assemblies[i].getName();
      }

      builder.heights(heights);
      builder.widths(widths);
      builder.names(tables);
      layoutGraphService.layoutGraph(worksheet, builder.build());
   }

   static void initCompositeColumnSelection(CompositeTableAssembly composite) {
      if(composite == null) {
         return;
      }

      ColumnSelection cs = new ColumnSelection();
      TableAssembly[] tableAssemblies = composite.getTableAssemblies();

      for(TableAssembly ta : tableAssemblies) {
         ColumnSelection baseCs = ta.getColumnSelection(true);

         for(int i = 0; i < baseCs.getAttributeCount(); i++) {
            DataRef attr = baseCs.getAttribute(i);
            String alias = attr instanceof ColumnRef cr ? cr.getAlias() : null;
            String colName = alias != null ? alias : attr.getAttribute();
            boolean visible = needAddColumn(composite, cs, ta.getName(), colName);
            AttributeRef attrRef = new AttributeRef(ta.getName(), colName);
            attrRef.setDataType(attr.getDataType());
            ColumnRef col = new ColumnRef(attrRef);

            // Disambiguate a visible column whose NAME collides with one already added from another
            // base table. Downstream resolution (ColumnSelection.getAttribute / AssetUtil.findColumn)
            // matches by attribute NAME, ignoring the entity/table qualifier — so two same-named
            // columns (e.g. a self-join's "month" exposed by both sides) are indistinguishable, which
            // makes the join render empty cells and any aggregation over it collapse to zero rows.
            // Suppressed equi-join keys are invisible (needAddColumn == false) and never reach this
            // branch; only genuinely-distinct duplicates (non-equi joins) do. Give the duplicate a
            // unique alias so it resolves, without touching the first (clean-named) occurrence.
            if(visible && cs.getAttribute(colName) != null) {
               String uniqueAlias = ta.getName() + "_" + colName;

               for(int suffix = 2; cs.getAttribute(uniqueAlias) != null; suffix++) {
                  uniqueAlias = ta.getName() + "_" + colName + "_" + suffix;
               }

               col.setAlias(uniqueAlias);
            }

            col.setVisible(visible);
            cs.addAttribute(col);
         }
      }

      composite.setColumnSelection(cs, false);
   }

   static boolean needAddColumn(CompositeTableAssembly composite, ColumnSelection cs,
                                String tableName, String colName)
   {
      if(composite instanceof RelationalJoinTableAssembly join) {
         if(cs.getAttribute(colName) == null) {
            return true;
         }

         Enumeration<TableAssemblyOperator> ops = join.getOperators();

         while(ops.hasMoreElements()) {
            for(TableAssemblyOperator.Operator op : ops.nextElement().getOperators()) {
               if(Tool.equals(op.getLeftAttribute(), op.getRightAttribute()) &&
                  (Tool.equals(tableName, op.getLeftTable()) || Tool.equals(tableName, op.getRightTable())) &&
                  (op.getOperation() == TableAssemblyOperator.INNER_JOIN ||
                   op.getOperation() == TableAssemblyOperator.RIGHT_JOIN ||
                   op.getOperation() == TableAssemblyOperator.LEFT_JOIN ||
                   op.getOperation() == TableAssemblyOperator.FULL_JOIN))
               {
                  return false;
               }
            }
         }
      }

      return true;
   }
}
