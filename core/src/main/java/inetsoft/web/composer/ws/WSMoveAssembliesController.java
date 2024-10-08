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
import inetsoft.uql.asset.AbstractWSAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSMoveAssembliesEvent;
import inetsoft.web.composer.ws.event.WSRelocateAssembliesEvent;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

@Controller
public class WSMoveAssembliesController extends WorksheetController {
   @Undoable
   @MessageMapping("/composer/worksheet/move-assemblies/offset")
   public void offsetAssemblies(
      @Payload WSMoveAssembliesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String[] names = event.getAssemblyNames();
      int x = event.getOffsetLeft();
      int y = event.getOffsetTop();

      for(int i = 0; i < names.length; i++) {
         AbstractWSAssembly assembly = (AbstractWSAssembly) ws.getAssembly(names[i]);

         if(assembly != null) {
            Point pos = assembly.getPixelOffset();
            pos = new Point(pos.x + x, pos.y + y);
            pos.x = Math.max(pos.x, 0);
            pos.y = Math.max(pos.y, 0);
            assembly.setPixelOffset(pos);
         }
      }

      WorksheetEventUtil.layout(rws, names, commandDispatcher);
   }

   @Undoable
   @MessageMapping("/composer/worksheet/move-assemblies/relocate")
   public void relocateAssemblies(
      @Payload WSRelocateAssembliesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String[] names = event.assemblyNames();

      for(int i = 0; i < names.length; i++) {
         AbstractWSAssembly assembly = (AbstractWSAssembly) ws.getAssembly(names[i]);

         if(assembly != null) {
            double left = event.lefts()[i];
            double top = event.tops()[i];
            Point pos = new Point();
            pos.setLocation(Math.max(left, 0), Math.max(top, 0));
            assembly.setPixelOffset(pos);
         }
      }

      WorksheetEventUtil.layout(rws, names, commandDispatcher);
   }
}
