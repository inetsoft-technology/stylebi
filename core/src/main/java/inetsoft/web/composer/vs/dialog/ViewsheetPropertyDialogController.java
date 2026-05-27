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

import inetsoft.util.*;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.database.StringWrapper;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

@RestController
public class ViewsheetPropertyDialogController {

   /**
    * Creates a new instance of <tt>ViewsheetPropertyDialogController</tt>.
    *
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public ViewsheetPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                            ViewsheetPropertyDialogServiceProxy dialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogServiceProxy = dialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the viewsheet.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the principal.
    *
    * @return the viewsheet descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/viewsheet-property-dialog-model/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ViewsheetPropertyDialogModel getViewsheetInfo(@RemainingPath String runtimeId,
                                                        Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);

      return dialogServiceProxy.getViewsheetInfo(runtimeId, principal);
   }

   /**
    * Sets the top-level descriptor of the specified viewsheet.
    *
    * @param value the viewsheet descriptor.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/viewsheet-property-dialog-model")
   public void setViewsheetInfo(@Payload ViewsheetPropertyDialogModel value,
                                Principal principal, CommandDispatcher commandDispatcher,
                                @LinkUri String linkUri)
      throws Exception
   {
      String updatedName = dialogServiceProxy.setViewsheetInfo(runtimeViewsheetRef.getRuntimeId(),
                                                               value, principal, commandDispatcher,
                                                               linkUri, runtimeViewsheetRef.getFocusedLayoutName());

      if(updatedName != null) {
         runtimeViewsheetRef.setFocusedLayoutName(updatedName);
      }
   }

   @PostMapping(
      value = "/api/composer/vs/viewsheet-property-dialog-model/test-script")
   public StringWrapper testVsScript(@RequestParam("runtimeId") String runtimeId,
                                     @RequestBody ViewsheetPropertyDialogModel model,
                                     Principal principal)
      throws Exception
   {
      return dialogServiceProxy.testVsScript(runtimeId, model, principal);
   }

   @GetMapping("/api/composer/vs/viewsheet-property-dialog-model/convert-to-worksheet/**")
   @ResponseBody
   public ConvertToWorksheetResponseModel convertLogicModelToWorksheet(@RemainingPath String runtimeId,
                                                                       Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.convertLogicModelToWorksheet(runtimeId, principal) ;
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetPropertyDialogServiceProxy dialogServiceProxy;
}
