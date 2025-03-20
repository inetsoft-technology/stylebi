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

package inetsoft.web.binding.dnd.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.SourceChangeMessage;
import org.springframework.stereotype.Service;
import inetsoft.report.composition.WorksheetEngine;

import java.security.Principal;

@Service
@ClusterProxy
public class VSSourceChangeService {

   public VSSourceChangeService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public SourceChangeMessage checkSourceChanged(@ClusterProxyKey String vsId, String aname, String table,
                                                         VSAssemblyInfoHandler handler, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(vsId, principal);
      SourceChangeMessage sourceChangeMessage = new SourceChangeMessage();
      sourceChangeMessage.setChanged(false);

      if(rvs != null) {
         VSAssembly assembly = getVSAssembly(rvs, aname);
         sourceChangeMessage.setChanged(handler.sourceChanged(table, assembly));
      }

      return sourceChangeMessage;
   }

   protected VSAssembly getVSAssembly(RuntimeViewsheet rvs, String name) {
      Viewsheet viewsheet = rvs.getViewsheet();
      return (VSAssembly) viewsheet.getAssembly(name);
   }


   private ViewsheetService viewsheetService;
}
