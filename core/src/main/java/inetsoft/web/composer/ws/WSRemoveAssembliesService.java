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
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.delete.*;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSRemoveAssembliesEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class WSRemoveAssembliesService extends WorksheetControllerService {

   public WSRemoveAssembliesService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String hasSourceDependency(@ClusterProxyKey String rid, boolean all, WSRemoveAssembliesEvent event, Principal principal) throws Exception
   {
      WorksheetService engine = getWorksheetEngine();
      RuntimeWorksheet rws = engine.getWorksheet(rid, principal);
      String source = rws.getEntry().toIdentifier();
      List<DeleteInfo> dinfo = new ArrayList<>();
      String[] tables = event.assemblyNames();
      Worksheet ws = rws.getWorksheet();
      String primary = ws.getPrimaryAssemblyName();

      for(int i = 0; i < tables.length; i++) {
         DeleteInfo info = new DeleteInfo(tables[i],
                                          RenameInfo.ASSET | RenameInfo.TABLE, source, tables[i]);

         if(Tool.equals(primary, tables[i])) {
            info.setPrimary(true);
         }

         dinfo.add(info);
      }

      DeleteDependencyInfo info = DeleteDependencyHandler.createWsDependencyInfo(dinfo, rws);

      if(all) {
         return DeleteDependencyHandler.checkDependencyStatus(info);
      }
      else {
         return DeleteDependencyHandler.hasDependency(info).toString();
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void removeAssemblies(@ClusterProxyKey String runtimeId, WSRemoveAssembliesEvent event,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String[] names = event.assemblyNames();
      Set<String> nameset = new HashSet<>();

      // build nameset
      for(int i = 0; i < names.length; i++) {
         nameset.add(names[i]);
      }

      for(int i = 0; i < names.length; i++) {
         Assembly assembly = ws.getAssembly(names[i]);

         if(assembly != null) {
            if(AssetEventUtil.hasDependent(assembly, ws, nameset)) {
               MessageCommand command = new MessageCommand();
               command.setMessage(Catalog.getCatalog().
                                     getString("assembly.remove.dependency"));
               command.setType(MessageCommand.Type.WARNING);
               command.setAssemblyName(assembly.getName());
               commandDispatcher.sendCommand(command);
               continue;
            }

            WorksheetEventUtil.removeAssembly(rws, assembly, commandDispatcher);
         }
      }

      return null;
   }
}
