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

@Service
@ClusterProxy
public class TextPropertyDialogService {

   public TextPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                    VSOutputService vsOutputService,
                                    ViewsheetService engine,
                                    VSDialogService dialogService,
                                    VSTrapService trapService,
                                    VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.engine = engine;
      this.dialogService = dialogService;
      this.trapService = trapService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public TextPropertyDialogModel getTextPropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                             String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      TextVSAssembly textAssembly;
      TextVSAssemblyInfo textAssemblyInfo = null;

      try {
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         textAssembly = (TextVSAssembly) vs.getAssembly(objectId);

         if(textAssembly != null) {
            textAssemblyInfo = (TextVSAssemblyInfo) textAssembly.getVSAssemblyInfo();
         }
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      TextPropertyDialogModel result = new TextPropertyDialogModel();

      if(textAssemblyInfo == null) {
         return result;
      }

      TextGeneralPaneModel textGeneralPaneModel = result.getTextGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         textGeneralPaneModel.getSizePositionPaneModel();
      PaddingPaneModel padding = textGeneralPaneModel.getPaddingPaneModel();
      OutputGeneralPaneModel outputGeneralPaneModel =
         textGeneralPaneModel.getOutputGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = outputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TextPaneModel textPaneModel = textGeneralPaneModel.getTextPaneModel();
      DataOutputPaneModel dataOutputPaneModel = result.getDataOutputPaneModel();
      TipPaneModel tipPaneModel = textGeneralPaneModel.getTipPaneModel();
      TipCustomizeDialogModel tipCustomizeDialogModel = tipPaneModel.getTipCustomizeDialogModel();
      String[] dataRefList = VSUtil.getDataRefList(textAssemblyInfo, rvs);
      tipCustomizeDialogModel.setDataRefList(dataRefList);
      tipCustomizeDialogModel.setAvailableTipValues(VSUtil.getAvailableTipValues(dataRefList));

      ClickableScriptPaneModel.Builder clickableScriptPaneModel =
         ClickableScriptPaneModel.builder();

      textGeneralPaneModel.setPopComponent(textAssemblyInfo.getPopComponentValue() == null ?
                                              "" : vs.getAssembly(textAssemblyInfo.getPopComponentValue()) == null ?
         "" : textAssemblyInfo.getPopComponentValue());
      textGeneralPaneModel.setPopLocation(textAssemblyInfo.getPopLocationValue());
      textGeneralPaneModel.setAlpha(textAssemblyInfo.getAlphaValue() == null ?
                                       "100" : textAssemblyInfo.getAlphaValue());
      textGeneralPaneModel.setPopComponents(this.vsObjectPropertyService.getSupportedPopComponents(
         vs, textAssemblyInfo.getAbsoluteName()));

      Point pos = dialogService.getAssemblyPosition(textAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(textAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(textAssembly.getContainer() != null);
      sizePositionPaneModel.setScaleVertical(textAssemblyInfo.getScaleVerticalValue());

      padding.setTop(textAssemblyInfo.getPadding().top);
      padding.setLeft(textAssemblyInfo.getPadding().left);
      padding.setBottom(textAssemblyInfo.getPadding().bottom);
      padding.setRight(textAssemblyInfo.getPadding().right);

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(textAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(textAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setVisible(textAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setPrimary(textAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(true);
      basicGeneralPaneModel.setShadow(textAssemblyInfo.getShadowValue());
      basicGeneralPaneModel.setObjectNames(
         this.vsObjectPropertyService.getObjectNames(vs, textAssemblyInfo.getAbsoluteName()));

      textPaneModel.setText(textAssemblyInfo.getTextValue());
      textPaneModel.setAutoSize(textAssemblyInfo.getAutoSizeValue());
      textPaneModel.setUrl(textAssemblyInfo.getUrlValue());

      tipPaneModel.setTipOption(textAssemblyInfo.isTooltipVisible());
      String customTip = textAssemblyInfo.getCustomTooltipString();

      if(customTip != null && !customTip.isEmpty()) {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.CUSTOM);
         tipCustomizeDialogModel.setCustomTip(customTip);
         tipCustomizeDialogModel.setLineChart(false);
      }
      else {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.DEFAULT);
         tipCustomizeDialogModel.setLineChart(false);
      }

      ScalarBindingInfo outputBinding = textAssemblyInfo.getScalarBindingInfo();
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

      String script = textAssemblyInfo.getScript() == null ?
         "" : textAssemblyInfo.getScript();
      String onClick = textAssemblyInfo.getOnClick() == null ?
         "" : textAssemblyInfo.getOnClick();
      clickableScriptPaneModel.scriptEnabled(textAssemblyInfo.isScriptEnabled());
      clickableScriptPaneModel.scriptExpression(script);
      clickableScriptPaneModel.onClickExpression(onClick);
      result.setClickableScriptPaneModel(clickableScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setTextPropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                          TextPropertyDialogModel value, String linkUri,
                                          Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      TextVSAssemblyInfo textAssemblyInfo;

      try {
         rvs = engine.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         TextVSAssembly textAssembly = (TextVSAssembly) vs.getAssembly(objectId);
         textAssemblyInfo = (TextVSAssemblyInfo) Tool.clone(textAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      TextGeneralPaneModel textGeneralPaneModel = value.getTextGeneralPaneModel();
      OutputGeneralPaneModel outputGeneralPaneModel =
         textGeneralPaneModel.getOutputGeneralPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         textGeneralPaneModel.getSizePositionPaneModel();
      PaddingPaneModel padding = textGeneralPaneModel.getPaddingPaneModel();
      GeneralPropPaneModel generalPropPaneModel = outputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      TextPaneModel textPaneModel = textGeneralPaneModel.getTextPaneModel();
      DataOutputPaneModel dataOutputPaneModel = value.getDataOutputPaneModel();
      ClickableScriptPaneModel clickableScriptPaneModel = value.getClickableScriptPaneModel();
      TipPaneModel tipPaneModel = textGeneralPaneModel.getTipPaneModel();
      TipCustomizeDialogModel tipCustomizeDialogModel = tipPaneModel.getTipCustomizeDialogModel();

      String str = textGeneralPaneModel.getPopComponent();
      str = str != null && str.length() > 0 ? str : null;
      textAssemblyInfo.setPopComponentValue(str);
      textAssemblyInfo.setPopOptionValue(
         str != null ? PopVSAssemblyInfo.POP_OPTION : PopVSAssemblyInfo.NO_POP_OPTION);
      textAssemblyInfo.setPopLocationValue(textGeneralPaneModel.getPopLocation());
      str = textGeneralPaneModel.getAlpha();
      textAssemblyInfo.setAlphaValue(str != null && str.length() > 0 ? str : null);

      textAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      textAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      textAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      textAssemblyInfo.setShadowValue(basicGeneralPaneModel.isShadow());

      textAssemblyInfo.setTextValue(textPaneModel.getText());
      textAssemblyInfo.setAutoSizeValue(textPaneModel.isAutoSize());
      textAssemblyInfo.setUrlValue(textPaneModel.isUrl());

      textAssemblyInfo.setTooltipVisible(tipPaneModel.isTipOption());

      if(tipCustomizeDialogModel.getCustomRB() == TipCustomizeDialogModel.TipFormat.CUSTOM) {
         String customTip = tipCustomizeDialogModel.getCustomTip();
         customTip = (customTip == null || customTip.isEmpty()) ? null : customTip;
         textAssemblyInfo.setCustomTooltipString(customTip);
      }
      else {
         textAssemblyInfo.setCustomTooltipString(null);
      }

      textAssemblyInfo.getPadding().top = padding.getTop();
      textAssemblyInfo.getPadding().left = padding.getLeft();
      textAssemblyInfo.getPadding().bottom = padding.getBottom();
      textAssemblyInfo.getPadding().right = padding.getRight();

      dialogService.setAssemblySize(textAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(textAssemblyInfo, sizePositionPaneModel);
      textAssemblyInfo.setScaleVerticalValue(sizePositionPaneModel.isScaleVertical());

      ScalarBindingInfo info = new ScalarBindingInfo();

      if(dataOutputPaneModel.getTable() != null) {
         info.setTableName(dataOutputPaneModel.getTable());
         info.setColumnValue(dataOutputPaneModel.getColumn());
         info.setScale(dataOutputPaneModel.getMagnitude());
         String formula = dataOutputPaneModel.getAggregate();
         info.setAggregateValue(formula);
         info.changeColumnType(formula, dataOutputPaneModel.getColumnType() == null ?
            XSchema.STRING : dataOutputPaneModel.getColumnType());

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

      textAssemblyInfo.setScalarBindingInfo(info);

      textAssemblyInfo.setScriptEnabled(clickableScriptPaneModel.scriptEnabled());

      textAssemblyInfo.setOnClick(clickableScriptPaneModel.onClickExpression() == null ? "" : clickableScriptPaneModel.onClickExpression());
      textAssemblyInfo.setScript(clickableScriptPaneModel.scriptExpression() == null ? "" : clickableScriptPaneModel.scriptExpression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, textAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, TextPropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      TextVSAssembly textVSAssembly =
         (TextVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(textVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(textVSAssembly.getVSAssemblyInfo());
      TextVSAssemblyInfo newAssemblyInfo =
         (TextVSAssemblyInfo) Tool.clone(textVSAssembly.getVSAssemblyInfo());
      setOutputValues(newAssemblyInfo, model, rvs, principal);

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   private void setOutputValues(TextVSAssemblyInfo textAssemblyInfo,
                                TextPropertyDialogModel model,
                                RuntimeViewsheet rvs,
                                Principal principal)
      throws Exception
   {
      ScalarBindingInfo info = new ScalarBindingInfo();
      DataOutputPaneModel dataOutputPaneModel = model.getDataOutputPaneModel();

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

      textAssemblyInfo.setScalarBindingInfo(info);
   }

   private final ViewsheetService engine;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final VSDialogService dialogService;
   private final VSTrapService trapService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
