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
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
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
   public TreeNodeModel getVisualizations(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
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

            AssetEntry vEntry = assetRepository.getAssetEntry(AssetEntry.createAssetEntry(visualization));

            if(vEntry == null) {
               LOG.warn("Asset entry could not be found for " + visualization);
               continue;
            }

            TreeNodeModel.Builder builder = TreeNodeModel.builder()
               .icon("viewsheet-icon")
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

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private static final Logger LOG = LoggerFactory.getLogger(VisualizationService.class);
}
