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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
public class VisualizationService {
   public VisualizationService(ViewsheetService viewsheetService,
                               AssetRepository assetRepository)
   {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
   }

   public TreeNodeModel getVisualizations(String runtimeId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      AssetEntry vsEntry = rvs.getEntry();
      String vsId = vsEntry != null ? vsEntry.toIdentifier() : null;

      AssetEntry root = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", null);
      AssetEntry[] allEntries = assetRepository.getAllEntries(
         root, principal, ResourceAction.READ,
         new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET));

      List<TreeNodeModel> children = new ArrayList<>();

      for(AssetEntry entry : allEntries) {
         if("true".equals(entry.getProperty("isWizVisualization")) &&
            vsId != null && vsId.equals(entry.getProperty("visualizationSheet")))
         {
            children.add(TreeNodeModel.builder()
               .label(entry.getName())
               .data(entry)
               .leaf(true)
               .icon("viewsheet-icon")
               .dragName("dragVisualization")
               .build());
         }
      }

      return TreeNodeModel.builder()
         .children(children)
         .build();
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
}
