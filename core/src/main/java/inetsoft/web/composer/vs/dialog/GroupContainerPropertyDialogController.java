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
 * Controller that provides the REST endpoints for the group property dialog.
 *
 * @since 12.3
 */
@Controller
public class GroupContainerPropertyDialogController {
   /**
    * Creates a new instance of <tt>GroupContainerPropertyDialogController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public GroupContainerPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                                 GroupContainerPropertyDialogServiceProxy dialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogServiceProxy = dialogServiceProxy;
   }

   @RequestMapping(
      value = "/api/composer/vs/group-container-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public GroupContainerPropertyDialogModel getGroupContainerPropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @RemainingPath String runtimeId,
      Principal principal)
      throws Exception
   {
      return dialogServiceProxy.getGroupContainerPropertyDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the specified group assembly info.
    *
    * @param objectId   the image id
    * @param value the image dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/group-container-property-dialog-model/{objectId}")
   public void setGroupContainerPropertyDialogModel(
      @DestinationVariable("objectId") String objectId,
      @Payload GroupContainerPropertyDialogModel value,
      @LinkUri String linkUri, Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      dialogServiceProxy.setGroupContainerPropertyDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                               value, linkUri, principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final GroupContainerPropertyDialogServiceProxy dialogServiceProxy;
}
