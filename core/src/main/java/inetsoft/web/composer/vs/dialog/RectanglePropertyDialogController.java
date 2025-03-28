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
 * Controller that provides the REST endpoints for the rect property dialog.
 *
 * @since 12.3
 */
@Controller
public class RectanglePropertyDialogController {
   /**
    * Creates a new instance of <tt>LinePropertyDialogController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public RectanglePropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            RectanglePropertyDialogServiceProxy rectanglePropertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.rectanglePropertyDialogServiceProxy = rectanglePropertyDialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the rectangle.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the rectangle object.
    *
    * @return the rectangle descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/rectangle-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public RectanglePropertyDialogModel getRectanglePropertyDialogModel(@PathVariable("objectId") String objectId,
                                                                       @RemainingPath String runtimeId,
                                                                       Principal principal)
      throws Exception
   {
      return rectanglePropertyDialogServiceProxy.getRectanglePropertyDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the top-level descriptor of the specified rectangle.
    *
    * @param objectId the runtime identifier of the rectangle object.
    * @param value the rectangle descriptor.
    *
    */
   @Undoable
   @LoadingMask
   @MessageMapping(value = "/composer/vs/rectangle-property-dialog-model/{objectId}")
   public void setRectanglePropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                               @Payload RectanglePropertyDialogModel value,
                                               @LinkUri String linkUri,
                                               Principal principal,
                                               CommandDispatcher commandDispatcher)
      throws Exception
   {
      rectanglePropertyDialogServiceProxy.setRectanglePropertyDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                                     objectId, value, linkUri,
                                                                     principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RectanglePropertyDialogServiceProxy rectanglePropertyDialogServiceProxy;

}
