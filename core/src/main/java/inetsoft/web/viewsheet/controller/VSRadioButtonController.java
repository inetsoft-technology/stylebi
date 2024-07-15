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
package inetsoft.web.viewsheet.controller;


import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ListInputVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.VSListInputSelectionEvent;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides REST endpoints and message handling for Radio Button.
 *
 * @since 12.3
 */
@Controller
public class VSRadioButtonController {
   /**
    * Creates a new instance of <tt>VSRadioButtonController</tt>.
    */
   @Autowired
   public VSRadioButtonController(VSInputService inputService,
                                  ViewsheetService viewsheetService)
   {
      this.inputService = inputService;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Apply selection.
    *
    * @param principal    a principal identifying the current user.
    * @param event        the apply event
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/radioButton/applySelection")
   public void applySelection(@Payload VSListInputSelectionEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri)
      throws Exception
   {
      inputService.singleApplySelection(event.assemblyName(), event.value(),
                                  principal, dispatcher, linkUri);
   }

   @RequestMapping(value="/api/composer/vs/setDetailHeight/{aid}/{height}/**",
                   method = RequestMethod.POST)
   @ResponseBody
   public String setDetailHeight(
      @PathVariable("aid") String objectId,
      @PathVariable("height") double height,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeViewsheet rvs;
      Viewsheet vs;
      VSAssembly assembly;
      ListInputVSAssemblyInfo assemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         assembly = vs.getAssembly(objectId);
         assemblyInfo = (ListInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         assemblyInfo.setCellHeight((int) height);
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      return null;
   }

   private final VSInputService inputService;
   private final ViewsheetService viewsheetService;
}
