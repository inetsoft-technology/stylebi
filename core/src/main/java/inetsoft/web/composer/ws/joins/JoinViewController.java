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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.HandleAssetExceptions;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class JoinViewController extends WorksheetController {
   @LoadingMask
   @MessageMapping("/composer/ws/join/open-join/")
   @HandleAssetExceptions
   public void openJoin(Principal principal) throws Exception {
      final RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      rws.cloneWS();
   }

   @LoadingMask
   @MessageMapping("/composer/ws/join/cancel-ws-join/")
   @HandleAssetExceptions
   public void cancelJoin(Principal principal, CommandDispatcher dispatcher) throws Exception {
      final RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      WorksheetService engine = super.getWorksheetEngine();
      rws.cancelJoin();
      WorksheetEventUtil.refreshWorksheet(rws, engine, false, false, dispatcher, principal);

      UpdateUndoStateCommand command = new UpdateUndoStateCommand();
      command.setPoints(rws.size());
      command.setCurrent(rws.getCurrent());
      command.setSavePoint(rws.getSavePoint());
      dispatcher.sendCommand(command);
   }
}
