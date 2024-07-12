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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.MirrorAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class MirrorAutoUpdateController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/mirror-auto-update")
   public void toggleAutoUpdate(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.getAssemblyName();
      MirrorAssembly assembly = (MirrorAssembly) ws.getAssembly(name);

      if(assembly != null) {
         assembly.setAutoUpdate(!assembly.isAutoUpdate());
         WorksheetEventUtil.refreshAssembly(rws, name, false, commandDispatcher, principal);
      }
   }
}
