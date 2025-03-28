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

@Controller
public class RangeSliderPropertyDialogController {
   /**
    * Creates a new instance of <tt>RangeSliderPropertyController</tt>.
    *
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public RangeSliderPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                              RangeSliderPropertyDialogServiceProxy dialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogServiceProxy = dialogServiceProxy;
   }

   /**
    * Gets the range slider property dialog model.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the range slider id
    * @return the range slider property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/range-slider-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public RangeSliderPropertyDialogModel getRangeSliderPropertyModel(@PathVariable("objectId") String objectId,
                                                                     @RemainingPath String runtimeId,
                                                                     Principal principal)
      throws Exception
   {
      return dialogServiceProxy.getRangeSliderPropertyModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the specified rangeSlider assembly info.
    *
    * @param objectId   the rangeSlider id
    * @param value the rangeSlider property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/range-slider-property-dialog-model/{objectId}")
   public void setRangeSliderPropertyModel(@DestinationVariable("objectId") String objectId,
                                           @Payload RangeSliderPropertyDialogModel value,
                                           @LinkUri String linkUri,
                                           Principal principal,
                                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      dialogServiceProxy.setRangeSliderPropertyModel(runtimeViewsheetRef.getRuntimeId(), objectId, value, linkUri,
                                  principal, commandDispatcher);
   }

   /**
    * Check whether the bound table columns for the assembly will cause a trap.
    *
    * @param model     the model containing the hyperlink model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("/api/composer/vs/range-slider-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkTrap(@RequestBody() RangeSliderPropertyDialogModel model,
                                     @PathVariable("objectId") String objectId,
                                     @RemainingPath String runtimeId,
                                     Principal principal) throws Exception
   {
      return dialogServiceProxy.checkTrap(runtimeId, model, objectId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RangeSliderPropertyDialogServiceProxy dialogServiceProxy;

}
