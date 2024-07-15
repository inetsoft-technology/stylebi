/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import org.springframework.stereotype.Service;

import java.awt.*;

@Service
public class MaxModeAssemblyService {
   public MaxModeAssemblyService(PlaceholderService placeholderService) {
      this.placeholderService = placeholderService;
   }

   public void toggleMaxMode(RuntimeViewsheet rvs, String assemblyName, Dimension maxSize,
                             CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly ass = vs.getAssembly(assemblyName);
      int oldShowTypeValue = -1;

      if(!(ass instanceof MaxModeSupportAssembly)) {
         return;
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

      while(vs.getViewsheet() != null) {
         // make sure embedded vs is on top when maximizing chart inside
         vs.setZIndex(vs.getZIndex() + zAdjust);
         vs = vs.getViewsheet();
         vs.setMaxMode(maxSize != null);
      }

      MaxModeSupportAssemblyInfo assemblyInfo = assembly.getMaxModeInfo();
      assemblyInfo.setMaxSize(maxSize);

      if(maxSize != null) {
         final Assembly[] assemblies = vs.getAssemblies(true, true);

         if(assemblies != null) {
            final VSAssembly topAssembly = (VSAssembly) assemblies[assemblies.length - 1];
            final int zIndex = topAssembly.getVSAssemblyInfo().getZIndex() + 1;
            assemblyInfo.setMaxModeZIndex(zIndex);
         }
      }

      final ChangedAssemblyList clist = placeholderService.createList(
         true, dispatcher, rvs, linkUri);

      placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, 0, 0,
         false, null, false, dispatcher, false,
         false, true, clist, null, null, false,
         false, false, true);

      if(ass.getVSAssemblyInfo() instanceof SelectionBaseVSAssemblyInfo &&
         ((SelectionBaseVSAssemblyInfo) ass.getVSAssemblyInfo()).isAdhocFilter())
      {
         SelectionBaseVSAssemblyInfo selectionBaseInfo =
            (SelectionBaseVSAssemblyInfo)ass.getVSAssemblyInfo();
         selectionBaseInfo.setShowType(oldShowTypeValue);
         placeholderService.refreshVSAssembly(rvs, assemblyName, dispatcher);
      }

   }

   private final PlaceholderService placeholderService;
}
