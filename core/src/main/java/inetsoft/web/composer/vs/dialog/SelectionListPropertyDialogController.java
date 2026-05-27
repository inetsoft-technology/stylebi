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

import inetsoft.web.binding.drm.DataRefModel;
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
import java.util.List;

/**
 * Controller that provides the REST endpoints for the Selection List
 * dialog
 *
 * @since 12.3
 */
@Controller
public class SelectionListPropertyDialogController {
   /**
    * Creates a new instance of <tt>SelectionListPropertyController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public SelectionListPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                                SelectionListPropertyDialogServiceProxy selectionDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.selectionDialogServiceProxy = selectionDialogServiceProxy;
   }

   /**
    * Gets the range slider property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the range slider id
    * @return the range slider property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/selection-list-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SelectionListPropertyDialogModel getSelectionListPropertyModel(@PathVariable("objectId") String objectId,
                                                                         @RemainingPath String runtimeId,
                                                                         Principal principal)
      throws Exception
   {
      return selectionDialogServiceProxy.getSelectionListPropertyModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the specified selection list assembly info.
    *
    * @param objectId   the selection list id
    * @param value the selection list property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/selection-list-property-dialog-model/{objectId}")
   public void setSelectionListPropertyModel(@DestinationVariable("objectId") String objectId,
                                             @Payload SelectionListPropertyDialogModel value,
                                             @LinkUri String linkUri,
                                             Principal principal,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      selectionDialogServiceProxy.setSelectionListPropertyModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                                value, linkUri, principal, commandDispatcher);
   }

   /**
    * Check whether the parameters set for the selection list will cause a trap.
    *
    * @param value     the selection list property dialog model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("api/composer/vs/selection-list-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkVSTrap(@RequestBody SelectionListPropertyDialogModel value,
                                       @PathVariable("objectId") String objectId,
                                       @RemainingPath String runtimeId,
                                       Principal principal)
      throws Exception
   {
      return selectionDialogServiceProxy.checkVSTrap(runtimeId, value, objectId, principal);
   }

   @PostMapping("api/composer/vs/selection-list-property-dialog-model/getGrayedOutFields/{objectId}/**")
   @ResponseBody
   public List<DataRefModel> getGrayedOutFields(@RequestBody SelectionListPropertyDialogModel value,
                                                @PathVariable("objectId") String objectId,
                                                @RemainingPath String runtimeId,
                                                Principal principal)
                                              throws Exception
   {
      return selectionDialogServiceProxy.getGrayedOutFields(runtimeId, value, objectId, principal);
   }


   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final SelectionListPropertyDialogServiceProxy selectionDialogServiceProxy;
}
