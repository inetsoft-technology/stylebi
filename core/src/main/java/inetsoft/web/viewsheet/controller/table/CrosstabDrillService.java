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

package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.command.ClearSelectionCommand;
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.security.Principal;


@Service
@ClusterProxy
public class CrosstabDrillService extends BaseTableDrillService<DrillEvent> {
   public CrosstabDrillService(CoreLifecycleService coreLifecycleService,
                                  ViewsheetService viewsheetService,
                                  CrosstabDrillHandler crosstabDrillHandler,
                                  VSBindingService bindingFactory)
   {
      super(coreLifecycleService, viewsheetService, crosstabDrillHandler, bindingFactory);
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            DrillEvent event,
                            Principal principal,
                            CommandDispatcher dispatcher,
                            String linkUri) throws Exception
   {
      processDrill(runtimeId, event, principal, dispatcher, linkUri, true);
      dispatcher.sendCommand(event.getAssemblyName(), new ClearSelectionCommand());

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void drill(@ClusterProxyKey String runtimeId, DrillCellsEvent event, Principal principal,
                     CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      DrillCellsEvent.DrillTarget drillTarget = event.getDrillTarget();

      if(drillTarget == DrillCellsEvent.DrillTarget.CROSSTAB
         || drillTarget == DrillCellsEvent.DrillTarget.FIELD)
      {
         processDrill(runtimeId, null, principal, dispatcher, linkUri,
                      true, event.getAssemblyName(), drillTarget, event.isDrillUp(),
                      event.getField(), false);
      }
      else {
         for(int i = event.getDrillEvents().length - 1; i >=0 ; i--) {
            processDrill(runtimeId, event.getDrillEvents()[i], principal, dispatcher,
                         linkUri, i == 0);
         }
      }

      dispatcher.sendCommand(event.getAssemblyName(), new ClearSelectionCommand());
      return null;
   }
}
