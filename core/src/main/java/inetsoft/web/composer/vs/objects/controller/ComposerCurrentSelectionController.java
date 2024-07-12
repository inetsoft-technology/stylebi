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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.event.VSObjectEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.awt.*;
import java.security.Principal;

/**
 * Controller that processes VS Selection List events.
 */
@Controller
public class ComposerCurrentSelectionController {
   /**
    * Creates a new instance of <tt>ComposerCurrentSelectionController</tt>.
    *  @param runtimeViewsheetRef the runtime viewsheet reference
    * @param placeholderService the placeholder service
    * @param viewsheetService
    */
   @Autowired
   public ComposerCurrentSelectionController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Change range slider to a selection list. Mimic of ConvertCSComponentEvent.java
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/currentSelection/titleRatio/{ratio}")
   public void setTitleRatio(@DestinationVariable("ratio") double ratio,
                             VSObjectEvent event, Principal principal,
                             CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      String name = event.getName();

      if(viewsheet == null) {
         return;
      }

      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(name);

      if(!(assembly instanceof CurrentSelectionVSAssembly)) {
         return;
      }

      // sanity check
      Dimension size = viewsheet.getPixelSize(assembly.getVSAssemblyInfo());
      // reserve 50px for the mini-toolbar, otherwise resizer is covered
      ratio = Math.max(0.05, Math.min((size.width - 50.0) / size.width, ratio));

      CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly) assembly;
      ((CurrentSelectionVSAssemblyInfo) containerAssembly.getInfo()).setTitleRatio(ratio);
      placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   private final PlaceholderService placeholderService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
}
