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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class WSPrimaryService extends WorksheetControllerService {

   public WSPrimaryService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setAsPrimary(@ClusterProxyKey String runtiemId, WSAssemblyEvent event,
                            Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtiemId, principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.getAssemblyName();
      String old = ws.getPrimaryAssemblyName();

      if(name == null) {
         return null;
      }

      if(!name.equals(old)) {
         if(!ws.setPrimaryAssembly(name)) {
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.setPrimaryFailed"));
            command.setType(MessageCommand.Type.WARNING);
            command.setAssemblyName(name);
            commandDispatcher.sendCommand(command);
         }
         else {
            if(old != null) {
               AssemblyInfo info = ws.getPrimaryAssembly().getInfo();

               if(info instanceof TableAssemblyInfo) {
                  ((TableAssemblyInfo) info).setVisibleTable(true);
               }

               WorksheetEventUtil.refreshAssembly(rws, old, false, commandDispatcher, principal);
            }

            Assembly assembly = ws.getAssembly(name);

            if(assembly instanceof AbstractTableAssembly) {
               ((AbstractTableAssembly) assembly).setVisibleTable(true);
            }

            WorksheetEventUtil.refreshAssembly(rws, name, false, commandDispatcher, principal);
         }
      }

      return null;
   }
}
