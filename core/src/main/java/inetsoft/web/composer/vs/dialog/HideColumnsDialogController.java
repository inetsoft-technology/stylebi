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

import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
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
 * Controller that provides the endpoints for the hide columns dialog.
 *
 * @since 12.3
 */
@Controller
public class HideColumnsDialogController {
   /**
    * Creates a new instance of <tt>HideColumnsDialogController</tt>.
    *
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    */
   @Autowired
   public HideColumnsDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      HideColumnsDialogServiceProxy hideColumnsDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.hideColumnsDialogServiceProxy = hideColumnsDialogServiceProxy;
   }

   /**
    * Gets the model for the hide column dialog.
    *
    * @param objectId  the object identifier.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/hide-columns-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public HideColumnsDialogModel getColumnOptionDialogModel(@RequestParam("objectId") String objectId,
                                                             @RequestParam("runtimeId") String runtimeId,
                                                             Principal principal)
      throws Exception
   {
      return hideColumnsDialogServiceProxy.getColumnOptionDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets information gathered from the hide column dialog.
    *
    * @param objectId   the object id.
    * @param model      the hyperlink dialog model.
    * @param principal  the user information.
    * @param dispatcher the command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/hide-columns-dialog-model/{objectId}")
   public void setColumnOptionDialogModel(@DestinationVariable("objectId") String objectId,
                                          @Payload HideColumnsDialogModel model,
                                          Principal principal,
                                          CommandDispatcher dispatcher,
                                          @LinkUri String linkUri)
      throws Exception
   {
      hideColumnsDialogServiceProxy.setColumnOptionDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                               objectId, model, principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final HideColumnsDialogServiceProxy hideColumnsDialogServiceProxy;
}
