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
 * Controller that provides the REST endpoints for the chart property dialog.
 *
 * @since 12.3
 */
@Controller
public class ChartPropertyDialogController {
   /**
    * Creates a new instance of <tt>ChartPropertyController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public ChartPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                        ChartPropertyDialogServiceProxy dialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.dialogServiceProxy = dialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the chart descriptor.
    */

   @RequestMapping(
      value = "/api/composer/vs/chart-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ChartPropertyDialogModel getChartPropertyDialogModel(@PathVariable("objectId") String objectId,
                                                               @RemainingPath String runtimeId,
                                                               Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogServiceProxy.getChartPropertyDialogModel(runtimeId, objectId, principal);
   }

   @PostMapping("api/composer/vs/chart-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkVSTrap(@RequestBody ChartPropertyDialogModel value,
                                       @PathVariable("objectId") String objectId,
                                       @RemainingPath String runtimeId,
                                       Principal principal)
      throws Exception
   {
      return dialogServiceProxy.checkVSTrap(runtimeId, value, objectId, principal);
   }

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the chart property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/chart-property-dialog-model/{objectId}")
   public void setChartPropertyModel(@DestinationVariable("objectId") String objectId,
                                     @Payload ChartPropertyDialogModel value,
                                     @LinkUri String linkUri,
                                     Principal principal,
                                     CommandDispatcher commandDispatcher)
      throws Exception
   {
      dialogServiceProxy.setChartPropertyModel(runtimeViewsheetRef.getRuntimeId(), objectId, value,
                                               linkUri, principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ChartPropertyDialogServiceProxy dialogServiceProxy;
}
