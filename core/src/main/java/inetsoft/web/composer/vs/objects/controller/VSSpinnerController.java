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

import inetsoft.web.composer.vs.objects.event.ChangeVSObjectValueEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that processes vs spinner events.
 */
@Controller
public class VSSpinnerController {
   /**
    * Creates a new instance of <tt>VSSpinnerController</tt>.
    *
    * @param vsInputService   reference to the VSInputService
    */
   @Autowired
   public VSSpinnerController(VSInputService vsInputService) {
      this.vsInputService = vsInputService;
   }

   /**
    * Change value of the object.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/vsSpinner/changeValue")
   public void changeValue(@Payload ChangeVSObjectValueEvent event, Principal principal,
                           CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      this.vsInputService.singleApplySelection(event.getName(), event.getValue(),
                                         principal, dispatcher, linkUri);
   }

   private final VSInputService vsInputService;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSSpinnerController.class);
}
