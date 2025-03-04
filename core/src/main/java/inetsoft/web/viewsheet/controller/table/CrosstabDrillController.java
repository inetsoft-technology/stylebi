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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.ClearSelectionCommand;
import inetsoft.web.viewsheet.event.table.DrillCellsEvent;
import inetsoft.web.viewsheet.event.table.DrillEvent;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class CrosstabDrillController extends BaseTableDrillController<DrillEvent> {

   @Autowired
   public CrosstabDrillController(CrosstabDrillHandler crosstabDrillHandler,
                                  RuntimeViewsheetRef runtimeViewsheetRef,
                                  CoreLifecycleService coreLifecycleService,
                                  ViewsheetService viewsheetService,
                                  VSBindingService bindingFactory)
   {
      super(crosstabDrillHandler, runtimeViewsheetRef, coreLifecycleService,
            viewsheetService, bindingFactory);
   }

   @Override
   @Undoable
   @LoadingMask
   @MessageMapping("/table/drill")
   public void eventHandler(@Payload DrillEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      processDrill(event, principal, dispatcher, linkUri, true);
      dispatcher.sendCommand(event.getAssemblyName(), new ClearSelectionCommand());
   }

   @Undoable
   @LoadingMask(true)
   @MessageMapping("/table/drill/cells")
   public void drill(@Payload DrillCellsEvent event, Principal principal,
                     CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      DrillCellsEvent.DrillTarget drillTarget = event.getDrillTarget();

      if(drillTarget == DrillCellsEvent.DrillTarget.CROSSTAB
         || drillTarget == DrillCellsEvent.DrillTarget.FIELD)
      {
         processDrill(null, principal, dispatcher, linkUri,
            true, event.getAssemblyName(), drillTarget, event.isDrillUp(),
            event.getField(), false);
      }
      else {
         for(int i = event.getDrillEvents().length - 1; i >=0 ; i--) {
            processDrill(event.getDrillEvents()[i], principal, dispatcher,
               linkUri, i == 0);
         }
      }

      dispatcher.sendCommand(event.getAssemblyName(), new ClearSelectionCommand());
   }
}
