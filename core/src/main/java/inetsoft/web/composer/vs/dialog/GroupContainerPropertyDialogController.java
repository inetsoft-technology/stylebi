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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
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
    * @param vsObjectPropertyService VSObjectPropertyService instance
    * @param runtimeViewsheetRef     RuntimeViewsheetRef instance
    */
   @Autowired
   public GroupContainerPropertyDialogController(VSObjectPropertyService vsObjectPropertyService,
                                                 RuntimeViewsheetRef runtimeViewsheetRef,
                                                 ViewsheetService viewsheetService,
                                                 CoreLifecycleService coreLifecycleService,
                                                 VSDialogService dialogService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.dialogService = dialogService;
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
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      GroupContainerVSAssembly imageAssembly =
         (GroupContainerVSAssembly) vs.getAssembly(objectId);
      GroupContainerVSAssemblyInfo info =
         (GroupContainerVSAssemblyInfo) imageAssembly.getVSAssemblyInfo();

      GroupContainerPropertyDialogModel groupContainerPropertyDialog =
         new GroupContainerPropertyDialogModel();

      GroupContainerGeneralPaneModel generalPaneModel = new GroupContainerGeneralPaneModel();
      groupContainerPropertyDialog.setGroupContainerGeneralPane(generalPaneModel);
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPane =
         VSAssemblyScriptPaneModel.builder();

      GeneralPropPaneModel generalPropPaneModel = new GeneralPropPaneModel();
      generalPaneModel.setGeneralPropPane(generalPropPaneModel);

      SizePositionPaneModel sizePositionPaneModel = new SizePositionPaneModel();
      generalPaneModel.setSizePositionPaneModel(sizePositionPaneModel);

      Point pos = info.getLayoutPosition() != null ?
         info.getLayoutPosition() :
         vs.getPixelPosition(info);
      Dimension size = info.getLayoutSize();

      if(size == null || size.width == 0 || size.height == 0) {
         size = vs.getPixelSize(info);
      }

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(imageAssembly.getContainer() != null);

      BasicGeneralPaneModel basicGeneralPaneModel = new BasicGeneralPaneModel();
      generalPropPaneModel.setBasicGeneralPaneModel(basicGeneralPaneModel);

      generalPropPaneModel.setShowEnabledGroup(false);

      basicGeneralPaneModel.setName(info.getAbsoluteName());
      basicGeneralPaneModel.setVisible(info.getVisibleValue());
      basicGeneralPaneModel.setPrimary(info.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(false);
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, info.getAbsoluteName()));

      String imageValue = info.getBackgroundImage();
      int imageAlpha;

      try {
         imageAlpha = Integer.parseInt(info.getImageAlphaValue());
      }
      catch(Exception e) {
         imageAlpha = 100;
      }

      ImagePreviewPaneController imageController = new ImagePreviewPaneController();

      ImagePreviewPaneModel imagePreviewPane = ImagePreviewPaneModel.builder()
         .alpha(imageAlpha)
         .animateGifImage(info.isAnimateGIF())
         .allowNullImage(true)
         .selectedImage(imageValue)
         .imageTree(imageController.getImageTree(rvs))
         .build();

      StaticImagePaneModel staticImagePane = StaticImagePaneModel.builder()
         .imagePreviewPaneModel(imagePreviewPane)
         .build();
      generalPaneModel.setStaticImagePane(staticImagePane);

      ImageScalePaneModel imageScalePaneModel = ImageScalePaneModel.builder()
         .scaleImageChecked(info.isScaleImageValue())
         .maintainAspectRatio(info.isMaintainAspectRatioValue())
         .tile(info.isTileValue())
         .insets(info.getScale9Value())
         .size(info.getPixelSize())
         .build();

      groupContainerPropertyDialog.setImageScalePane(imageScalePaneModel);

      vsAssemblyScriptPane.scriptEnabled(info.isScriptEnabled());
      vsAssemblyScriptPane.expression(info.getScript() == null ? "" : info.getScript());
      groupContainerPropertyDialog.setVsAssemblyScriptPane(vsAssemblyScriptPane.build());

      return groupContainerPropertyDialog;
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
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      GroupContainerVSAssembly imageAssembly =
         (GroupContainerVSAssembly) vs.getAssembly(objectId);
      GroupContainerVSAssemblyInfo info =
         (GroupContainerVSAssemblyInfo) imageAssembly.getVSAssemblyInfo();
      GroupContainerGeneralPaneModel groupContainerGeneralPane =
         value.getGroupContainerGeneralPane();
      GeneralPropPaneModel generalPropPane = groupContainerGeneralPane.getGeneralPropPane();
      BasicGeneralPaneModel basicGeneralPane = generalPropPane.getBasicGeneralPaneModel();
      ImageScalePaneModel scaleImagePane = value.getImageScalePane();
      StaticImagePaneModel staticImagePane = groupContainerGeneralPane.getStaticImagePane();
      SizePositionPaneModel sizePositionPaneModel =
         groupContainerGeneralPane.getSizePositionPaneModel();
      ImagePreviewPaneModel imagePreviewPane = staticImagePane.imagePreviewPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPane = value.getVsAssemblyScriptPane();

      info.setVisibleValue(basicGeneralPane.getVisible());
      info.setPrimary(basicGeneralPane.isPrimary());

      info.setImageAlphaValue(imagePreviewPane.alpha() + "");
      info.setAnimateGIF(imagePreviewPane.animateGifImage());

      if(imagePreviewPane.selectedImage() != null) {
         info.setBackgroundImage(imagePreviewPane.selectedImage());

         if(imagePreviewPane.selectedImage().startsWith(ImageVSAssemblyInfo.SKIN_IMAGE)) {
            info.setMaintainAspectRatioValue(false);
            info.setScale9Value(new Insets(1, 1, 1, 1));
         }
      }
      else {
         info.setBackgroundImage(null);
      }

      info.setScaleImageValue(scaleImagePane.scaleImageChecked());
      info.setTileValue(scaleImagePane.tile());

      if(scaleImagePane.scaleImageChecked()) {
         info.setMaintainAspectRatioValue(scaleImagePane.maintainAspectRatio());

         if(!scaleImagePane.maintainAspectRatio()) {
            Insets insets = new Insets(scaleImagePane.top(), scaleImagePane.left(),
                                       scaleImagePane.bottom(), scaleImagePane.right());
            info.setScale9Value(insets);
         }
      }

      if(sizePositionPaneModel.getLeft() >= 0 && sizePositionPaneModel.getTop() >= 0) {
         dialogService.setContainerPosition(info, sizePositionPaneModel,
                                            imageAssembly.getAssemblies(), vs);

         ChangedAssemblyList clist = this.coreLifecycleService.createList(false, commandDispatcher, rvs, linkUri);
         this.coreLifecycleService.layoutViewsheet(rvs, rvs.getID(), linkUri, commandDispatcher,
                                                 imageAssembly.getAbsoluteName(), clist);
      }

      info.setScriptEnabled(vsAssemblyScriptPane.scriptEnabled());
      info.setScript(vsAssemblyScriptPane.expression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, info, objectId, basicGeneralPane.getName(), linkUri, principal, commandDispatcher);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSDialogService dialogService;
}
