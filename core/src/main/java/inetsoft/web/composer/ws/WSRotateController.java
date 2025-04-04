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
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

@Controller
public class WSRotateController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/rotate")
   public void addRotateAssembly(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      WSAssembly assembly = (WSAssembly) rws.getWorksheet()
         .getAssembly(event.getAssemblyName());

      if(assembly instanceof TableAssembly) {
         int x = assembly.getPixelOffset().x;
         int y = assembly.getPixelOffset().y;

         final String nname = AssetUtil.getNextName(rws.getWorksheet(), AbstractSheet.TABLE_ASSET);
         RotatedTableAssembly table =
            new RotatedTableAssembly(rws.getWorksheet(), nname, (TableAssembly) assembly);

         table.setLiveData(true);
         table.setPixelOffset(new Point(x, y));

         rws.getWorksheet().addAssembly(table);
         AssetQuerySandbox box = rws.getAssetQuerySandbox();

         TableModeService.setDefaultTableMode(table, box);
         AssetEventUtil.layoutResultantTable(assembly, assembly, table);
         WorksheetEventUtil.createAssembly(rws, table, commandDispatcher, principal);
         WorksheetEventUtil.refreshColumnSelection(rws, nname, false);
         WorksheetEventUtil.loadTableData(rws, nname, false, false);
         WorksheetEventUtil.refreshAssembly(rws, nname, false, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }
   }
}
