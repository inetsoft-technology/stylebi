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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.table.MaxTableEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

/**
 * Controller that processes vs form events.
 */
@Controller
public class VSTableMaxModeController {
   @Autowired
   public VSTableMaxModeController(ViewsheetService viewsheetService,
                                RuntimeViewsheetRef runtimeViewsheetRef,
                                CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/vstable/toggle-max-mode")
   public void toggleMaxMode(MaxTableEvent event, Principal principal, @LinkUri String linkUri,
                             CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      String name = event.tableName();
      TableDataVSAssembly tableAssembly = (TableDataVSAssembly) vs.getAssembly(name);
      Dimension maxSize = event.maxSize();

      if(tableAssembly == null) {
         return;
      }

      int index = name.lastIndexOf(".");

      if(index >= 0) {
         vs = (Viewsheet) vs.getAssembly(name.substring(0, index));
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

      TableDataVSAssemblyInfo tableAssemblyInfo = tableAssembly.getTableDataVSAssemblyInfo();
      tableAssemblyInfo.setMaxSize(event.maxSize());

      if(maxSize != null) {
         final Assembly[] assemblies = vs.getAssemblies(true, true);
         int parentZAdjust = embeddedViewsheet ? zAdjust : 0;

         if(assemblies != null) {
            final VSAssembly topAssembly = (VSAssembly) assemblies[assemblies.length - 1];
            final int zIndex = topAssembly.getVSAssemblyInfo().getZIndex() + 1 + parentZAdjust;
            tableAssemblyInfo.setMaxModeZIndex(zIndex);
         }
      }

      final ChangedAssemblyList clist = coreLifecycleService.createList(true, dispatcher, rvs,
                                                                        linkUri);

      tableAssembly.setLastStartRow(0);
      coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, event.width(), event.height(), false,
                                            null, false, dispatcher, false, false, true, clist,
                                            null, null, false, false, false, true);
   }

   /**
    * @return the runtime ID from the injected RuntimeViewsheetRef
    */
   protected String getRuntimeId() {
      return runtimeViewsheetRef.getRuntimeId();
   }

   private ViewsheetService viewsheetService;
   private RuntimeViewsheetRef runtimeViewsheetRef;
   private CoreLifecycleService coreLifecycleService;

}
