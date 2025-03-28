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
 * Controller that provides the REST endpoints for the image property dialog.
 *
 * @since 12.3
 */
@Controller
public class ImagePropertyDialogController {
   /**
    * Creates a new instance of <tt>ImagePropertyDialogController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public ImagePropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        ImagePropertyDialogServiceProxy imagePropertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.imagePropertyDialogServiceProxy = imagePropertyDialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the image.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the image object.
    *
    * @return the image descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/image-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ImagePropertyDialogModel getImagePropertyDialogModel(@PathVariable("objectId") String objectId,
                                                               @RemainingPath String runtimeId,
                                                               Principal principal)
      throws Exception
   {
      return imagePropertyDialogServiceProxy.getImagePropertyDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the specified image assembly info.
    *
    * @param objectId   the image id
    * @param value the image dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/image-property-dialog-model/{objectId}")
   public void setImagePropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                           @Payload ImagePropertyDialogModel value,
                                           @LinkUri String linkUri,
                                           Principal principal,
                                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      imagePropertyDialogServiceProxy.setImagePropertyDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                             objectId, value, linkUri, principal,
                                                             commandDispatcher);
   }

   /**
    * Check whether the list values columns for the assembly will cause a trap.
    *
    * @param model     the model containing the hyperlink model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("/api/composer/vs/image-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkTrap(@RequestBody() ImagePropertyDialogModel model,
                                     @PathVariable("objectId") String objectId,
                                     @RemainingPath String runtimeId,
                                     Principal principal) throws Exception
   {
      return imagePropertyDialogServiceProxy.checkTrap(runtimeId, model, objectId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ImagePropertyDialogServiceProxy imagePropertyDialogServiceProxy;
}
