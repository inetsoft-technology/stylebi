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
import inetsoft.util.MessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
      Viewsheet vs;

      // Bug #77261: the stored identifier keeps its own orgID, so the READ check is what rejects
      // another org's viewsheet. A denial is treated the same as a missing sheet (default flags)
      // so that it neither drops the dashboard from the list nor fails the request.
      try {
         vs = (Viewsheet) viewsheetService.getAssetRepository().getSheet(
            entry, principal, true, AssetContent.CONTEXT);
      }
      catch(MessageException e) {
         LOG.debug("Dashboard viewsheet is not readable: {}", runtimeId, e);
         return null;
      }

      if(vs != null) {
         ViewsheetInfo info = vs.getViewsheetInfo();
         return new DashboardModelInfo(info.isComposedDashboard(), info.isScaleToScreen(),
                                       info.isFitToWidth(), vs.getBaseEntry() != null);
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(DashboardService.class);


   public static final class DashboardModelInfo implements Serializable {
      public boolean composedDashboard;
      public boolean scaleToScreen;
      public boolean fitToWidth;
      public boolean hasBaseEntry;

      public DashboardModelInfo(boolean composedDashboard, boolean scaleToScreen,
                                boolean fitToWidth, boolean hasBaseEntry)
      {
         this.composedDashboard = composedDashboard;
         this.scaleToScreen = scaleToScreen;
         this.fitToWidth = fitToWidth;
         this.hasBaseEntry = hasBaseEntry;
      }
   }

}
