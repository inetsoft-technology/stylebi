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

package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.BindingPaneData;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class VSBindingControllerService {
   @Autowired
   public VSBindingControllerService(ViewsheetService viewsheetService,
                                     inetsoft.web.binding.service.VSBindingService vsBindingService)
   {
      this.viewsheetService = viewsheetService;
      this.vsBindingService = vsBindingService;
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BindingPaneData open(@ClusterProxyKey String vsId, String assemblyName, boolean viewer,
                               boolean temporarySheet, Principal principal)
      throws Exception
   {
      String id = this.vsBindingService.createRuntimeSheet(vsId, viewer, temporarySheet,
                                                           principal, assemblyName);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      return new BindingPaneData(id, vs.getViewsheetInfo().isMetadata());
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String commit(@ClusterProxyKey String vsId, String assemblyName,
                        String editMode, String originalMode,
                        Principal principal)
      throws Exception
   {
      return vsBindingService.finishEdit(viewsheetService, Tool.byteDecode(vsId),
                                         assemblyName, editMode, originalMode, principal);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSSelectionTrap(@ClusterProxyKey String runtimeId,
                                                InsertSelectionChildEvent event,
                                                String linkUri,
                                                Principal principal)
      throws Exception
   {
      return this.vsBindingService.checkVSSelectionTrap(event, runtimeId, principal);
   }

   private final ViewsheetService viewsheetService;
   private final inetsoft.web.binding.service.VSBindingService vsBindingService;
}
