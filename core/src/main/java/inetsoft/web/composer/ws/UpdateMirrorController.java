/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Catalog;
import inetsoft.util.log.LogLevel;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class UpdateMirrorController extends WorksheetController {
   @LoadingMask
   @MessageMapping("/composer/worksheet/update-mirror")
   public void updateMirror(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.getAssemblyName();
      MirrorAssembly assembly = (MirrorAssembly) ws.getAssembly(name);

      if(assembly != null) {
         WorksheetService engine = getWorksheetEngine();
         assembly.updateMirror(engine.getAssetRepository(), principal);
         Assembly sassembly = assembly.getAssembly();
         int stype = sassembly == null ? -1 : sassembly.getAssemblyType();

         if(stype != ((Assembly) assembly).getAssemblyType()) {
            String msg = Catalog.getCatalog().getString(
               "common.mirrorAssembly.updateFailed");
            throw new RuntimeMessageException(msg, LogLevel.DEBUG);
         }

         Assembly[] assemblies =
            AssetUtil.getDependedAssemblies(ws, (WSAssembly) assembly, true);
         AssetQuerySandbox box = rws.getAssetQuerySandbox();

         for(int i = 0; i < assemblies.length; i++) {
            box.resetDefaultColumnSelection(assemblies[i].getName());

            if(!assemblies[i].getName().equals(name)) {
               box.refreshColumnSelection(assemblies[i].getName(), false);
            }

            if(assembly instanceof TableAssembly && sassembly != null) {
               clearTableLensCache((TableAssembly) assembly, box, AssetQuerySandbox.LIVE_MODE);
               clearTableLensCache(((TableAssembly) sassembly), box,
                                   PreAssetQuery.fixSubQueryMode(AssetQuerySandbox.LIVE_MODE));
            }
         }

         WorksheetEventUtil.refreshColumnSelection(rws, name, true);
         WorksheetEventUtil.loadTableData(rws, name, true, true);
         WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }
   }

   private void clearTableLensCache(TableAssembly assembly, AssetQuerySandbox box, int mode)
      throws Exception
   {
      box.resetTableLens(assembly.getName());
      DataKey key = AssetDataCache.getCacheKey(assembly, box, null, mode, true);
      AssetDataCache.removeCachedData(key);
   }
}
