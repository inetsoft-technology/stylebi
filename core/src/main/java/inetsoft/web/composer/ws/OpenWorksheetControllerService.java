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

package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.composer.model.ws.WorksheetModel;
import inetsoft.web.composer.ws.command.OpenWorksheetCommand;
import inetsoft.web.composer.ws.command.WSInitCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@ClusterProxy
public class OpenWorksheetControllerService {
   public OpenWorksheetControllerService(ViewsheetService viewsheetService,
                                         RuntimeViewsheetManager runtimeViewsheetManager)
   {
      this.engine = viewsheetService;
      this.runtimeViewsheetManager = runtimeViewsheetManager;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void processNewWorksheet(@ClusterProxyKey String runtimeId, Principal principal,
                                   CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = engine.getWorksheet(runtimeId, principal);
      AssetEntry entry = rws.getEntry();

      WorksheetModel worksheet = new WorksheetModel();
      worksheet.setId(entry.toIdentifier());
      worksheet.setRuntimeId(runtimeId);
      worksheet.setLabel(rws.getEntry().getName());
      worksheet.setType("worksheet");
      worksheet.setNewSheet(true);
      worksheet.setInit(true);
      worksheet.setCurrent(rws.getCurrent());
      worksheet.setSavePoint(rws.getSavePoint());

      OpenWorksheetCommand command = new OpenWorksheetCommand();
      command.setWorksheet(worksheet);
      runtimeViewsheetManager.sheetOpened(principal, runtimeId);
      commandDispatcher.sendCommand(command);
      commandDispatcher.sendCommand(new WSInitCommand(principal));

      return null;
   }

   private final WorksheetService engine;
   private final RuntimeViewsheetManager runtimeViewsheetManager;
}
