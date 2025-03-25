/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class MaxModeAssemblyService {
   public MaxModeAssemblyService(ViewsheetService viewsheetService,
                                 CoreLifecycleService coreLifecycleService)
   {
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService =   viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void toggleMaxMode(@ClusterProxyKey String vsId, String assemblyName, Dimension maxSize,
                             CommandDispatcher dispatcher, String linkUri, Principal principal)
      throws Exception
   {
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly ass = vs.getAssembly(assemblyName);
      int oldShowTypeValue = -1;

      if(!(ass instanceof MaxModeSupportAssembly)) {
         return null;
      }

      if(ass.getVSAssemblyInfo() instanceof SelectionBaseVSAssemblyInfo) {
         SelectionBaseVSAssemblyInfo selectionBaseInfo = (SelectionBaseVSAssemblyInfo)ass.getVSAssemblyInfo();
         oldShowTypeValue = selectionBaseInfo.getShowType();
      }

      MaxModeSupportAssembly assembly = (MaxModeSupportAssembly) ass;

      int index = assemblyName.lastIndexOf(".");

      if(index >= 0) {
         vs = (Viewsheet) vs.getAssembly(assemblyName.substring(0, index));
      }

      int zAdjust = maxSize == null ? -100000 : 100000;
      vs.setMaxMode(maxSize != null);
      boolean embeddedViewsheet = false;

      while(vs.getViewsheet() != null) {
         // make sure embedded vs is on top when maximizing chart inside
         vs.setZIndex(vs.getZIndex() + zAdjust);
         vs = vs.getViewsheet();
         vs.setMaxMode(maxSize != null);
         embeddedViewsheet = true;
      }

      MaxModeSupportAssemblyInfo assemblyInfo = assembly.getMaxModeInfo();
      assemblyInfo.setMaxSize(maxSize);

      if(maxSize != null) {
         final Assembly[] assemblies = vs.getAssemblies(true, true);
         int parentZAdjust = embeddedViewsheet ? zAdjust : 0;

         if(assemblies != null) {
            final VSAssembly topAssembly = (VSAssembly) assemblies[assemblies.length - 1];
            final int zIndex = topAssembly.getVSAssemblyInfo().getZIndex() + 1 + parentZAdjust;
            assemblyInfo.setMaxModeZIndex(zIndex);
         }
      }

      final ChangedAssemblyList clist = coreLifecycleService.createList(
         true, dispatcher, rvs, linkUri);

      coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, 0, 0,
                                            false, null, false, dispatcher, false,
                                            false, true, clist, null, null, false,
                                            false, false, true);

      if(ass.getVSAssemblyInfo() instanceof SelectionBaseVSAssemblyInfo &&
         ((SelectionBaseVSAssemblyInfo) ass.getVSAssemblyInfo()).isAdhocFilter())
      {
         SelectionBaseVSAssemblyInfo selectionBaseInfo =
            (SelectionBaseVSAssemblyInfo)ass.getVSAssemblyInfo();
         selectionBaseInfo.setShowType(oldShowTypeValue);
         coreLifecycleService.refreshVSAssembly(rvs, assemblyName, dispatcher);
      }

      return null;
   }

   private final CoreLifecycleService coreLifecycleService;
   private ViewsheetService viewsheetService;
}
