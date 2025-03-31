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

package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.command.AddLayoutObjectCommand;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectTextEvent;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class VSTextService {
   public VSTextService(
      VSObjectPropertyService vsObjectPropertyService,
      ViewsheetService viewsheetService,
      VSObjectModelFactoryService objectModelService,
      VSLayoutService vsLayoutService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
      this.objectModelService = objectModelService;
      this.vsLayoutService = vsLayoutService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeText(@ClusterProxyKey String runtimeId, ChangeVSObjectTextEvent event,
                          String linkUri, Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TextVSAssembly assembly = (TextVSAssembly) viewsheet.getAssembly(event.getName());

      if(assembly == null) {
         return null;
      }

      TextVSAssemblyInfo assemblyInfo = (TextVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
      assemblyInfo.setTextValue(event.getText());
      // should change to static text and clear binding
      assemblyInfo.setScalarBindingInfo(null);
      this.vsObjectPropertyService.editObjectProperty(
         rvs, assemblyInfo, event.getName(), event.getName(), linkUri, principal, dispatcher);
      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean changeText(@ClusterProxyKey String runtimeId, String focusedLayoutName,
                             int region, ChangeVSObjectTextEvent event, String linkUri,
                          Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeId, principal);
      RuntimeViewsheet parentRvs =
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet viewsheet = parentRvs.getViewsheet();
      PrintLayout layout = viewsheet.getLayoutInfo().getPrintLayout();

      Optional<VSAssemblyLayout> layoutOptional = vsLayoutService
         .findAssemblyLayout(layout, event.getName(), region);
      layoutOptional.ifPresent(l -> {
            TextVSAssemblyInfo textAssemblyInfo =
               (TextVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo();

            String text = event.getText();
            textAssemblyInfo.setTextValue(text);

            AddLayoutObjectCommand command = new AddLayoutObjectCommand();
            command.setObject(vsLayoutService.createObjectModel(parentRvs, l,
                                                                objectModelService));
            command.setRegion(region);
            dispatcher.sendCommand(command);
            vsLayoutService.makeUndoable(parentRvs, dispatcher, focusedLayoutName);
         });
      return layoutOptional.isPresent();
   }


   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
   private final VSLayoutService vsLayoutService;
   private final VSObjectModelFactoryService objectModelService;
}
