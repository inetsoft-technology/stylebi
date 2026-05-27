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
 * Controller that provides the endpoints for the column option dialog.
 *
 * @since 12.3
 */
@Controller
public class ColumnOptionDialogController {
   /**
    * Creates a new instance of <tt>ColumnOptionDialogController</tt>.
    *
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    */
   @Autowired
   public ColumnOptionDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       VSInputServiceProxy vsInputServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsInputServiceProxy = vsInputServiceProxy;
   }

   /**
    * Gets the model for the column option dialog.
    *
    * @param objectId  the object identifier.
    * @param col       the column of the selected cell.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/column-option-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ColumnOptionDialogModel getColumnOptionDialogModel(
      @RequestParam("objectId") String objectId,
      @RequestParam("col") Integer col,
      @RequestParam("runtimeId") String runtimeId,
      Principal principal)
         throws Exception
   {
      return vsInputServiceProxy.getColumnOptionDialogModel(runtimeId, objectId, col, principal);
   }

   /**
    * Sets information gathered from the column option.
    *
    * @param objectId   the object id.
    * @param col        the column index.
    * @param model      the hyperlink dialog model.
    * @param principal  the user information.
    * @param dispatcher the command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/column-option-dialog-model/{objectId}/{col}")
   public void setColumnOptionDialogModel(@DestinationVariable("objectId") String objectId,
                                          @DestinationVariable("col") int col,
                                          @Payload ColumnOptionDialogModel model,
                                          Principal principal,
                                          CommandDispatcher dispatcher,
                                          @LinkUri String linkUri)
      throws Exception
   {
      vsInputServiceProxy.setColumnOptionDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                     col, model, principal, dispatcher, linkUri);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSInputServiceProxy vsInputServiceProxy;
}
