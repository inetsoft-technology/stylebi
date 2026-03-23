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
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class VisualizationService {
   public VisualizationService(ViewsheetService viewsheetService, AssetRepository assetRepository) {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel getComponents(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);

      if(rvs == null || rvs.getViewsheet() == null || rvs.getViewsheet().getWizInfo() == null ||
         !rvs.getViewsheet().getWizInfo().isWizSheet() ||
         rvs.getViewsheet().getWizInfo().getVisualizations() == null ||
         rvs.getViewsheet().getWizInfo().getVisualizations().isEmpty())
      {
         return TreeNodeModel.builder().build();
      }

      List<TreeNodeModel> children = new ArrayList<>();

      for(String visualization : rvs.getViewsheet().getWizInfo().getVisualizations()) {
         try {
            AssetEntry assetEntry = AssetEntry.createAssetEntry(visualization);

            if(assetEntry == null) {
               LOG.warn("Invalid visualization entry for " + visualization);
               continue;
            }

            AssetEntry vEntry = assetRepository.getAssetEntry(assetEntry);

            if(vEntry == null) {
               LOG.warn("Asset entry could not be found for " + visualization);
               continue;
            }

            if(!Tool.equals(vEntry.getProperty("visualizationScope"), "private")) {
               continue;
            }

            TreeNodeModel.Builder builder = TreeNodeModel.builder()
               .icon("new-viewsheet-icon")
               .dragName("dragVisualization")
               .data(vEntry)
               .leaf(true);

            if(VSUtil.isWizCopyEntry(vEntry, true)) {
               AssetEntry wizOriginalVisualization = VSUtil.createWizOriginalVisualization(vEntry);
               builder.label(wizOriginalVisualization.getName());
            }
            else {
               builder.label(vEntry.getName());
            }

            children.add(builder.build());
         }
         catch(Exception e) {
            LOG.error("Failed to load visualization entry: {}", visualization, e);
         }
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TreeNodeModel getVisualizations(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      AssetEntry visualizationsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER,
         VISUALIZATION_ROOT_FOLDER_PATH, user);
      AssetEntry.Selector assetSelector = new AssetEntry.Selector(
         AssetEntry.Type.FOLDER, AssetEntry.Type.VIEWSHEET, AssetEntry.Type.REPOSITORY_FOLDER);
      AssetEntry[] entries = assetRepository.getEntries(
         visualizationsEntry, principal, ResourceAction.READ, assetSelector);
      List<TreeNodeModel> children = new ArrayList<>();

      if(entries != null) {
         for(AssetEntry entry : entries) {
            if(entry.isFolder()) {
               children.add(TreeNodeModel.builder()
                  .label(entry.getName())
                  .icon("folder-toolbox-icon")
                  .data(entry)
                  .leaf(false)
                  .build());
            }
            else if("shared".equals(entry.getProperty("visualizationScope"))) {
               TreeNodeModel.Builder builder = TreeNodeModel.builder()
                  .icon("new-viewsheet-icon")
                  .dragName("dragVisualization")
                  .data(entry)
                  .leaf(true);

               if(VSUtil.isWizCopyEntry(entry, true)) {
                  AssetEntry wizOriginalVisualization = VSUtil.createWizOriginalVisualization(entry);
                  builder.label(wizOriginalVisualization.getName());
               }
               else {
                  builder.label(entry.getName());
               }

               children.add(builder.build());
            }
         }
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   public static final String VISUALIZATION_ROOT_FOLDER_PATH = "visualizations-593bb4a4-fd6d-4178-b3f0-c89dad407f02";
   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(VisualizationService.class);
}
