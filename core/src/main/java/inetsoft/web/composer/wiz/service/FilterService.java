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
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class FilterService {
   public FilterService(ViewsheetService viewsheetService, AssetRepository assetRepository) {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel getFilters(@ClusterProxyKey String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null || rvs.getViewsheet() == null) {
         return TreeNodeModel.builder().build();
      }

      Viewsheet vs = rvs.getViewsheet();
      AssetEntry baseEntry = vs.getBaseEntry();
      Worksheet ws = null;

      // For a wiz dashboard, baseEntry points to a temporary wiz worksheet that has been
      // enriched with MirrorTableAssembly instances (one per visualization) on top of the
      // original BoundTableAssembly source tables. We load this merged worksheet from the
      // repository (not vs.getBaseWorksheet(), which may be stale) and then filter it to
      // root tables only — those whose getDependeds set is empty. Root tables are always
      // BoundTableAssembly (the original source tables); the wiz mirrors are derived and
      // are excluded. This gives the correct set of filterable columns.
      if(baseEntry != null && baseEntry.isWorksheet()) {
         ws = (Worksheet) assetRepository.getSheet(baseEntry, principal, false, AssetContent.ALL);
      }

      if(ws == null) {
         return TreeNodeModel.builder().build();
      }

      List<TreeNodeModel> tableNodes = new ArrayList<>();
      IdentityID pId = principal == null ? null
         : IdentityID.getIdentityIDFromKey(principal.getName());

      for(Assembly assembly : ws.getAssemblies()) {
         if(!(assembly instanceof TableAssembly table) || !table.isVisible()) {
            continue;
         }

         if(!table.isVisibleTable()) {
            continue;
         }

         // Only include root tables: tables that do not depend on any other assembly.
         // In the wiz merged worksheet, BoundTableAssembly (source tables) have an empty
         // dependeds set; MirrorTableAssembly (wiz-internal derived views) do not.
         Set<AssemblyRef> dependeds = new HashSet<>();
         assembly.getDependeds(dependeds);

         if(!dependeds.isEmpty()) {
            continue;
         }

         String tableName = assembly.getName();
         ColumnSelection columns = table.getColumnSelection(true);
         List<TreeNodeModel> columnNodes = new ArrayList<>();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            if(!(columns.getAttribute(i) instanceof ColumnRef cref)) {
               continue;
            }

            String colName = cref.getAlias() != null && !cref.getAlias().isEmpty()
               ? cref.getAlias() : cref.getAttribute();

            AssetEntry colEntry = new AssetEntry(
               AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
               "/baseWorksheet/" + tableName + "/" + colName, pId);
            colEntry.setProperty("dtype", cref.getDataType());
            colEntry.setProperty("assembly", tableName);
            colEntry.setProperty("attribute", colName);
            colEntry.setProperty("source", "baseWorksheet");
            colEntry.setProperty("type", XSourceInfo.ASSET + "");

            columnNodes.add(TreeNodeModel.builder()
               .label(colName)
               .type(AssetEntry.Type.COLUMN.name())
               .leaf(true)
               .dragName("dragFilter")
               .data(colEntry)
               .build());
         }

         if(!columnNodes.isEmpty()) {
            tableNodes.add(TreeNodeModel.builder()
               .label(assembly.getName())
               .icon("data-table-icon")
               .children(columnNodes)
               .build());
         }
      }

      return TreeNodeModel.builder()
         .children(tableNodes)
         .build();
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
}
