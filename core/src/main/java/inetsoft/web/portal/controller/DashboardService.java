/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.portal.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.security.Principal;

@Service
@ClusterProxy
public class DashboardService {

   public DashboardService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public DashboardModelInfo getDashboardModelInfo(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(runtimeId);

      Viewsheet vs = (Viewsheet) viewsheetService.getAssetRepository().getSheet(
         entry, principal, false, AssetContent.CONTEXT);

      if(vs != null) {
         ViewsheetInfo info = vs.getViewsheetInfo();
         return new DashboardModelInfo(info.isComposedDashboard(), info.isScaleToScreen(), info.isFitToWidth());
      }

      return null;
   }

   private final ViewsheetService viewsheetService;


   public static final class DashboardModelInfo implements Serializable {
      public boolean composedDashboard;
      public boolean scaleToScreen;
      public boolean fitToWidth;

      public DashboardModelInfo(boolean composedDashboard, boolean scaleToScreen, boolean fitToWidth) {
         this.composedDashboard = composedDashboard;
         this.scaleToScreen = scaleToScreen;
         this.fitToWidth = fitToWidth;
      }
   }

}
