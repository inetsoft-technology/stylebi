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
 * Controller that provides the REST endpoints for the line property dialog.
 *
 * @since 12.3
 */
@Controller
public class LinePropertyDialogController {

   /**
    * Creates a new instance of <tt>LinePropertyDialogController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public LinePropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       LinePropertyDialogServiceProxy propertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.propertyDialogServiceProxy = propertyDialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the line.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the line object.
    *
    * @return the line descriptor.
    */
   @GetMapping(value = "/api/composer/vs/line-property-dialog-model/{objectId}/**")
   @ResponseBody
   public LinePropertyDialogModel getLinePropertyDialogModel(@PathVariable("objectId") String objectId,
                                                             @RemainingPath String runtimeId,
                                                             Principal principal)
      throws Exception
   {
      return propertyDialogServiceProxy.getLinePropertyDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the top-level descriptor of the specified line.
    *
    * @param objectId the runtime identifier of the line object.
    * @param value the line descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/line-property-dialog-model/{objectId}")
   public void setLinePropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                          @Payload LinePropertyDialogModel value,
                                          @LinkUri String linkUri,
                                          Principal principal,
                                          CommandDispatcher commandDispatcher)
      throws Exception
   {
      propertyDialogServiceProxy.setLinePropertyDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                       value, linkUri, principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final LinePropertyDialogServiceProxy propertyDialogServiceProxy;
}
