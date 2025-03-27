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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the text property dialog.
 *
 * @since 12.3
 */
@Controller
public class TextPropertyDialogController {
   /**
    * Creates a new instance of <tt>TextPropertyDialogController</tt>.
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public TextPropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                       TextPropertyDialogServiceProxy textPropertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.textPropertyDialogServiceProxy = textPropertyDialogServiceProxy;
   }

   /**
    * Gets the top-level descriptor of the text.
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param objectId the runtime identifier of the text object.
    *
    * @return the text descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/text-property-dialog-model/{objectId}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TextPropertyDialogModel getTextPropertyDialogModel(
      @PathVariable("objectId") String objectId, @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      return textPropertyDialogServiceProxy.getTextPropertyDialogModel(runtimeId, objectId, principal);
   }

   /**
    * Sets the specified text assembly info.
    *
    * @param objectId   the text id
    * @param value the text dialog model.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/text-property-dialog-model/{objectId}")
   @HandleAssetExceptions
   public void setTextPropertyDialogModel(@DestinationVariable("objectId") String objectId,
                                           @Payload TextPropertyDialogModel value,
                                           @LinkUri String linkUri,
                                           Principal principal,
                                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      textPropertyDialogServiceProxy.setTextPropertyDialogModel(runtimeViewsheetRef.getRuntimeId(),
                                                                objectId, value, linkUri, principal, commandDispatcher);
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
   @PostMapping("/api/composer/vs/text-property-dialog-model/checkTrap/{objectId}/**")
   @ResponseBody
   public VSTableTrapModel checkTrap(@RequestBody() TextPropertyDialogModel model,
                                     @PathVariable("objectId") String objectId,
                                     @RemainingPath String runtimeId,
                                     Principal principal) throws Exception
   {
      return textPropertyDialogServiceProxy.checkTrap(runtimeId, model, objectId, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final TextPropertyDialogServiceProxy textPropertyDialogServiceProxy;
}
