/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CloseWorksheetController extends WorksheetController {
   @Autowired
   public CloseWorksheetController(RuntimeViewsheetManager runtimeViewsheetManager) {
      this.runtimeViewsheetManager = runtimeViewsheetManager;
   }

   @MessageMapping("/ws/close")
   public void closeWorksheet(Principal principal) throws Exception {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();

      // @by stephenwebster, For Bug #9865
      // When the worksheet is closed, eventually the TableLens in
      // AssetQuerySandbox are disposed, but this doesn't necessarily cancel
      // underlying table processing.  Implemented a cancel method to stop
      // processing for this AssetQuerySandbox when the worksheet is closed.
      if(box != null) {
         box.cancelTableLens();
      }

      getWorksheetEngine().closeWorksheet(getRuntimeId(), principal);
      runtimeViewsheetManager.sheetClosed(getRuntimeId());
      AssetEntry entry = rws.getEntry();
      VSEventUtil.deleteAutoSavedFile(entry, principal);
      AssetRepository rep = AssetUtil.getAssetRepository(false);
      ((AbstractAssetEngine)rep).fireAutoSaveEvent(entry);
   }

   private final RuntimeViewsheetManager runtimeViewsheetManager;
}
