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

package inetsoft.web.admin.schedule;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.asset.AssetEntry;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;

@Service
@ClusterProxy
public class EMScheduleTaskActionService {
   public EMScheduleTaskActionService(ScheduleTaskActionService actionService,
                                      ScheduleTaskActionServiceProxy actionServiceProxy,
                                      ViewsheetService viewsheetService) {
      this.actionService = actionService;
      this.actionServiceProxy = actionServiceProxy;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<String> getViewsheetTableDataAssemblies(
      @ClusterProxyKey String identifier,
      Principal principal) throws Exception
   {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);
      String runtimeId = viewsheetService.openViewsheet(entry, null, false);
      List<String> assemblies = actionServiceProxy.getViewsheetTableDataAssemblies(runtimeId, principal);
      viewsheetService.closeViewsheet(identifier, null);

      return assemblies;
   }

   private final ScheduleTaskActionService actionService;
   private final ScheduleTaskActionServiceProxy actionServiceProxy;
   private final ViewsheetService viewsheetService;
}
