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
 * Controller that provides the endpoints for the highlight dialog.
 *
 * @since 12.3
 */
@Controller
public class HighlightDialogController {
   /**
    * Creates a new instance of <tt>HighlightDialogController</tt>.
    *
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    */
   @Autowired
   public HighlightDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    HighlightDialogServiceProxy highlightDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.highlightDialogServiceProxy = highlightDialogServiceProxy;
   }

   /**
    * Gets the model for the hyperlink dialog.
    *
    * @param objectId  the object identifier.
    * @param row       the row of the selected cell.
    * @param col       the column of the selected cell.
    * @param colName   the measure name of the selected region.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/highlight-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public HighlightDialogModel getHighlightDialogModel(
      @RequestParam("objectId") String objectId,
      @RequestParam(value = "row", required = false) Integer row,
      @RequestParam(value = "col", required = false) Integer col,
      @RequestParam(value = "colName", required = false) String colName,
      @RequestParam(value = "isAxis", required = false) boolean isAxis,
      @RequestParam(value = "isText", required = false) boolean isText,
      @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      return highlightDialogServiceProxy.getHighlightDialogModel(runtimeId, objectId, row, col, colName,
                                                          isAxis, isText, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/highlight-dialog-model/{objectId}")
   public void setHighlightDialogModel(@DestinationVariable("objectId") String objectId,
      @Payload HighlightDialogModel model,
      @LinkUri String linkUri,
      Principal principal,
      CommandDispatcher dispatcher)
      throws Exception
   {
      highlightDialogServiceProxy.setHighlightDialogModel(runtimeViewsheetRef.getRuntimeId(), objectId,
                                                          model, linkUri, principal, dispatcher);
   }

   /**
    * Check whether the conditions set for highlight cause a trap.
    *
    * @param model     the model containing the hyperlink model
    * @param objectId  the object id
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @PostMapping("/api/composer/viewsheet/check-highlight-dialog-trap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkVSTableTrap(
      @RequestBody() HighlightDialogModel model,
      @PathVariable("objectId") String objectId,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      return highlightDialogServiceProxy.checkVSTableTrap(runtimeId, model, objectId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final HighlightDialogServiceProxy highlightDialogServiceProxy;
}
