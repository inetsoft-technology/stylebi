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

package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.CancelViewsheetLoadingEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class CancelViewsheetLoadingService {

   public CancelViewsheetLoadingService(ViewsheetService viewsheetService,
                                        CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void cancelViewsheet(@ClusterProxyKey String rId, CancelViewsheetLoadingEvent event, String linkUri,
                               Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(rId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      box.cancelAllQueries();

      if(event.isMeta() && !vs.getViewsheetInfo().isMetadata()) {
         vs.getViewsheetInfo().setMetadata(true);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
                                               false, true, true, new ChangedAssemblyList());
      }

      if(event.isIniting() && event.isPreview()) {
         MessageCommand cancelMessageCommand = new MessageCommand();
         cancelMessageCommand.setMessage("Viewsheet preview cancelled");
         cancelMessageCommand.setType(MessageCommand.Type.INFO);
         dispatcher.sendCommand(cancelMessageCommand);
      }

      return null;
   }

   private ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
}
