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

import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.graph.model.dialog.*;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
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
public class RegionPropertyDialogController {
   /**
    * Creates a new instance of <tt>ChartPropertyController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public RegionPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                         RegionPropertyDialogServiceProxy regionPropertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.regionPropertyDialogServiceProxy = regionPropertyDialogServiceProxy;
   }

   /**
    * Gets the axis property dialog model of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the property dialog model.
    */

   @RequestMapping(
      value = "/api/composer/vs/axis-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public AxisPropertyDialogModel getAxisPropertyDialogModel(
      @PathVariable("objectId") String objectId,
      @RequestParam("axisType") String axisType,
      @RequestParam("index") String index,
      @RequestParam("field") String field,
      @RemainingPath String runtimeId,
      @LinkUri String linkUri,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return regionPropertyDialogServiceProxy.getAxisPropertyDialogModel(runtimeId, objectId, axisType,
                                                             index, field, linkUri, principal);
   }

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the axis property dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/axis-property-dialog-model/{objectId}/{axisType}/{index}/{field}")
   public void setAxisPropertyDialogModel(
      @DestinationVariable("objectId") String objectId,
      @DestinationVariable("axisType") String axisType,
      @DestinationVariable("index") int index,
      @DestinationVariable("field") String field,
      @Payload AxisPropertyDialogModel value,
      @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      regionPropertyDialogServiceProxy.setAxisPropertyDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                             objectId, axisType, index, field, value,
                                                             linkUri, principal, commandDispatcher);
   }

   /**
    * Gets the legend format dialog model of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the legend format dialog model.
    */

   @RequestMapping(
      value = "/api/composer/vs/legend-format-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public LegendFormatDialogModel getLegendFormatDialogModel(
      @PathVariable("objectId") String objectId,
      @RequestParam("index") String index,
      @RemainingPath String runtimeId,
      @LinkUri String linkUri,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return regionPropertyDialogServiceProxy.getLegendFormatDialogModel(runtimeId, objectId, index,
                                                                    linkUri, principal);
   }

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the legend format dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/legend-format-dialog-model/{objectId}/{index}")
   public void setLegendFormatDialogModel(
      @DestinationVariable("objectId") String objectId,
      @DestinationVariable("index") int index,
      @Payload LegendFormatDialogModel value,
      @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      regionPropertyDialogServiceProxy.setLegendFormatDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                             objectId, index, value, linkUri,
                                                             principal, commandDispatcher);
   }

   /**
    * Gets the title format dialog model of the chart
    *
    * @param runtimeId  the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the chart.
    *
    * @return the property dialog model.
    */
   @RequestMapping(
      value = "/api/composer/vs/title-format-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TitleFormatDialogModel getTitleFormatDialogModel(
      @PathVariable("objectId") String objectId,
      @RequestParam("axisType") String axisType,
      @RemainingPath String runtimeId,
      @LinkUri String linkUri,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return regionPropertyDialogServiceProxy.getTitleFormatDialogModel(runtimeId, objectId, axisType,
                                                                   linkUri, principal);
   }

   /**
    * Sets the specified chart assembly info.
    *
    * @param objectId   the chart id
    * @param value the title format dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/title-format-dialog-model/{objectId}/{axisType}")
   public void setTitleFormatDialogModel(
      @DestinationVariable("objectId") String objectId,
      @DestinationVariable("axisType") String axisType,
      @Payload TitleFormatDialogModel value,
      @LinkUri String linkUri,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      regionPropertyDialogServiceProxy.setTitleFormatDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                            objectId, axisType, value, linkUri,
                                                            principal, commandDispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final RegionPropertyDialogServiceProxy regionPropertyDialogServiceProxy;

}
