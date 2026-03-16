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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WizViewsheetService {

   public WizViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void updateWizSheetByCopyVisualization(@ClusterProxyKey String rId, String entryId, Principal principal) throws Exception
   {
      return updateWizSheetByCopyVisualization(viewsheetService.getViewsheet(rId, principal), entryId);
   }

   public static Void updateWizSheetByCopyVisualization(RuntimeViewsheet rvs, String entryId) throws Exception {
      if(rvs == null || rvs.getViewsheet() == null || rvs.getViewsheet().getWizInfo() == null ||
         !rvs.getViewsheet().getWizInfo().isWizSheet())
      {
         throw new IllegalArgumentException("Invalid Viewsheet");
      }

      AssetEntry visualizationEntry = AssetEntry.createAssetEntry(entryId);

      if(visualizationEntry == null) {
         throw new IllegalArgumentException("Invalid Viewsheet");
      }

      AssetEntry originalEntry = VSUtil.createWizOriginalVisualization(visualizationEntry);
      Viewsheet.WizInfo wizInfo = rvs.getViewsheet().getWizInfo();
      wizInfo.removeVisualization(originalEntry.toIdentifier());
      wizInfo.addVisualization(entryId);

      return null;
   }

   private final ViewsheetService viewsheetService;
}
