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
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides the endpoints for the vs sorting dialog.
 *
 * @since 12.3
 */
@Controller
public class VSSortingDialogController {
   /**
    * Creates a new instance of <tt>VSSortingDialogController</tt>.
    */
   @Autowired
   public VSSortingDialogController(VSInputServiceProxy vsInputServiceProxy,
                                    RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsInputServiceProxy = vsInputServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    * Gets the model for the vs sorting dialog.
    *
    * @param objectId  the object identifier.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/vs-sorting-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public VSSortingDialogModel getVSSortingDialogModel(@RequestParam("objectId") String objectId,
                                                       @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      return vsInputServiceProxy.getVSSortingDialogModel(runtimeId, objectId, principal);
   }

   @Undoable
   @MessageMapping("/composer/vs/vs-sorting-dialog-model/{objectId}")
   public void setVSSortingDialogModel(@DestinationVariable("objectId") String objectId,
                                       @Payload VSSortingDialogModel model,
                                       Principal principal,
                                       CommandDispatcher dispatcher) throws Exception
   {
      vsInputServiceProxy.setVSSortingDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                  model, principal, dispatcher);
   }

   private final VSInputServiceProxy vsInputServiceProxy;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
