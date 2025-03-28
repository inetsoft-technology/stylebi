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
package inetsoft.web.composer.vs.dialog;


import inetsoft.web.composer.model.vs.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the Selection Container
 * dialog
 *
 * @since 12.3
 */
@Controller
public class SelectionContainerPropertyDialogController {
   /**
    * Creates a new instance of <tt>SelectionContainerPropertyController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public SelectionContainerPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                                     SelectionContainerPropertyDialogServiceProxy propertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.propertyDialogServiceProxy = propertyDialogServiceProxy;
   }

   /**
    * Gets the selection container property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the selection container id
    * @return the selection container property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/selection-container-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SelectionContainerPropertyDialogModel getSelectionContainerPropertyModel(@PathVariable("objectId") String objectId,
                                                                                   @RemainingPath String runtimeId,
                                                                                   Principal principal)
      throws Exception
   {
      return propertyDialogServiceProxy.getSelectionContainerPropertyModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the specified selection container assembly info.
    *
    * @param objectId   the selection container id
    * @param value the selection container property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/selection-container-property-dialog-model/{objectId}")
   public void setSelectionContainerPropertyModel(@DestinationVariable("objectId") String objectId,
                                                  @Payload SelectionContainerPropertyDialogModel value,
                                                  @LinkUri String linkUri,
                                                  Principal principal,
                                                  CommandDispatcher commandDispatcher)
      throws Exception
   {
      propertyDialogServiceProxy.setSelectionContainerPropertyModel(runtimeViewsheetRef.getRuntimeId(),
                                                                    objectId, value, linkUri, principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final SelectionContainerPropertyDialogServiceProxy propertyDialogServiceProxy;
}
