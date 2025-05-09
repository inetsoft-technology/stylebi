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
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.CrosstabDrillFilterAction;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class CrosstabDrillActionService extends BaseTableDrillService<BaseTableDrillEvent> {
   public CrosstabDrillActionService(
                                     CoreLifecycleService coreLifecycleService,
                                     ViewsheetService viewsheetService,
                                     CrosstabDrillHandler crosstabDrillHandler,
                                     VSBindingService bindingFactory,
                                     VSDrillHandler vsDrillHandler)
   {
      super(coreLifecycleService, viewsheetService, crosstabDrillHandler, bindingFactory);

      this.vsDrillHandler = vsDrillHandler;
   }

   @Override
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void eventHandler(@ClusterProxyKey String runtimeId,
                            BaseTableDrillEvent event,
                            Principal principal,
                            CommandDispatcher dispatcher,
                            String linkUri) throws Exception
   {
      vsDrillHandler.processDrillAction(runtimeId, prepareDrillFilterInfo(event, event.getDrillEvents()),
                                        dispatcher, linkUri, principal);

      return null;
   }

   private CrosstabDrillFilterAction prepareDrillFilterInfo(BaseTableDrillEvent event,
                                                           DrillEvent[] drillEvents)
   {
      List<CrosstabDrillFilterAction.DrillCellInfo> drillCellInfos = new ArrayList<>();

      for(int i = 0; i < drillEvents.length; i++) {
         CrosstabDrillFilterAction.DrillCellInfo cellInfo
            = new CrosstabDrillFilterAction.DrillCellInfo();

         cellInfo.setRow(drillEvents[i].getRow())
            .setCol(drillEvents[i].getCol())
            .setField(drillEvents[i].getField())
            .setDirection(drillEvents[i].getDirection());

         drillCellInfos.add(cellInfo);
      }

      CrosstabDrillFilterAction drillFilterInfo = new CrosstabDrillFilterAction();
      drillFilterInfo
         .setCellInfos(drillCellInfos)
         .setFields(drillCellInfos.stream()
                       .map(CrosstabDrillFilterAction.DrillCellInfo::getField)
                       .collect(Collectors.toList()))
         .setDrillUp(event.isDrillUp())
         .setAssemblyName(event.getAssemblyName());

      return drillFilterInfo;
   }

   private final VSDrillHandler vsDrillHandler;
}
