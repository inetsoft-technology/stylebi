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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class RangeSliderPropertyDialogService {

   public RangeSliderPropertyDialogService(VSObjectPropertyService vsObjectPropertyService,
                                           VSOutputService vsOutputService,
                                           ViewsheetService viewsheetService,
                                           VSDialogService dialogService,
                                           VSTrapService trapService,
                                           SelectionDialogService selectionDialogService,
                                           VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.vsOutputService = vsOutputService;
      this.viewsheetService = viewsheetService;
      this.dialogService = dialogService;
      this.trapService = trapService;
      this.selectionDialogService = selectionDialogService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public RangeSliderPropertyDialogModel getRangeSliderPropertyModel(@ClusterProxyKey String runtimeId,
                                                                     String objectId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      TimeSliderVSAssembly timeSliderAssembly;
      TimeSliderVSAssemblyInfo timeSliderAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         timeSliderAssembly = (TimeSliderVSAssembly) vs.getAssembly(objectId);
         timeSliderAssemblyInfo = (TimeSliderVSAssemblyInfo) timeSliderAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         throw e;
      }

      RangeSliderPropertyDialogModel result = new RangeSliderPropertyDialogModel();
      RangeSliderGeneralPaneModel rangeSliderGeneralPane =
         result.getRangeSliderGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel =
         rangeSliderGeneralPane.getTitlePropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         rangeSliderGeneralPane.getSizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel =
         rangeSliderGeneralPane.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel =
         generalPropPaneModel.getBasicGeneralPaneModel();
      RangeSliderDataPaneModel rangeSliderDataPaneModel =
         result.getRangeSliderDataPaneModel();
      RangeSliderAdvancedPaneModel rangeSliderAdvancedPaneModel =
         result.getRangeSliderAdvancedPaneModel();
      RangeSliderSizePaneModel rangeSliderSizePaneModel =
         rangeSliderAdvancedPaneModel.getRangeSliderSizePaneModel();
      SliderLabelPaneModel sliderLabelPaneModel =
         rangeSliderAdvancedPaneModel.getSliderLabelPaneModel();
      VSAssemblyScriptPaneModel.Builder vsAssemblyScriptPaneModel =
         VSAssemblyScriptPaneModel.builder();

      boolean inSelectionContainer =
         timeSliderAssembly.getContainer() instanceof CurrentSelectionVSAssembly;
      rangeSliderGeneralPane.setInSelectionContainer(inSelectionContainer);
      titlePropPaneModel.setVisible(timeSliderAssemblyInfo.getTitleVisibleValue());
      titlePropPaneModel.setTitle(timeSliderAssemblyInfo.getTitleValue());

      Point pos = dialogService.getAssemblyPosition(timeSliderAssemblyInfo, vs);
      Dimension size = dialogService.getAssemblySize(timeSliderAssemblyInfo, vs);

      if(timeSliderAssembly.getContainer() != null) {
         sizePositionPaneModel.setTitleHeight(timeSliderAssemblyInfo.getTitleHeightValue());
         sizePositionPaneModel.setContainer(true);
      }

      sizePositionPaneModel.setPositions(pos, size);
      generalPropPaneModel.setShowEnabledGroup(true);
      generalPropPaneModel.setEnabled(timeSliderAssemblyInfo.getEnabledValue());

      basicGeneralPaneModel.setName(timeSliderAssemblyInfo.getAbsoluteName());
      basicGeneralPaneModel.setPrimary(timeSliderAssemblyInfo.isPrimary());
      basicGeneralPaneModel.setVisible(timeSliderAssemblyInfo.getVisibleValue());
      basicGeneralPaneModel.setObjectNames(this.vsObjectPropertyService.getObjectNames(
         vs, timeSliderAssemblyInfo.getAbsoluteName()));

      rangeSliderDataPaneModel.setComposite(timeSliderAssemblyInfo.isComposite());
      rangeSliderDataPaneModel.setGrayedOutFields(assemblyInfoHandler.getGrayedOutFields(rvs));
      final String selectedTableName = timeSliderAssemblyInfo.getFirstTableName();
      rangeSliderDataPaneModel.setSelectedTable(selectedTableName);
      rangeSliderDataPaneModel.setAssemblySource(
         timeSliderAssemblyInfo.getSourceType() == XSourceInfo.VS_ASSEMBLY);
      rangeSliderDataPaneModel.setAdditionalTables(
         timeSliderAssemblyInfo.getAdditionalTableNames());
      rangeSliderDataPaneModel.setTargetTree(
         this.vsOutputService.getRangeSliderTablesTree(rvs, principal, false));
      final TreeNodeModel compositeTree =
         this.vsOutputService.getRangeSliderTablesTree(rvs, principal, true);
      rangeSliderDataPaneModel.setCompositeTargetTree(compositeTree);
      TimeInfo timeInfo = timeSliderAssemblyInfo.getTimeInfo();

      rangeSliderSizePaneModel.setLength(timeInfo.getLengthValue());
      rangeSliderSizePaneModel.setLogScale(timeSliderAssemblyInfo.getLogScaleValue());
      rangeSliderSizePaneModel.setUpperInclusive(
         timeSliderAssemblyInfo.getUpperInclusiveValue());
      rangeSliderSizePaneModel.setSubmitOnChange(timeSliderAssemblyInfo.getSubmitOnChangeValue());

      if(timeInfo instanceof SingleTimeInfo) {
         SingleTimeInfo singleTimeInfo = (SingleTimeInfo) timeInfo;
         rangeSliderSizePaneModel.setRangeType(singleTimeInfo.getRangeTypeValue());
         rangeSliderSizePaneModel.setRangeSize(singleTimeInfo.getRangeSizeValue());
         rangeSliderSizePaneModel.setMaxRangeSize(singleTimeInfo.getMaxRangeSizeValue());
         DataRef dataRef = singleTimeInfo.getDataRef();

         if(dataRef != null) {
            OutputColumnRefModel columnRefModel = new OutputColumnRefModel();
            columnRefModel.setEntity(dataRef.getEntity());
            columnRefModel.setAttribute(dataRef.getAttribute());

            if(dataRef instanceof ColumnRef) {
               columnRefModel.setCaption(((ColumnRef) dataRef).getCaption());
            }

            columnRefModel.setDataType(dataRef.getDataType());
            columnRefModel.setRefType(dataRef.getRefType());
            columnRefModel.getProperties()
               .put("type", String.valueOf(timeSliderAssemblyInfo.getSourceType()));

            if((dataRef.getRefType() & AbstractDataRef.CUBE_DIMENSION) == AbstractDataRef.CUBE_DIMENSION ||
               XSchema.isNumericType(dataRef.getDataType()) || XSchema.isDateType(dataRef.getDataType()))
            {
               columnRefModel.setTable(selectedTableName);
            }

            rangeSliderDataPaneModel.setSelectedColumns(
               new OutputColumnRefModel[] {columnRefModel});
         }
      }
      else if(timeInfo instanceof CompositeTimeInfo) {
         DataRef[] dataRefs = ((CompositeTimeInfo) timeInfo).getDataRefs();

         if(dataRefs != null && dataRefs.length > 0) {
            List<OutputColumnRefModel> columnRefModels = new ArrayList<>();

            for(DataRef dataRef : dataRefs) {
               final Optional<OutputColumnRefModel> columnRefModel =
                  selectionDialogService.findSelectedOutputColumnRefModel(
                     compositeTree, selectedTableName, (ColumnRef) dataRef);

               columnRefModel.ifPresent(columnRefModels::add);
            }

            rangeSliderDataPaneModel.setSelectedColumns(
               columnRefModels.toArray(new OutputColumnRefModel[0]));
         }
      }

      sliderLabelPaneModel.setTick(timeSliderAssemblyInfo.getTickVisibleValue());
      sliderLabelPaneModel.setCurrentValue(timeSliderAssemblyInfo.getCurrentVisibleValue());
      sliderLabelPaneModel.setMinimum(timeSliderAssemblyInfo.getMinVisibleValue());
      sliderLabelPaneModel.setMaximum(timeSliderAssemblyInfo.getMaxVisibleValue());

      vsAssemblyScriptPaneModel.scriptEnabled(timeSliderAssemblyInfo.isScriptEnabled());
      vsAssemblyScriptPaneModel.expression(timeSliderAssemblyInfo.getScript() == null ?
                                              "" : timeSliderAssemblyInfo.getScript());
      result.setVsAssemblyScriptPaneModel(vsAssemblyScriptPaneModel.build());

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setRangeSliderPropertyModel(@ClusterProxyKey String runtimeId, String objectId,
                                           RangeSliderPropertyDialogModel value, String linkUri,
                                           Principal principal, CommandDispatcher commandDispatcher)
      throws Exception
   {
      RuntimeViewsheet viewsheet;
      TimeSliderVSAssemblyInfo info;

      try {
         viewsheet = viewsheetService.getViewsheet(runtimeId, principal);
         TimeSliderVSAssembly timeSliderAssembly = (TimeSliderVSAssembly)
            viewsheet.getViewsheet().getAssembly(objectId);
         info = (TimeSliderVSAssemblyInfo)
            Tool.clone(timeSliderAssembly.getVSAssemblyInfo());
      }
      catch(Exception e) {
         throw e;
      }

      RangeSliderGeneralPaneModel rangeSliderGeneralPane = value.getRangeSliderGeneralPaneModel();
      TitlePropPaneModel titlePropPaneModel = rangeSliderGeneralPane.getTitlePropPaneModel();
      SizePositionPaneModel sizePositionPaneModel =
         rangeSliderGeneralPane.getSizePositionPaneModel();
      GeneralPropPaneModel generalPropPaneModel = rangeSliderGeneralPane.getGeneralPropPaneModel();
      BasicGeneralPaneModel basicGeneralPaneModel = generalPropPaneModel.getBasicGeneralPaneModel();
      RangeSliderDataPaneModel rangeSliderDataPaneModel = value.getRangeSliderDataPaneModel();
      RangeSliderAdvancedPaneModel rangeSliderAdvancedPaneModel =
         value.getRangeSliderAdvancedPaneModel();
      RangeSliderSizePaneModel rangeSliderSizePaneModel =
         rangeSliderAdvancedPaneModel.getRangeSliderSizePaneModel();
      SliderLabelPaneModel sliderLabelPaneModel =
         rangeSliderAdvancedPaneModel.getSliderLabelPaneModel();
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel = value.getVsAssemblyScriptPaneModel();

      info.setTitleVisibleValue(titlePropPaneModel.isVisible());
      info.setTitleValue(titlePropPaneModel.getTitle());

      dialogService.setAssemblySize(info, sizePositionPaneModel);
      dialogService.setAssemblyPosition(info, sizePositionPaneModel);

      if(sizePositionPaneModel.getTitleHeight() > 0) {
         info.setTitleHeightValue(sizePositionPaneModel.getTitleHeight());
      }

      info.setEnabledValue(generalPropPaneModel.getEnabled());

      info.setPrimary(basicGeneralPaneModel.isPrimary());
      info.setVisibleValue(basicGeneralPaneModel.getVisible());

      info.setComposite(rangeSliderDataPaneModel.isComposite());
      info.setFirstTableName(VSUtil.getTableName(rangeSliderDataPaneModel.getSelectedTable()));
      info.setAdditionalTableNames(rangeSliderDataPaneModel.getAdditionalTables().stream()
                                      .map(VSUtil::getTableName)
                                      .collect(Collectors.toList()));

      setTimeInfo(info, rangeSliderDataPaneModel, rangeSliderSizePaneModel);

      TimeInfo timeInfo = info.getTimeInfo();
      timeInfo.setLengthValue(rangeSliderSizePaneModel.getLength());
      info.setLogScaleValue(rangeSliderSizePaneModel.isLogScale());
      info.setUpperInclusiveValue(rangeSliderSizePaneModel.isUpperInclusive());

      info.setTickVisibleValue(sliderLabelPaneModel.isTick());
      info.setCurrentVisibleValue(sliderLabelPaneModel.isCurrentValue());
      info.setMinVisibleValue(sliderLabelPaneModel.isMinimum());
      info.setMaxVisibleValue(sliderLabelPaneModel.isMaximum());

      info.setScriptEnabled(vsAssemblyScriptPaneModel.scriptEnabled());
      info.setScript(vsAssemblyScriptPaneModel.expression());

      this.vsObjectPropertyService.editObjectProperty(
         viewsheet, info, objectId, basicGeneralPaneModel.getName(), linkUri,
         principal, commandDispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkTrap(@ClusterProxyKey String runtimeId, RangeSliderPropertyDialogModel model,
                                     String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      TimeSliderVSAssembly timeSliderVSAssembly =
         (TimeSliderVSAssembly) rvs.getViewsheet().getAssembly(objectId);

      if(timeSliderVSAssembly == null) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      VSAssemblyInfo oldAssemblyInfo =
         (VSAssemblyInfo) Tool.clone(timeSliderVSAssembly.getVSAssemblyInfo());
      TimeSliderVSAssemblyInfo newAssemblyInfo =
         (TimeSliderVSAssemblyInfo) Tool.clone(timeSliderVSAssembly.getVSAssemblyInfo());
      RangeSliderDataPaneModel rangeSliderDataPaneModel = model.getRangeSliderDataPaneModel();
      RangeSliderSizePaneModel rangeSliderSizePaneModel = model.getRangeSliderAdvancedPaneModel()
         .getRangeSliderSizePaneModel();

      setTimeInfo(newAssemblyInfo, rangeSliderDataPaneModel, rangeSliderSizePaneModel);

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   private void setTimeInfo(TimeSliderVSAssemblyInfo assemblyInfo,
                            RangeSliderDataPaneModel rangeSliderDataPaneModel,
                            RangeSliderSizePaneModel rangeSliderSizePaneModel)
   {
      TimeInfo oldTimeInfo =
         assemblyInfo.getTimeInfo() != null ? (TimeInfo) assemblyInfo.getTimeInfo().clone() : null;
      OutputColumnRefModel[] columns = rangeSliderDataPaneModel.getSelectedColumns();

      if(rangeSliderDataPaneModel.isComposite()) {
         CompositeTimeInfo compositeTimeInfo = new CompositeTimeInfo();
         List<ColumnRef> columnRefs = new ArrayList<>();

         if(columns != null && columns.length > 0) {
            for(OutputColumnRefModel outputColumnRef : columns) {
               AttributeRef aRef = new AttributeRef(outputColumnRef.getEntity(),
                                                    outputColumnRef.getAttribute());
               aRef.setRefType(outputColumnRef.getRefType());
               aRef.setCaption(outputColumnRef.getCaption());
               ColumnRef cRef = new ColumnRef(aRef);
               cRef.setDataType(outputColumnRef.getDataType());
               columnRefs.add(cRef);
               String columnType = outputColumnRef.getProperties().get("type");

               if(columnType != null) {
                  int itype = Integer.parseInt(columnType);
                  assemblyInfo.setSourceType(itype);
               }
               else {
                  assemblyInfo.setSourceType(SourceInfo.ASSET);
               }
            }

            compositeTimeInfo.setDataRefs(columnRefs.toArray(new ColumnRef[0]));
         }
         else {
            compositeTimeInfo.setDataRefs(null);
         }

         assemblyInfo.setTimeInfo(compositeTimeInfo);
      }
      else {
         SingleTimeInfo singleTimeInfo = new SingleTimeInfo();

         if(columns != null && columns.length > 0) {
            OutputColumnRefModel outputColumnRef = columns[0];
            AttributeRef aRef = new AttributeRef(outputColumnRef.getEntity(),
                                                 outputColumnRef.getAttribute());
            aRef.setRefType(outputColumnRef.getRefType());
            aRef.setCaption(outputColumnRef.getCaption());
            ColumnRef cRef = new ColumnRef(aRef);
            cRef.setDataType(outputColumnRef.getDataType());
            singleTimeInfo.setDataRef(cRef);
            String columnType = outputColumnRef.getProperties().get("type");

            if(columnType != null) {
               int itype = Integer.parseInt(columnType);
               assemblyInfo.setSourceType(itype);
            }
            else {
               assemblyInfo.setSourceType(SourceInfo.ASSET);
            }
         }
         else {
            singleTimeInfo.setDataRef(null);
         }

         singleTimeInfo.setRangeTypeValue(rangeSliderSizePaneModel.getRangeType());

         if(rangeSliderSizePaneModel.getRangeType() == TimeInfo.NUMBER) {
            if(!rangeSliderSizePaneModel.isLogScale()) {
               singleTimeInfo.setRangeSizeValue(rangeSliderSizePaneModel.getRangeSize());
               singleTimeInfo.setMaxRangeSizeValue(rangeSliderSizePaneModel.getMaxRangeSize());
            }
         }
         else {
            singleTimeInfo.setMaxRangeSizeValue(rangeSliderSizePaneModel.getMaxRangeSize());
         }

         assemblyInfo.setTimeInfo(singleTimeInfo);
      }

      assemblyInfo.setSubmitOnChangeValue(rangeSliderSizePaneModel.isSubmitOnChange());

      if(!Tool.equals(oldTimeInfo, assemblyInfo.getTimeInfo())) {
         assemblyInfo.setTimeSliderSelection(new TimeSliderSelection());
      }
   }

   private final VSObjectPropertyService vsObjectPropertyService;
   private final VSOutputService vsOutputService;
   private final ViewsheetService viewsheetService;
   private final VSDialogService dialogService;
   private final VSTrapService trapService;
   private final SelectionDialogService selectionDialogService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
