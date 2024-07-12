/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.VSEditableAssemblyLayout;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.command.AddLayoutObjectCommand;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectTextEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that processes vs text events.
 */
@Controller
public class VSTextController {
   /**
    * Creates a new instance of <tt>VSTextController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public VSTextController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSObjectPropertyService vsObjectPropertyService,
      ViewsheetService viewsheetService,
      VSObjectModelFactoryService objectModelService,
      PlaceholderService placeholderService,
      VSLayoutService vsLayoutService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
      this.objectModelService = objectModelService;
      this.vsLayoutService = vsLayoutService;
      this.placeholderService = placeholderService;
   }

   /**
    * Change inner text of the object.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/vsText/changeText")
   public void changeText(@Payload ChangeVSObjectTextEvent event, @LinkUri String linkUri,
                          Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TextVSAssembly assembly = (TextVSAssembly) viewsheet.getAssembly(event.getName());

      if(assembly == null) {
         return;
      }

      TextVSAssemblyInfo assemblyInfo = (TextVSAssemblyInfo) Tool.clone(assembly.getVSAssemblyInfo());
      assemblyInfo.setTextValue(event.getText());
      // should change to static text and clear binding
      assemblyInfo.setScalarBindingInfo(null);
      this.vsObjectPropertyService.editObjectProperty(
         rvs, assemblyInfo, event.getName(), event.getName(), linkUri, principal, dispatcher);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/printLayout/vsText/changeText/{region}")
   public void changeText(@DestinationVariable("region") int region,
                           @Payload ChangeVSObjectTextEvent event, @LinkUri String linkUri,
                          Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      RuntimeViewsheet parentRvs =
         viewsheetService.getViewsheet(rvs.getOriginalID(), principal);
      Viewsheet viewsheet = parentRvs.getViewsheet();
      PrintLayout layout = viewsheet.getLayoutInfo().getPrintLayout();

      vsLayoutService.findAssemblyLayout(layout, event.getName(), region)
            .ifPresent(l -> {
               TextVSAssemblyInfo textAssemblyInfo =
                  (TextVSAssemblyInfo) ((VSEditableAssemblyLayout) l).getInfo();

               String text = event.getText();
               textAssemblyInfo.setTextValue(text);

               AddLayoutObjectCommand command = new AddLayoutObjectCommand();
               command.setObject(vsLayoutService.createObjectModel(parentRvs, l,
                  objectModelService));
               command.setRegion(region);
               dispatcher.sendCommand(command);
               this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
               placeholderService.makeUndoable(parentRvs, dispatcher,
                  this.runtimeViewsheetRef.getFocusedLayoutName());
            });
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
   private final VSLayoutService vsLayoutService;
   private final VSObjectModelFactoryService objectModelService;
   private final PlaceholderService placeholderService;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSTextController.class);
}
