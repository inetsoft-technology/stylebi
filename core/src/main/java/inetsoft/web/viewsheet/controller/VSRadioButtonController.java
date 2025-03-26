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
package inetsoft.web.viewsheet.controller;

import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.event.VSListInputSelectionEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
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
   public VSRadioButtonController(VSInputServiceProxy inputServiceProxy,
                                  RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.inputServiceProxy = inputServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
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
      inputServiceProxy.singleApplySelection(runtimeViewsheetRef.getRuntimeId(), event.assemblyName(), event.value(),
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
      return inputServiceProxy.setDetailHeight(runtimeId, objectId, height, principal);
   }

   private VSInputServiceProxy inputServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
