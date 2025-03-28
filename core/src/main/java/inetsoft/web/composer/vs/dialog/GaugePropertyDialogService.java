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
import java.util.ArrayList;
import java.util.List;

@Service
@ClusterProxy
public class GaugePropertyDialogService {

   public GaugePropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                     VSOutputService vsOutputService,
                                     VSDialogService dialogService,
                                     ViewsheetService viewsheetService,
                                     VSTrapService trapService,
                                     VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.dialogService = dialogService;
      this.viewsheetService = viewsheetService;
      this.trapService = trapService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public GaugePropertyDialogModel getGaugePropertyDialogModel(@ClusterProxyKey String runtimeId,
                                                               String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      GaugeVSAssembly gaugeAssembly;
      GaugeVSAssemblyInfo gaugeAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         gaugeAssembly = (GaugeVSAssembly) vs.getAssembly(objectId);
         gaugeAssemblyInfo = (GaugeVSAssemblyInfo) gaugeAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         //TODO decide what to do with exception
         throw e;
      }

      GaugePropertyDialogModel result = new GaugePropertyDialogModel();
      GaugeGeneralPaneModel gaugeGeneralPaneModel = result.getGaugeGeneralPaneModel();
      OutputGeneralPaneModel outputGeneralPaneModel =
         gaugeGeneralPaneModel.getOutputGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         outputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      NumberRangePaneModel numberRangePaneModel =
         gaugeGeneralPaneModel.getNumberRangePaneModel();
      FacePaneModel facePaneModel = gaugeGeneralPaneModel.getFacePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         gaugeGeneralPaneModel.getSizePositionPaneModel();
      DataOutputPaneModel dataOutputPaneModel = result.getDataOutputPaneModel();
      GaugeAdvancedPaneModel gaugeAdvancedPaneModel = result.getGaugeAdvancedPaneModel();
      RangePaneModel rangePaneModel = gaugeAdvancedPaneModel.getRangePaneModel();
      PaddingPaneModel paddingPaneModel = gaugeGeneralPaneModel.getPaddingPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();
      TipPaneModel tipPaneModel = gaugeGeneralPaneModel.getTipPaneModel();
      TipCustomizeDialogModel tipCustomizeDialogModel = tipPaneModel.getTipCustomizeDialogModel();
      String[] dataRefList = VSUtil.getDataRefList(gaugeAssemblyInfo, rvs);
      String[] availableTipValues = VSUtil.getAvailableTipValues(dataRefList);
      tipCustomizeDialogModel.setDataRefList(dataRefList);
      tipCustomizeDialogModel.setAvailableTipValues(availableTipValues);

      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(gaugeAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(gaugeAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setVisible(gaugeAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setPrimary(gaugeAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setShowShadowCheckbox(true);
      basicGeneralPaneModel.setShadow(gaugeAssemblyInfo.getShadowValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, gaugeAssemblyInfo.getAbsoluteName()));

      numberRangePaneModel.setMin(gaugeAssemblyInfo.getMinValue());
      numberRangePaneModel.setMax(gaugeAssemblyInfo.getMaxValue());
      numberRangePaneModel.setMinorIncrement(gaugeAssemblyInfo.getMinorIncValue());
      numberRangePaneModel.setMajorIncrement(gaugeAssemblyInfo.getMajorIncValue());

      facePaneModel.setFace(gaugeAssemblyInfo.getFace());

      Point pos = dialogService.getAssemblyPosition(gaugeAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(gaugeAssemblyInfo, vs);

      sizePositionPaneModel.setPositions(pos, size);
      sizePositionPaneModel.setContainer(gaugeAssembly.getContainer() != null);

      tipPaneModel.setTipOption(gaugeAssemblyInfo.isTooltipVisible());
      String customTip = gaugeAssemblyInfo.getCustomTooltipString();

      if(customTip != null && !customTip.isEmpty()) {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.CUSTOM);
         tipCustomizeDialogModel.setCustomTip(customTip);
         tipCustomizeDialogModel.setLineChart(false);
      }
      else {
         tipCustomizeDialogModel.setCustomRB(TipCustomizeDialogModel.TipFormat.DEFAULT);
         tipCustomizeDialogModel.setLineChart(false);
      }

      paddingPaneModel.setTop(gaugeAssemblyInfo.getPadding().top);
      paddingPaneModel.setLeft(gaugeAssemblyInfo.getPadding().left);
      paddingPaneModel.setBottom(gaugeAssemblyInfo.getPadding().bottom);
      paddingPaneModel.setRight(gaugeAssemblyInfo.getPadding().right);

      ScalarBindingInfo outputBinding = gaugeAssemblyInfo.getScalarBindingInfo();
      dataOutputPaneModel.setTargetTree(
         this.vsOutputService.getOutputTablesTree(rvs, principal));
      dataOutputPaneModel.setLogicalModel(vs.getBaseEntry() != null &&
                                             vs.getBaseEntry().getType() == AssetEntry.Type.LOGIC_MODEL);
      dataOutputPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
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

      gaugeAdvancedPaneModel.setShowValue(gaugeAssemblyInfo.getLabelVisibleValue());

      rangePaneModel.setGradient(gaugeAssemblyInfo.isRangeGradientValue());
      rangePaneModel.setTargetValue(gaugeAssemblyInfo.getTargetValue());
      String[] rangeValues = gaugeAssemblyInfo.getRangeValues();
      String[] rangePaneValues = rangePaneModel.getRangeValues();

      for(int i = 0; i < rangePaneValues.length; i++) {
         if(rangeValues != null && i < rangeValues.length && rangeValues[i] != null) {
            rangePaneValues[i] = rangeValues[i];
         }
         else {
            rangePaneValues[i] = "";
         }
      }

      Color[] rangeColorValues = gaugeAssemblyInfo.getRangeColorsValue();
      String[] rangePaneColorValues = rangePaneModel.getRangeColorValues();

      for(int i = 0; i < rangePaneColorValues.length; i++) {
         if(rangeColorValues != null && i < rangeColorValues.length &&
            rangeColorValues[i] != null)
         {
            rangePaneColorValues[i] = "#" +
               Integer.toHexString(rangeColorValues[i].getRGB()).substring(2);
         }
         else {
            rangePaneColorValues[i] = null;
         }
      }

      vsAssemblyScriptPaneModel.scriptEnabled(gaugeAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(gaugeAssemblyInfo.getScript() == null ?
                                              "" : gaugeAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setGaugePropertyDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                           GaugePropertyDialogModel value, String linkUri,
                                           Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      GaugeVSAssemblyInfo gaugeAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         GaugeVSAssembly gaugeAssembly = (GaugeVSAssembly) vs.getAssembly(objectId);
         gaugeAssemblyInfo = (GaugeVSAssemblyInfo) Tool.clone(gaugeAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      GaugeGeneralPaneModel gaugeGeneralPaneModel = value.getGaugeGeneralPaneModel();
      OutputGeneralPaneModel outputGeneralPaneModel =
         gaugeGeneralPaneModel.getOutputGeneralPaneModel();
      GeneralPropPaneModel generalPropPaneModel = outputGeneralPaneModel.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      NumberRangePaneModel numberRangePaneModel = gaugeGeneralPaneModel.getNumberRangePaneModel();
      FacePaneModel facePaneModel = gaugeGeneralPaneModel.getFacePaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         gaugeGeneralPaneModel.getSizePositionPaneModel();
      DataOutputPaneModel dataOutputPaneModel = value.getDataOutputPaneModel();
      GaugeAdvancedPaneModel gaugeAdvancedPaneModel = value.getGaugeAdvancedPaneModel();
      RangePaneModel rangePaneModel = gaugeAdvancedPaneModel.getRangePaneModel();
      PaddingPaneModel paddingPaneModel = gaugeGeneralPaneModel.getPaddingPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();
      TipPaneModel tipPaneModel = gaugeGeneralPaneModel.getTipPaneModel();
      TipCustomizeDialogModel tipCustomizeDialogModel = tipPaneModel.getTipCustomizeDialogModel();

      gaugeAssemblyInfo.setEnabledValue(generalPropPaneModel.getEnabled());

      gaugeAssemblyInfo.setVisibleValue(basicGeneralPaneModel.getVisible());
      gaugeAssemblyInfo.setPrimary(basicGeneralPaneModel.isPrimary());
      gaugeAssemblyInfo.setShadowValue(basicGeneralPaneModel.isShadow());

      gaugeAssemblyInfo.setMinValue(numberRangePaneModel.getMin());
      gaugeAssemblyInfo.setMaxValue(numberRangePaneModel.getMax());
      gaugeAssemblyInfo.setMinorIncValue(numberRangePaneModel.getMinorIncrement());
      gaugeAssemblyInfo.setMajorIncValue(numberRangePaneModel.getMajorIncrement());

      gaugeAssemblyInfo.setFace(facePaneModel.getFace());
      gaugeAssemblyInfo.setPadding(new Insets(paddingPaneModel.getTop(),
                                              paddingPaneModel.getLeft(),
                                              paddingPaneModel.getBottom(),
                                              paddingPaneModel.getRight()));

      gaugeAssemblyInfo.setTooltipVisible(tipPaneModel.isTipOption());

      if(tipCustomizeDialogModel.getCustomRB() == TipCustomizeDialogModel.TipFormat.CUSTOM) {
         String customTip = tipCustomizeDialogModel.getCustomTip();
         customTip = (customTip == null || customTip.isEmpty()) ? null : customTip;
         gaugeAssemblyInfo.setCustomTooltipString(customTip);
      }
      else {
         gaugeAssemblyInfo.setCustomTooltipString(null);
      }

      dialogService.setAssemblySize(gaugeAssemblyInfo, sizePositionPaneModel);
      dialogService.setAssemblyPosition(gaugeAssemblyInfo, sizePositionPaneModel);

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

      gaugeAssemblyInfo.setScalarBindingInfo(info);
      gaugeAssemblyInfo.setLabelVisibleValue(gaugeAdvancedPaneModel.isShowValue());
      List<Color> rangeColorValues = new ArrayList<>();

      for(String rangePaneColorValue : rangePaneModel.getRangeColorValues()) {
         if(rangePaneColorValue != null && !rangePaneColorValue.isEmpty()) {
            rangeColorValues.add(Color.decode(rangePaneColorValue));
         }
         else {
            rangeColorValues.add(null);
         }
      }

      gaugeAssemblyInfo.setRangeColorsValue(rangeColorValues.toArray(new Color[0]));
      gaugeAssemblyInfo.setRangeValues(rangePaneModel.getRangeValues());
      gaugeAssemblyInfo.setRangeGradientValue(rangePaneModel.isGradient());
      gaugeAssemblyInfo.setTargetValue(rangePaneModel.getTargetValue());

      gaugeAssemblyInfo.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      gaugeAssemblyInfo.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         rvs, gaugeAssemblyInfo, objectId, basicGeneralPaneModel.getName(), linkUri, principal,
         commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, GaugePropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      GaugeVSAssembly gaugeVSAssembly =
         (GaugeVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(gaugeVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(gaugeVSAssembly.getVSAssemblyInfo());
      GaugeVSAssemblyInfo newAssemblyInfo =
         (GaugeVSAssemblyInfo) Tool.clone(gaugeVSAssembly.getVSAssemblyInfo());
      setOutputValues(newAssemblyInfo, model, rvs, principal);

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   private void setOutputValues(GaugeVSAssemblyInfo gaugeAssemblyInfo,
                                GaugePropertyDialogModel model,
                                RuntimeViewsheet rvs,
                                Principal principal)
      throws Exception
   {
      DataOutputPaneModel dataOutputPaneModel = model.getDataOutputPaneModel();
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

      gaugeAssemblyInfo.setScalarBindingInfo(info);
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final VSDialogService dialogService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService trapService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
