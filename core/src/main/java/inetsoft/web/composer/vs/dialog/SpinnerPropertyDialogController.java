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
 * Controller that provides the REST endpoints for the spinner property dialog.
 *
 * @since 12.3
 */
@Controller
public class SpinnerPropertyDialogController {

   @Autowired
   public SpinnerPropertyDialogController(VSInputServiceProxy vsInputServiceProxy,
                                          RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsInputServiceProxy = vsInputServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    * Gets the top-level descriptor of the spinner.
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId   the spinner id
    * @return the spinner descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/spinner-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public SpinnerPropertyDialogModel getSpinnerPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                   @RemainingPath String runtimeId,
                                                                   Principal principal)
      throws Exception
   {
      return vsInputServiceProxy.getSpinnerPropertyDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the top-level descriptor of the specified spinner.
    *
    * @param objectId   the spinner id
    * @param value the spinner descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/spinner-property-dialog-model/{objectId}")
   public void setSpinnerPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                             @Payload SpinnerPropertyDialogModel value,
                                             @LinkUri String linkUri,
                                             Principal principal,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
     vsInputServiceProxy.setSpinnerPropertyDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                       value, linkUri, principal, commandDispatcher);
   }

   private final VSInputServiceProxy vsInputServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
