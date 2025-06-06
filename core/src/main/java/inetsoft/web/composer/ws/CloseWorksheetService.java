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

package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class CloseWorksheetService extends WorksheetControllerService {
   public CloseWorksheetService(ViewsheetService viewsheetService,
                                RuntimeViewsheetManager runtimeViewsheetManager)
   {
      super(viewsheetService);
      this.runtimeViewsheetManager = runtimeViewsheetManager;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void closeWorksheet(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      RuntimeWorksheet rws = getRuntimeWorksheet(runtimeId, principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      // @by stephenwebster, For Bug #9865
      // When the worksheet is closed, eventually the TableLens in
      // AssetQuerySandbox are disposed, but this doesn't necessarily cancel
      // underlying table processing.  Implemented a cancel method to stop
      // processing for this AssetQuerySandbox when the worksheet is closed.
      if(box != null) {
         box.cancelTableLens();
      }

      getWorksheetEngine().closeWorksheet(runtimeId, principal);
      runtimeViewsheetManager.sheetClosed(principal, runtimeId);
      AssetEntry entry = rws.getEntry();
      VSEventUtil.deleteAutoSavedFile(entry, principal);
      AssetRepository rep = AssetUtil.getAssetRepository(false);
      ((AbstractAssetEngine)rep).fireAutoSaveEvent(entry);
      return null;
   }

   private final RuntimeViewsheetManager runtimeViewsheetManager;
}
