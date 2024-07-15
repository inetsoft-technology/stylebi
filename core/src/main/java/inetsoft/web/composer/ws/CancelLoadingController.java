/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.XFactory;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CancelLoadingController extends WorksheetController {
   /**
    * From 12.2 LoadingMetaDataEvent.
    */
   @MessageMapping("/composer/worksheet/cancel-loading")
   public void cancelLoading(
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      Worksheet ws = rws.getWorksheet();

      if(box == null || ws == null) {
         return;
      }

      Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) assembly;
            table.setEditMode(false);
            table.setLiveData(false);
            table.setRuntime(false);
            table.setRuntimeSelected(false);
         }
      }

      if(box.getQueryManager() != null) {
         box.getQueryManager().cancel();
      }

      XFactory.getRepository().refreshMetaData();
      box.reset();
      WorksheetEventUtil.refreshWorksheet(
         rws, getWorksheetEngine(), false, false, commandDispatcher, principal);
   }
}
