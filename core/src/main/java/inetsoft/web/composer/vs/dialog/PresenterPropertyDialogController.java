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
 * Controller that provides the REST endpoints for the presenter property dialog.
 *
 * @since 12.3
 */
@Controller
public class PresenterPropertyDialogController {
   /**
    * Creates a new instance of <tt>PresenterPropertyDialogController</tt>.
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    */
   @Autowired
   public PresenterPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            PresenterPropertyDialogServiceProxy propertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.propertyDialogServiceProxy = propertyDialogServiceProxy;
   }

   /**
    * Gets the descriptor of the presenter ref.
    *
    * @param runtimeId the runtime identifier of the viewsheet
    * @param objectId  the runtime identifier of the object
    * @param row       the table row number (if applicable)
    * @param col       the table col number (if applicable)
    * @param presenter the presenter name
    * @param runtimeId the viewsheet runtime id
    * @param principal the current user
    *
    * @return the presenter descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/presenter-property-dialog-model/{objectId}/{row}/{col}/{presenter}/{layout}/{layoutRegion}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public PresenterPropertyDialogModel getPresenterPropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @PathVariable("row") int row,
      @PathVariable("col") int col,
      @PathVariable("presenter") String presenter,
      @PathVariable("layout") boolean layout,
      @PathVariable("layoutRegion") int layoutRegion,
      @RemainingPath String runtimeId,
      Principal principal)
      throws Exception
   {
      return propertyDialogServiceProxy.getPresenterPropertyDialogModel(runtimeId, objectId, row, col, presenter,
                                                            layout, layoutRegion, principal);
   }

   /**
    * Sets the specified presenter ref.
    *
    * @param objectId          the object id
    * @param model             the presenter dialog model.
    * @param row               the table row (if applicable)
    * @param col               the table col (if applicable)
    * @param principal         the current user
    * @param commandDispatcher the command dispatcher
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/presenter-property-dialog-model/{objectId}/{row}/{col}/{layout}/{layoutRegion}")
   public void setPresenterDialogModel(@DestinationVariable("objectId") String objectId,
                                       @DestinationVariable("row") int row,
                                       @DestinationVariable("col") int col,
                                       @DestinationVariable("layout") boolean layout,
                                       @DestinationVariable("layoutRegion") int layoutRegion,
                                       @Payload PresenterPropertyDialogModel model,
                                       @LinkUri String linkUri,
                                       Principal principal,
                                       CommandDispatcher commandDispatcher)
      throws Exception
   {
      propertyDialogServiceProxy.setPresenterDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                    row, col, layout, layoutRegion, model,
                                                    linkUri, principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PresenterPropertyDialogServiceProxy propertyDialogServiceProxy;
}
