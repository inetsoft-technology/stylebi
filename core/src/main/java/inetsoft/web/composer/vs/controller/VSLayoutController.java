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
package inetsoft.web.composer.vs.controller;

import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.event.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.DataTipInLayoutCheckResult;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides endpoints for viewsheet layout actions.
 */
@Controller
public class VSLayoutController {
   /**
    * Creates a new instance of <tt>VSLayoutController</tt>.
    */
   @Autowired
   public VSLayoutController(RuntimeViewsheetRef runtimeViewsheetRef,
                             VSLayoutControllerServiceProxy vsLayoutControllerService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsLayoutControllerService = vsLayoutControllerService;
   }

   /**
    * Get the info for selected layout.
    *
    * @param layoutName the name of the layout being retrieved.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/vs/changeLayout/{layoutName}")
   public void changeViewsheetLayout(@DestinationVariable("layoutName") String layoutName,
                                     Principal principal,
                                     @LinkUri String linkUri,
                                     CommandDispatcher dispatcher)
      throws Exception
   {
      String id = this.runtimeViewsheetRef.getRuntimeId();
      String currLayout = this.runtimeViewsheetRef.getFocusedLayoutName();
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      vsLayoutControllerService.changeViewsheetLayout(id, currLayout, layoutName, principal, linkUri, dispatcher);
   }

   /**
    * Undo/revert to a previous viewsheet state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/vs/layouts/undo/{runtimeId}")
   public void layoutUndo(@DestinationVariable("runtimeId") String runtimeId, Principal principal,
                          @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      String layoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      vsLayoutControllerService.layoutUndo(Tool.byteDecode(runtimeId),
                                           this.runtimeViewsheetRef.getRuntimeId(),
                                           layoutName, principal, linkUri, dispatcher);
   }

   /**
    * Redo/change to a future viewsheet layout state.
    *
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get or refresh viewsheet
    */
   @LoadingMask
   @MessageMapping("composer/vs/layouts/redo/{runtimeId}")
   public void layoutRedo(@DestinationVariable("runtimeId") String runtimeId, Principal principal,
                          @LinkUri String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      String layoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      vsLayoutControllerService.layoutRedo(Tool.byteDecode(runtimeId),
                                           this.runtimeViewsheetRef.getRuntimeId(),
                                           layoutName, principal, linkUri, dispatcher);
   }

   /**
    * Move/Resize layout object.
    *
    * @param event      the event model.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/vs/layouts/moveResizeObjects")
   public void moveResizeLayoutObjects(@Payload MoveResizeLayoutObjectsEvent event,
                                       Principal principal,
                                       CommandDispatcher dispatcher)
      throws Exception
   {
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      vsLayoutControllerService.moveResizeLayoutObjects(this.runtimeViewsheetRef.getRuntimeId(),
                                                        focusedLayoutName,
                                                        event, principal, dispatcher);
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
   }

   /**
    * Drop object from component tree into layout.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @MessageMapping("composer/vs/layouts/addObject")
   public void addObject(@Payload AddVSLayoutObjectEvent event,
                         Principal principal,
                         CommandDispatcher dispatcher)
      throws Exception
   {
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      vsLayoutControllerService.addObject(this.runtimeViewsheetRef.getRuntimeId(),
                                          focusedLayoutName, event, principal, dispatcher);
      runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
   }

   /**
    * Removes an object from the layout.
    *
    * @param event             the event model
    * @param principal         the principal
    * @param commandDispatcher the command dispatcher
    */
   @MessageMapping("/composer/vs/layouts/removeObjects")
   public void removeObject(@Payload RemoveVSLayoutObjectEvent event,
                            Principal principal,
                            CommandDispatcher commandDispatcher)
      throws Exception
   {
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      vsLayoutControllerService.removeObject(this.runtimeViewsheetRef.getRuntimeId(),
                                          focusedLayoutName, event, principal, commandDispatcher);
      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
   }

