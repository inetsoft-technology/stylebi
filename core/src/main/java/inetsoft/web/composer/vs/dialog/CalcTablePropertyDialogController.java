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
 * Controller that provides the REST endpoints for the Calc Table
 * dialog
 *
 * @since 12.3
 */
@Controller
public class CalcTablePropertyDialogController {
   /**
    * Creates a new instance of <tt>CalcTablePropertyController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public CalcTablePropertyDialogController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      CalcTablePropertyDialogServiceProxy calcTablePropertyDialogService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.calcTablePropertyDialogService = calcTablePropertyDialogService;
   }

   /**
    * Gets the top-level descriptor of the calc table
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the calc table.
    *
    * @return the rectangle descriptor.
    */

   @RequestMapping(
      value = "/api/composer/vs/calc-table-property-dialog-model/{objectId}/{scrollX}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public CalcTablePropertyDialogModel getCalcTablePropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @PathVariable(value = "scrollX", required = false) double scrollX,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      return calcTablePropertyDialogService
         .getCalcTablePropertyDialogModel(runtimeId, objectId, scrollX, principal);
   }

   /**
    * Sets the specified table assembly info.
    *
    * @param objectId   the table id
    * @param value the table property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/calc-table-property-dialog-model/{objectId}")
   public void setCalcTablePropertyModel(@DestinationVariable("objectId") String objectId,
                                         @Payload CalcTablePropertyDialogModel value,
                                         @LinkUri String linkUri,
                                         Principal principal,
                                         CommandDispatcher commandDispatcher)
      throws Exception
   {
      String runtimeId = this.runtimeViewsheetRef.getRuntimeId();

      calcTablePropertyDialogService
         .setCalcTablePropertyModel(runtimeId, objectId, value, linkUri, principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CalcTablePropertyDialogServiceProxy calcTablePropertyDialogService;
}
