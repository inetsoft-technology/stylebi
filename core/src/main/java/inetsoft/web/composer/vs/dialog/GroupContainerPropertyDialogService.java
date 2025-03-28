/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;

@Service
@ClusterProxy
public class GroupContainerPropertyDialogService {

   public GroupContainerPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                              ViewsheetService viewsheetService,
                                              CoreLifecycleService coreLifecycleService,
                                              VSDialogService dialogService)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.dialogService = dialogService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public GroupContainerPropertyDialogModel getGroupContainerPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                                                 String objectId, Principal principal) throws Exception
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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setGroupContainerPropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                                    GroupContainerPropertyDialogModel value, String linkUri,
                                                    Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
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

      return null;
   }


   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSDialogService dialogService;
}
