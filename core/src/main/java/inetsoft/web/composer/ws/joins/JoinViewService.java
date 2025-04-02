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

package inetsoft.web.composer.ws.joins;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class JoinViewService extends WorksheetControllerService {

   public JoinViewService(ViewsheetService viewsheetService) {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void openJoin(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      final RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      rws.cloneWS();

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void cancelJoin(@ClusterProxyKey String runtimeId, Principal principal, CommandDispatcher dispatcher) throws Exception {
      final RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      WorksheetService engine = super.getWorksheetEngine();
      rws.cancelJoin();
      WorksheetEventUtil.refreshWorksheet(rws, engine, false, false, dispatcher, principal);

      UpdateUndoStateCommand command = new UpdateUndoStateCommand();
      command.setPoints(rws.size());
      command.setCurrent(rws.getCurrent());
      command.setSavePoint(rws.getSavePoint());
      dispatcher.sendCommand(command);

      return null;
   }

}
