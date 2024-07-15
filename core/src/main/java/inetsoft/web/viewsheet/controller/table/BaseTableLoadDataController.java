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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.viewsheet.ExecutionMonitoring;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.event.table.LoadTableDataEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class BaseTableLoadDataController extends BaseTableController<LoadTableDataEvent> {
   @Autowired
   public BaseTableLoadDataController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      PlaceholderService placeholderService,
                                      ViewsheetService viewsheetService)
   {
      super(runtimeViewsheetRef, placeholderService, viewsheetService);
   }

   @Override
   @LoadingMask
   @ExecutionMonitoring
   @MessageMapping("/table/reload-table-data")
   public void eventHandler(@Payload LoadTableDataEvent event, Principal principal,
                            CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      String assemblyName = event.getAssemblyName();
      int start = event.getStart();
      int rowCount = event.getRowCount();
      int mode = 0;
      loadTableData(rvs, assemblyName, mode, start, rowCount, linkUri, dispatcher);
   }
}
