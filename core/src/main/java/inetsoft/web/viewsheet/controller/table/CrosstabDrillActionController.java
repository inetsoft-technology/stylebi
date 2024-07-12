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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.table.BaseTableDrillEvent;
import inetsoft.web.viewsheet.event.table.DrillEvent;
import inetsoft.web.viewsheet.handler.VSDrillHandler;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.CrosstabDrillFilterAction;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class CrosstabDrillActionController extends BaseTableDrillController<BaseTableDrillEvent> {

   @Autowired
   public CrosstabDrillActionController(CrosstabDrillHandler crosstabDrillHandler,
                                        RuntimeViewsheetRef runtimeViewsheetRef,
                                        PlaceholderService placeholderService,
                                        ViewsheetService viewsheetService,
                                        VSBindingService bindingFactory,
                                        VSDrillHandler vsDrillHandler)
   {
      super(crosstabDrillHandler, runtimeViewsheetRef, placeholderService,
         viewsheetService, bindingFactory);

      this.vsDrillHandler = vsDrillHandler;
   }

   @Override
   @Undoable
   @LoadingMask
   @MessageMapping("/crosstab/action/drill")
   public void eventHandler(@Payload BaseTableDrillEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      vsDrillHandler.processDrillAction(prepareDrillFilterInfo(event, event.getDrillEvents()),
         dispatcher, linkUri, principal);
   }

   public CrosstabDrillFilterAction prepareDrillFilterInfo(BaseTableDrillEvent event,
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
