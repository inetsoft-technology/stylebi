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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
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
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.security.Principal;
import java.util.Optional;

@Service
@ClusterProxy
public class ImagePropertyDialogService {

   public ImagePropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                     VSOutputService vsOutputService,
                                     ViewsheetService viewsheetService,
                                     VSDialogService dialogService,
                                     VSTrapService trapService,
                                     VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.viewsheetService = viewsheetService;
      this.dialogService = dialogService;
      this.trapService = trapService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ImagePropertyDialogModel getImagePropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                               String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ImageVSAssembly imageAssembly;
      ImageVSAssemblyInfo imageAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         imageAssembly = (ImageVSAssembly) vs.getAssembly(objectId);
         imageAssemblyInfo = (ImageVSAssemblyInfo) imageAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      OutputGeneralPaneModel outputGeneralPaneModel = new OutputGeneralPaneModel();

      ImagePreviewPaneController imageController = new ImagePreviewPaneController();

      TipPaneModel tipPaneModel = new TipPaneModel();
      tipPaneModel.setTipOption(imageAssemblyInfo.isTooltipVisible());

      SizePositionPaneModel sizePositionPaneModel = new SizePositionPaneModel();
      sizePositionPaneModel.setLocked(imageAssemblyInfo.getLocked());
      String imageValue = imageAssemblyInfo.getImageValue();
      int imageAlpha;

      try {
         imageAlpha = Integer.parseInt(imageAssemblyInfo.getImageAlphaValue());
      }
      catch(Exception e) {
         imageAlpha = 100;
      }

      ImagePreviewPaneModel imagePreviewPaneModel = ImagePreviewPaneModel.builder()
         .alpha(imageAlpha)
         .animateGifImage(imageAssemblyInfo.isAnimateGIF())
         .selectedImage(imageValue)
         .imageTree(imageController.getImageTree(rvs))
         .build();

      StaticImagePaneModel staticImagePaneModel = StaticImagePaneModel.builder()
         .imagePreviewPaneModel(imagePreviewPaneModel)
         .build();

      ImageGeneralPaneModel imageGeneralPaneModel = ImageGeneralPaneModel.builder()
         .outputGeneralPaneModel(outputGeneralPaneModel)
         .staticImagePaneModel(staticImagePaneModel)
         .tipPaneModel(tipPaneModel)
         .sizePositionPaneModel(sizePositionPaneModel)
         .build();

      DataOutputPaneModel dataOutputPaneModel = new DataOutputPaneModel();

      String dynamicImageValue;

      if(imageValue != null) {
         dynamicImageValue = imageValue;
      }
      else {
         dynamicImageValue = "";
      }

      DynamicImagePaneModel dynamicImagePaneModel = DynamicImagePaneModel.builder()
         .dynamicImageSelected(imageAssemblyInfo.isDynamic())
         .dynamicImageValue(dynamicImageValue)
         .build();

      ImageScalePaneModel imageScalePaneModel = ImageScalePaneModel.builder()
         .scaleImageChecked(imageAssemblyInfo.isScaleImageValue())
         .maintainAspectRatio(imageAssemblyInfo.isMaintainAspectRatioValue())
         .tile(imageAssemblyInfo.isTileValue())
         .insets(imageAssemblyInfo.getScale9Value())
         .size(imageAssemblyInfo.getPixelSize())
         .build();

      ImageAdvancedPaneModel imageAdvancedPaneModel = ImageAdvancedPaneModel.builder()
         .dynamicImagePaneModel(dynamicImagePaneModel)
         .imageScalePaneModel(imageScalePaneModel)
         .popComponent(imageAssemblyInfo.getPopComponentValue() == null ?
                          "" : imageAssemblyInfo.getPopComponentValue())
         .alpha(imageAssemblyInfo.getAlphaValue() == null ?
                   "100" : imageAssemblyInfo.getAlphaValue())
         .popComponents(this.vsObjectPropertyService.getSupportedPopComponents(
            vs, imageAssemblyInfo.getAbsoluteName()))
         .popLocation(imageAssemblyInfo.getPopLocation())
         .build();

