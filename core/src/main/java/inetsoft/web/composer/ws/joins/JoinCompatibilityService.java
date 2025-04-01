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
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class JoinCompatibilityService extends WorksheetControllerService {

   public JoinCompatibilityService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<String> getCompatibleInsertionTables(@ClusterProxyKey String runtimeId,
                                                    String joinTableName, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      final RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);
      final Worksheet ws = rws.getWorksheet();
      final AbstractJoinTableAssembly joinTable =
         (AbstractJoinTableAssembly) ws.getAssembly(joinTableName);
      final ArrayList<String> validTables = new ArrayList<>();
      final Assembly[] assemblies = ws.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(joinTable == assembly || !(assembly instanceof TableAssembly)) {
            continue;
         }

         if(joinTable.getTableAssembly(assembly.getAbsoluteName()) != null) {
            continue;
         }

         if(!WorksheetEventUtil.checkCyclicalDependency(
            ws, joinTable, (TableAssembly) assembly))
         {
            validTables.add(assembly.getAbsoluteName());
         }
      }

      return validTables;
   }
}
