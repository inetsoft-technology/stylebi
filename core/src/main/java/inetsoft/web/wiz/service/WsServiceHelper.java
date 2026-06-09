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
            AttributeRef attrRef = new AttributeRef(ta.getName(), colName);
            attrRef.setDataType(attr.getDataType());
            ColumnRef col = new ColumnRef(attrRef);
            col.setVisible(needAddColumn(composite, cs, ta.getName(), colName));
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