      ClickableScriptPaneModel.Builder clickableScriptPaneModel =
         ClickableScriptPaneModel.builder();

      String script = imageAssemblyInfo.getScript() == null ?
         "" : imageAssemblyInfo.getScript();
      String onClick = imageAssemblyInfo.getOnClick() == null ?
         "" : imageAssemblyInfo.getOnClick();
      clickableScriptPaneModel.scriptEnabled(imageAssemblyInfo.isScriptEnabled());
      clickableScriptPaneModel.scriptExpression(script);
      clickableScriptPaneModel.onClickExpression(onClick);

      ImagePropertyDialogModel result = ImagePropertyDialogModel.builder()
         .imageGeneralPaneModel(imageGeneralPaneModel)
         .dataOutputPaneModel(dataOutputPaneModel)
         .imageAdvancedPaneModel(imageAdvancedPaneModel)
         .clickableScriptPaneModel(clickableScriptPaneModel.build())
         .build();

      GeneralPropPaneModel generalPropPaneModel = outputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(imageAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(imageAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setVisible(imageAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setPrimary(imageAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(true);
      basicGeneralPaneModel.setShadow(imageAssemblyInfo.getShadowValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, imageAssemblyInfo.getAbsoluteName()));

      Point pos = dialogService.getAssemblyPosition(imageAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(imageAssemblyInfo, vs);

      TipCustomizeDialogModel tipCustomizeDialogModel = tipPaneModel.getTipCustomizeDialogModel();
      String[] dataRefList = VSUtil.getDataRefList(imageAssemblyInfo, rvs);
      tipCustomizeDialogModel.setDataRefList(dataRefList);
      tipCustomizeDialogModel.setAvailableTipValues(VSUtil.getAvailableTipValues(dataRefList));

      String customTip = imageAssemblyInfo.getCustomTooltipString();

      if(customTip != null && !customTip.isEmpty()) {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.CUSTOM);
         tipCustomizeDialogModel.setCustomTip(customTip);
         tipCustomizeDialogModel.setLineChart(false);
      }
      else {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.DEFAULT);
         tipCustomizeDialogModel.setLineChart(false);
      }

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(imageAssembly.getContainer() != null);

      ScalarBindingInfo outputBinding = imageAssemblyInfo.getScalarBindingInfo();
      dataOutputPaneModel.setTargetTree(this.vsOutputService.getOutputTablesTree(rvs, principal));
      dataOutputPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
      dataOutputPaneModel.setLogicalModel(vs.getBaseEntry() != null &&
                                             vs.getBaseEntry().getType() == AssetEntry.Type.LOGIC_MODEL);
      dataOutputPaneModel.setTableType(this.vsOutputService.getTableType(vs.getBaseEntry()));

      if(outputBinding != null) {
         dataOutputPaneModel.setTable(outputBinding.getTableName());
         dataOutputPaneModel.setColumn(outputBinding.getColumnValue());
         dataOutputPaneModel.setAggregate(outputBinding.getAggregateValue());
         dataOutputPaneModel.setWith(outputBinding.getColumn2Value());
         dataOutputPaneModel.setNum(outputBinding.getNValue());
         dataOutputPaneModel.setMagnitude(outputBinding.getScale());
         dataOutputPaneModel.setColumnType(outputBinding.getColumnType());
      }

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setImagePropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                           ImagePropertyDialogModel value, String linkUri,
                                           Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      ImageVSAssemblyInfo imageAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         ImageVSAssembly imageAssembly = (ImageVSAssembly) vs.getAssembly(objectId);
         imageAssemblyInfo = (ImageVSAssemblyInfo) Tool.clone(imageAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      ImageGeneralPaneModel imageGeneralPaneModel = value.imageGeneralPaneModel();
      OutputGeneralPaneModel outputGeneralPaneModel = imageGeneralPaneModel.outputGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         imageGeneralPaneModel.sizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel = outputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      StaticImagePaneModel staticImagePaneModel = imageGeneralPaneModel.staticImagePaneModel();
      ImagePreviewPaneModel previewPane = staticImagePaneModel.imagePreviewPaneModel();
      TipPaneModel tipPaneModel = imageGeneralPaneModel.tipPaneModel();
      TipCustomizeDialogModel tipCustomizeDialogModel = tipPaneModel.getTipCustomizeDialogModel();
      DataOutputPaneModel dataOutputPaneModel = value.dataOutputPaneModel();
      ImageAdvancedPaneModel advPane = value.imageAdvancedPaneModel();
      DynamicImagePaneModel dynamicImagePaneModel = advPane.dynamicImagePaneModel();
      ImageScalePaneModel imageScalePaneModel = advPane.imageScalePaneModel();
      ClickableScriptPaneModel clickableScriptPaneModel = value.clickableScriptPaneModel();

      String str = advPane.popComponent();
      str = str != null && str.length() > 0 ? str : null;
      imageAssemblyInfo.setPopComponentValue(str);
      imageAssemblyInfo.setPopOptionValue(
         str != null ? PopVSAssemblyInfo.POP_OPTION : PopVSAssemblyInfo.NO_POP_OPTION);
      imageAssemblyInfo.setPopLocationValue(advPane.popLocation());
      str = advPane.alpha();
      imageAssemblyInfo.setAlphaValue(str != null && str.length() > 0 ? str : null);

      imageAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      imageAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      imageAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      imageAssemblyInfo.setShadowValue(basicGeneralPaneModel.isShadow());

      imageAssemblyInfo.setAnimateGIF(previewPane.animateGifImage());
      imageAssemblyInfo.setImageAlphaValue(Integer.toString(previewPane.alpha()));

      // allow dynamic value to be cleared
      if(advPane.dynamicImagePaneModel().dynamicImageSelected()) {
         imageAssemblyInfo.setImageValue(advPane.dynamicImagePaneModel().dynamicImageValue());
      }
      else if(previewPane.selectedImage() != null) {
         imageAssemblyInfo.setImageValue(previewPane.selectedImage());

         if(previewPane.selectedImage().startsWith(ImageVSAssemblyInfo.SKIN_IMAGE)) {
            imageAssemblyInfo.setMaintainAspectRatioValue(false);
            imageAssemblyInfo.setScale9Value(new Insets(1, 1, 1, 1));
         }
      }
      else {
         imageAssemblyInfo.setImageValue(null);
      }

      imageAssemblyInfo.setTooltipVisible(tipPaneModel.isTipOption());

      if(tipCustomizeDialogModel.getCustomRB() == TipCustomizeDialogModel.TipFormat.CUSTOM) {
         String customTip = tipCustomizeDialogModel.getCustomTip();
         customTip = (customTip == null || customTip.isEmpty()) ? null : customTip;
         imageAssemblyInfo.setCustomTooltipString(customTip);
      }
      else {
         imageAssemblyInfo.setCustomTooltipString(null);
      }

      dialogService.setAssemblySize(imageAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(imageAssemblyInfo, sizePositionPaneModel);

      ScalarBindingInfo info = new ScalarBindingInfo();

      if(dataOutputPaneModel.getTable() != null) {
         info.setTableName(dataOutputPaneModel.getTable());
         info.setColumnValue(dataOutputPaneModel.getColumn());
         info.setColumnType(dataOutputPaneModel.getColumnType() == null ?
                               XSchema.STRING : dataOutputPaneModel.getColumnType());
         info.setScale(dataOutputPaneModel.getMagnitude());
         String formula = dataOutputPaneModel.getAggregate();
         info.setAggregateValue(formula);

         if(dataOutputPaneModel.getWith() != null) {
            info.setColumn2Value(dataOutputPaneModel.getWith());
         }

         info.setNValue(dataOutputPaneModel.getNum());

         if(formula != null && !formula.isEmpty() && !"=".equals(formula)) {
            AggregateFormula af = AggregateFormula.getFormula(formula);
            String dataType = af == null ? null : af.getDataType();

            if(dataType != null && !dataType.isEmpty()) {
               info.setColumnType(dataType);
            }
         }
      }

      imageAssemblyInfo.setScalarBindingInfo(info);

      if(dynamicImagePaneModel.dynamicImageSelected()) {
         str = Optional.ofNullable(dynamicImagePaneModel.dynamicImageValue()).orElse(null);

         if(str != null && str.length() > 0) {
            imageAssemblyInfo.setDynamic(dynamicImagePaneModel.dynamicImageSelected());
            imageAssemblyInfo.setImageValue(str);
         }
      }
      else {
         imageAssemblyInfo.setDynamic(false);
      }

      imageAssemblyInfo.setScaleImageValue(imageScalePaneModel.scaleImageChecked());
      imageAssemblyInfo.setTileValue(imageScalePaneModel.tile());

      if(imageScalePaneModel.scaleImageChecked()) {
         imageAssemblyInfo.setMaintainAspectRatioValue(imageScalePaneModel.maintainAspectRatio());

         if(!imageScalePaneModel.maintainAspectRatio()) {
            Insets insets = new Insets(imageScalePaneModel.top(), imageScalePaneModel.left(),
                                       imageScalePaneModel.bottom(), imageScalePaneModel.right());
            imageAssemblyInfo.setScale9Value(insets);
         }
      }

      imageAssemblyInfo.setScriptEnabled(clickableScriptPaneModel.scriptEnabled());

      imageAssemblyInfo.setOnClick(clickableScriptPaneModel.onClickExpression() == null ? "" : clickableScriptPaneModel.onClickExpression());
      imageAssemblyInfo.setScript(clickableScriptPaneModel.scriptExpression() == null ? "" : clickableScriptPaneModel.scriptExpression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, imageAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, ImagePropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ImageVSAssembly imageVSAssembly =
         (ImageVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(imageVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(imageVSAssembly.getVSAssemblyInfo());
      ImageVSAssemblyInfo newAssemblyInfo =
         (ImageVSAssemblyInfo) Tool.clone(imageVSAssembly.getVSAssemblyInfo());
      setOutputValues(newAssemblyInfo, model);

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   private void setOutputValues(ImageVSAssemblyInfo imageAssemblyInfo,
                                ImagePropertyDialogModel model)
      throws Exception
   {
      ScalarBindingInfo info = new ScalarBindingInfo();
      DataOutputPaneModel dataOutputPaneModel = model.dataOutputPaneModel();

      if(dataOutputPaneModel.getTable() != null) {
         info.setTableName(dataOutputPaneModel.getTable());
         info.setColumnValue(dataOutputPaneModel.getColumn());
         info.setColumnType(dataOutputPaneModel.getColumnType() == null ?
                               XSchema.STRING : dataOutputPaneModel.getColumnType());
         info.setScale(dataOutputPaneModel.getMagnitude());
         String formula = dataOutputPaneModel.getAggregate();
         info.setAggregateValue(formula);

         if(dataOutputPaneModel.getWith() != null) {
            info.setColumn2Value(dataOutputPaneModel.getWith());
         }

         info.setNValue(dataOutputPaneModel.getNum());

         if(formula != null && !formula.isEmpty() && !"=".equals(formula)) {
            AggregateFormula af = AggregateFormula.getFormula(formula);
            String dataType = af == null ? null : af.getDataType();

            if(dataType != null && !dataType.isEmpty()) {
               info.setColumnType(dataType);
            }
         }
      }

      imageAssemblyInfo.setScalarBindingInfo(info);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final ViewsheetService viewsheetService;
   private final VSDialogService dialogService;
   private final VSTrapService trapService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
