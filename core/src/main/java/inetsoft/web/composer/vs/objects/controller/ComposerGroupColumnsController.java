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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.web.composer.vs.objects.event.GroupFieldsEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/**
 * Controller that processes adhoc filter events for tables and charts.
 */
@Controller
public class ComposerGroupColumnsController {
   /**
    * Creates a new instance of <tt>ComposerAdhocFilterController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerGroupColumnsController(ComposerGroupColumnsServiceProxy composerGroupColumnsServiceProxy,
                                         RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.composerGroupColumnsServiceProxy = composerGroupColumnsServiceProxy;
   }

   /**
    * Group rows/columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/groupFields")
   public void group(@Payload GroupFieldsEvent event, @LinkUri String linkUri,
                     Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      composerGroupColumnsServiceProxy.group(runtimeViewsheetRef.getRuntimeId(), event, linkUri,
                                             principal, dispatcher);
   }



   /**
    * Check whether the name input for the group is a duplicate.
    *
    * @param event     the model containing group info
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return whether the name is a duplicate
    */
   @PostMapping("/api/composer/viewsheet/groupFields/checkDuplicates/**")
   @ResponseBody
   public boolean checkVSTableTrap(
      @RequestBody() GroupFieldsEvent event,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      return composerGroupColumnsServiceProxy.checkVSTableTrap(runtimeId, event, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ComposerGroupColumnsServiceProxy composerGroupColumnsServiceProxy;
}