   /**
    * Gets the top-level descriptor of a text component belonging to a layout.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param runtimeId  the runtime identifier of the viewsheet.
    *
    * @return the text descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/layouts/text-property-dialog/{region}/{layoutName}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public TextPropertyDialogModel getTextPropertyDialogModel(
      @PathVariable("region") int region, @PathVariable("layoutName") String layoutName,
      @RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      return vsLayoutControllerService.getTextPropertyDialogModel(runtimeId, region,
                                                                  layoutName, principal);
   }

   /**
    * Sets the specified text assembly info for layout object.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param value      the info model.
    */
   @MessageMapping("/composer/vs/layouts/text-property-dialog/{region}/{layoutName}")
   public void setTextPropertyDialogModel(@DestinationVariable("region") int region,
                                          @DestinationVariable("layoutName") String layoutName,
                                          @Payload TextPropertyDialogModel value,
                                          Principal principal,
                                          CommandDispatcher commandDispatcher)
      throws Exception
   {
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      boolean isPresent = vsLayoutControllerService.setTextPropertyDialogModel(
         this.runtimeViewsheetRef.getRuntimeId(), focusedLayoutName, region,
         layoutName, value, principal, commandDispatcher);

      if(isPresent) {
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      }
   }

   /**
    * Gets the top-level descriptor of an image object contained in a layout.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param runtimeId  the runtime identifier of the viewsheet.
    *
    * @return the image descriptor.
    */
   @RequestMapping(
      value = "/api/composer/vs/layouts/image-property-dialog/{region}/{layoutName}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ImagePropertyDialogModel getImagePropertyDialogModel(
      @PathVariable("region") int region, @PathVariable("layoutName") String layoutName,
      @RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      return vsLayoutControllerService.getImagePropertyDialogModel(runtimeId, region,
                                                                   layoutName, principal);
   }

   /**
    * Sets the assembly info of an image layout object.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    * @param value      the info model.
    */
   @MessageMapping("/composer/vs/layouts/image-property-dialog/{region}/{layoutName}")
   public void setImagePropertyDialogModel(@DestinationVariable("region") int region,
                                           @DestinationVariable("layoutName") String layoutName,
                                           @Payload ImagePropertyDialogModel value,
                                           Principal principal,
                                           CommandDispatcher commandDispatcher)
      throws Exception
   {
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      boolean isPresent = vsLayoutControllerService.setImagePropertyDialogModel(
         this.runtimeViewsheetRef.getRuntimeId(), focusedLayoutName, region,
         layoutName, value, principal, commandDispatcher);

      if(isPresent) {
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      }
   }

   /**
    * Sets the assembly info of an image layout object.
    *
    * @param region     the print layout region (header, footer, or content).
    * @param layoutName the runtime identifier of the text object.
    */
   @MessageMapping("/composer/vs/layouts/table-layout-property-dialog/{region}/{layoutName}")
   public void setTableLayoutPropertyDialogModel(@DestinationVariable("region") int region,
                                             @DestinationVariable("layoutName") String layoutName,
                                             @Payload TableLayoutPropertyDialogModel model,
                                             Principal principal,
                                             CommandDispatcher commandDispatcher)
      throws Exception
   {
      String focusedLayoutName = this.runtimeViewsheetRef.getFocusedLayoutName();
      boolean isPresent = vsLayoutControllerService.setTableLayoutPropertyDialogModel(
         this.runtimeViewsheetRef.getRuntimeId(), focusedLayoutName, region,
         layoutName, model, principal, commandDispatcher);

      if(isPresent) {
         this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      }
   }

   @RequestMapping(
      value="/api/vs/layouts/check-assembly-in-layout/{layoutName}/{assemblyName}/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public DataTipInLayoutCheckResult checkAssemblyInLayout(@PathVariable("layoutName") String layoutName,
                                                           @PathVariable("assemblyName") String assemblyName,
                                                           @RemainingPath String runtimeId,
                                                           Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return vsLayoutControllerService
         .checkAssemblyInLayout(runtimeId, layoutName, assemblyName, principal);
   }

   public static final int VIEWSHEETLAYOUT = 4;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSLayoutControllerServiceProxy vsLayoutControllerService;
}
