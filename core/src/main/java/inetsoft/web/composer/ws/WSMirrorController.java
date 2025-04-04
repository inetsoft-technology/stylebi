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
public class WSMirrorController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/mirror")
   public void addMirrorAssembly(
      @Payload WSAssemblyEvent event,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      WSAssembly assembly = (WSAssembly) rws.getWorksheet()
         .getAssembly(event.getAssemblyName());

      Worksheet ws = rws.getWorksheet();
//      String name = (String) get("name");
      int x = assembly.getPixelOffset().x;
      int y = assembly.getPixelOffset().y;
//      WSAssembly assembly = (WSAssembly) ws.getAssembly(name);
      WSAssembly nassembly;
      final String nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);

      if(assembly instanceof DateRangeAssembly) {
         nassembly = new MirrorDateRangeAssembly(ws, nname, null, false,
                                                 assembly);
      }
      else if(assembly instanceof ConditionAssembly) {
         nassembly = new MirrorConditionAssembly(ws, nname, null, false,
                                                 assembly);
      }
      else if(assembly instanceof NamedGroupAssembly) {
         nassembly = new MirrorNamedGroupAssembly(ws, nname, null, false,
                                                  assembly);
      }
      else if(assembly instanceof VariableAssembly) {
         nassembly = new MirrorVariableAssembly(ws, nname, null, false,
                                                assembly);
      }
      else {
         nassembly = new MirrorTableAssembly(ws, nname, null, false,
                                             assembly);
         AssetEventUtil.initColumnSelection(rws, (TableAssembly) nassembly);
         TableModeService.setDefaultTableMode((MirrorTableAssembly) nassembly,
                                                 rws.getAssetQuerySandbox());
      }

      nassembly.setPixelOffset(new Point(x, y));
      ws.addAssembly(nassembly);

      AssetEventUtil.layoutResultantTable(assembly, assembly, nassembly);
      WorksheetEventUtil.createAssembly(rws, nassembly, commandDispatcher, principal);
      WorksheetEventUtil.loadTableData(rws, nname, false, false);
      WorksheetEventUtil.refreshAssembly(rws, nname, false, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
   }
}
