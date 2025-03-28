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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.graph.data.BoxDataSet;
import inetsoft.graph.data.SumDataSet;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.service.HighlightService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class HighlightDialogService {

   public HighlightDialogService(DataRefModelFactoryService dataRefModelFactoryService,
                                 HighlightService highlightService,
                                 VSObjectPropertyService vsObjectPropertyService,
                                 ViewsheetService viewsheetService,
                                 VSTrapService vsTrapService)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.highlightService = highlightService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.viewsheetService = viewsheetService;
      this.vsTrapService = vsTrapService;
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public HighlightDialogModel getHighlightDialogModel(@ClusterProxyKey String runtimeId, String objectId, Integer row, Integer col, String colName, boolean isAxis, boolean isText, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(objectId);
      VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo();
      HighlightDialogModel model = new HighlightDialogModel();
      DataRefModel[] fields = null;
      String tableName = null;
      VSTableLens lens = null;
      TableDataPath dataPath = null;

      if(assemblyInfo instanceof TableDataVSAssemblyInfo) {
         lens = box.getVSTableLens(objectId, false);
         dataPath = lens.getTableDataPath(row, col);

         if(assemblyInfo instanceof TableVSAssemblyInfo) {
            model.setTableAssembly(true);
            model.setShowRow(true);

            if(((TableVSAssemblyInfo) assemblyInfo).isForm()) {
               FormTableLens formLens = box.getFormTableLens(objectId);

               model.setConfirmChanges(formLens != null &&
                                          formLens.getRowCount() != lens.getRowHeights().length);
            }
         }
      }

      Worksheet ws = viewsheet.getBaseWorksheet();

      if(ws != null && assembly instanceof TableVSAssembly) {
         TableVSAssembly table = (TableVSAssembly) assembly;
         Assembly assembly0 = ws.getAssembly(table.getTableName() + "_O");

         if(assembly0 instanceof BoundTableAssembly) {
            model.setShowRow(!((BoundTableAssembly) assembly0).isCrosstab());
         }
      }

      if(assemblyInfo instanceof DataVSAssemblyInfo) {
         SourceInfo sourceInfo = ((DataVSAssemblyInfo) assemblyInfo).getSourceInfo();
         ColumnSelection cols = null;

         if(sourceInfo != null && sourceInfo.getSource() != null) {
            tableName = sourceInfo.getSource();
            model.setTableName(tableName);
         }

         if(ws != null && VSEventUtil.checkBaseWSPermission(
            viewsheet, principal, viewsheetService.getAssetRepository(), ResourceAction.READ))
         {
            List<DataRef> refs = this.highlightService.getRefsForVSAssembly(
               rvs, assembly, row == null ? 0 : row, col == null ? 0 : col,
               dataPath, colName, true);

            if(refs.size() > 0) {
               cols = new ColumnSelection();
               refs.forEach(cols::addAttribute);

               if(assemblyInfo instanceof ChartVSAssemblyInfo) {
                  ChartDescriptor desc = ((ChartVSAssemblyInfo) assemblyInfo).getChartDescriptor();
                  ChartInfo cinfo = ((ChartVSAssemblyInfo) assemblyInfo).getVSChartInfo();

                  if(isText) {
                     HighlightService.processStackMeasures(desc, cinfo, cols, colName);
                  }

                  if(colName != null && colName.startsWith(BoxDataSet.MAX_PREFIX)) {
                     HighlightService.processBoxplot(cinfo, cols);
                  }
               }

               if(assembly instanceof CalcTableVSAssembly) {
                  TableLens table = (TableLens) box.getData(assembly.getAbsoluteName());

                  if(table instanceof RuntimeCalcTableLens) {
                     List<String> nonSupportBrowseFields = this.highlightService.
                        getNamedGroupFields((RuntimeCalcTableLens) table, refs);
                     model.setNonsupportBrowseFields(nonSupportBrowseFields);
                  }
               }
            }

            if(cols != null) {
               fields = ConditionUtil
                  .getDataRefModelsFromColumnSelection(cols, dataRefModelFactoryService, 0);

               for(int i = 0; i < fields.length; i++) {
                  DataRefModel column = fields[i];

                  if(column instanceof ColumnRefModel) {
                     DataRefModel ref = ((ColumnRefModel) column).getDataRefModel();

                     if(ref instanceof DateRangeRefModel) {
                        String originalType = ((DateRangeRefModel) ref).getOriginalType();
                        int option = ((DateRangeRefModel) ref).getOption();

                        if(XSchema.TIME.equals(originalType) || XSchema.DATE.equals(originalType)) {
                           fields[i].setDataType(DateRangeRef.getDataType(option, originalType));
                        }
                        else {
                           fields[i].setDataType(ref.getDataType());
                        }
                     }
                  }
               }

               model.setFields(fields);
            }
         }
      }

      List<HighlightModel> highlightModelList = new ArrayList<>();

      if(assemblyInfo instanceof TableDataVSAssemblyInfo) {
         model.setRow(row);
         model.setCol(col);
         TableHighlightAttr tableHighlightAttr = ((TableDataVSAssemblyInfo) assemblyInfo)
            .getHighlightAttr();

         if(tableHighlightAttr != null) {
            HighlightGroup cells = tableHighlightAttr.getHighlight(dataPath);

            if(cells != null && !cells.isEmpty()) {
               for(String name : cells.getNames()) {
                  Highlight cellHighlight = cells.getHighlight(name);
                  HighlightModel highlightModel =
                     highlightService.convertHighlightToModel(cellHighlight);
                  VSConditionDialogModel vsConditionDialogModel = highlightModel
                     .getVsConditionDialogModel();
                  vsConditionDialogModel.setTableName(tableName);
                  vsConditionDialogModel.setFields(fields);
                  highlightModelList.add(highlightModel);
               }
            }

            if(assemblyInfo instanceof TableVSAssemblyInfo) {
               highlightService.getRowHighlight(tableHighlightAttr, highlightModelList, model,
                                                dataPath, tableName, fields);
            }
         }
      }
      else {
         HighlightGroup highlightGroup = null;

         if(assemblyInfo instanceof ChartVSAssemblyInfo) {
            model.setChartAssembly(true);
            model.setMeasure(colName);
            model.setAxis(isAxis);
            model.setText(isText);
            model.setShowFont(isText);
            VSChartInfo chartInfo = ((ChartVSAssemblyInfo) assemblyInfo).getVSChartInfo();
            HighlightRef highlightRef =
               (HighlightRef) getMeasure(chartInfo, colName, true, isAxis, isText);
            boolean wordCloud = GraphTypeUtil.isWordCloud(chartInfo);
            boolean boxplot = colName != null && colName.startsWith(BoxDataSet.MAX_PREFIX);
            boolean geo = GraphTypes.isGeo(chartInfo.getChartType());

            if(highlightRef instanceof VSChartDimensionRef ||
               highlightRef instanceof VSChartAggregateRef &&
                  ((VSChartAggregateRef) highlightRef).isDiscrete())
            {
               model.setShowFont(true);
            }

            if(isText && highlightRef != null) {
               highlightGroup = GraphUtil.getTextHighlightGroup(highlightRef, chartInfo);
            }
            else if(highlightRef == null ||
               (chartInfo instanceof MergedVSChartInfo &&
                  !GraphTypes.isRadarOne(chartInfo)) &&
                  !GraphTypes.isGantt(chartInfo.getChartType()) &&
                  (((ChartRef) highlightRef).isMeasure() || geo))
            {
               if(isText) {
                  highlightGroup = chartInfo.getTextHighlightGroup();
               }
               else {
                  highlightGroup = chartInfo.getHighlightGroup();
               }
            }
            else {
               highlightGroup = highlightRef.getHighlightGroup();
            }

            boolean refOnAxis = Arrays.stream(chartInfo.getRTAxisFields())
               .anyMatch(a -> a.equals(highlightRef));

            if(boxplot && model.getFields() != null) {
               // boxplot fields already set (with max/min/q75/q25/mid), don't override. (63113)
            }
            else if(!wordCloud && (!GraphTypes.isTreemap(chartInfo.getChartType()) &&
               !GraphTypes.isRelation(chartInfo.getChartType()) || refOnAxis))
            {
               if(highlightRef instanceof VSChartDimensionRef) {
                  DataRef hColumnRef = highlightService.getDimensionColumnRef(highlightRef);
                  highlightService.fixColumnDataType(hColumnRef);
                  List<DataRefModel> fieldList = new ArrayList<>();
                  fieldList.add(dataRefModelFactoryService.createDataRefModel(hColumnRef));

                  // if select chart axis, should only do highlight on axis columns, if select plot
                  // area, can do highlight not only the drill Members columns.
                  if(!isAxis && !GraphTypes.isRelation(chartInfo.getChartType())) {
                     // For a field with a drillMembers script which turns into multiple fields at
                     // runtime. Get all the matching field names
                     for(DataRef ref : chartInfo.getRTFields()) {
                        if(ref instanceof VSChartDimensionRef) {
                           VSChartDimensionRef dim = (VSChartDimensionRef) ref;

                           // Do not add the same dimension.
                           if(dim.equalsContent0(highlightRef, true)) {
                              continue;
                           }

                           if(listContainsCol(fieldList, ref)) {
                              continue;
                           }

                           if(!Tool.equals(((VSChartDimensionRef) highlightRef).getGroupColumnValue(),
                                           dim.getGroupColumnValue()) ||
                              !Tool.equals(((VSChartDimensionRef) highlightRef).getFullName(),
                                           dim.getFullName()))
                           {
                              fieldList.add(dataRefModelFactoryService.createDataRefModel(ref));
                           }
                        }
                        else if(ref instanceof VSChartAggregateRef) {
                           VSChartAggregateRef agg = (VSChartAggregateRef) ref;

                           // Do not add the same dimension.
                           if(agg.equals(highlightRef, true)) {
                              continue;
                           }

                           fieldList.add(dataRefModelFactoryService.createDataRefModel(ref));
                        }
                     }
                  }

                  fields = fieldList.toArray(new DataRefModel[fieldList.size()]);
                  model.setFields(fields);
               }
               else if(highlightRef instanceof VSChartAggregateRef &&
                  ((VSChartAggregateRef) highlightRef).isDiscrete())
               {
                  DataRefModel refModel =
                     dataRefModelFactoryService.createDataRefModel(highlightRef);
                  fields = new DataRefModel[]{ refModel };
                  model.setFields(fields);
               }
               else if(highlightRef instanceof VSChartAggregateRef && boxplot) {

               }
            }
         }
         else if(assemblyInfo instanceof OutputVSAssemblyInfo) {
            if(!(assemblyInfo instanceof TextVSAssemblyInfo)) {
               model.setImageObj(true);
            }

            ScalarBindingInfo bindingInfo = ((OutputVSAssemblyInfo) assemblyInfo)
               .getScalarBindingInfo();
            String dtype = bindingInfo != null ? bindingInfo
               .getColumnType() : XSchema.STRING;
            ColumnRef valueRef = new ColumnRef(new AttributeRef(null, "value"));
            valueRef.setDataType(dtype);
            fields = new DataRefModel[]{dataRefModelFactoryService.createDataRefModel(
               valueRef)};
            model.setFields(fields);
            highlightGroup = ((OutputVSAssemblyInfo) assemblyInfo).getHighlightGroup();
         }

         if(highlightGroup != null && !highlightGroup.isEmpty()) {
            String[] names = highlightGroup.getNames();

            for(String name : names) {
               Highlight highlight = highlightGroup.getHighlight(name);
               HighlightModel highlightModel = highlightService.convertHighlightToModel(highlight);
               VSConditionDialogModel vsConditionDialogModel = highlightModel
                  .getVsConditionDialogModel();
               vsConditionDialogModel.setFields(fields);
               vsConditionDialogModel.setTableName(tableName);
               highlightModelList.add(highlightModel);
            }
         }
      }

      model.setHighlights(highlightModelList.toArray(new HighlightModel[0]));
      return model;
   }

   private boolean listContainsCol(List<DataRefModel> list, DataRef ref) {
      for(int i = 0; i < list.size(); i++) {
         DataRefModel refModel = list.get(i);

         if(refModel instanceof BDimensionRefModel && ref instanceof VSChartDimensionRef) {
            VSChartDimensionRef dim = (VSChartDimensionRef) ref;
            BDimensionRefModel bdim = (BDimensionRefModel) refModel;

            if(Tool.equals(bdim.getName(), dim.getName()) &&
               Tool.equals(bdim.getFullName(), dim.getFullName()))
            {
               return true;
            }
         }
         else if(refModel instanceof BAggregateRefModel && ref instanceof VSChartAggregateRef) {
            VSChartAggregateRef agg = (VSChartAggregateRef) ref;
            BAggregateRefModel bagg = (BAggregateRefModel) refModel;

            if(Tool.equals(bagg.getName(), agg.getName()) &&
               Tool.equals(bagg.getFullName(), agg.getFullName()))
            {
               return true;
            }
         }
      }

      return false;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setHighlightDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                       HighlightDialogModel model, String linkUri, Principal principal,
                                       CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(objectId);
      VSAssemblyInfo oldAssemblyInfo = assembly.getVSAssemblyInfo();
      VSAssemblyInfo assemblyInfo = updateHighlights(model, oldAssemblyInfo, box, principal);

      this.vsObjectPropertyService.editObjectProperty(
         rvs, assemblyInfo, objectId, objectId, linkUri, principal, dispatcher);

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTableTrap(@ClusterProxyKey String runtimeId, HighlightDialogModel model,
                                            String objectId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(objectId);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(!(assembly instanceof TableVSAssembly)) {
         return VSTableTrapModel.builder()
            .showTrap(false)
            .build();
      }

      TableVSAssemblyInfo oinfo = (TableVSAssemblyInfo) assembly.getInfo().clone();
      VSAssemblyInfo ninfo = updateHighlights(model, oinfo, box, principal);
      assembly.setVSAssemblyInfo(ninfo);

      VSTableTrapModel result = vsTrapService.checkTrap(rvs, oinfo, ninfo);
      assembly.setVSAssemblyInfo(oinfo);

      return result;
   }

   private VSAssemblyInfo updateHighlights(HighlightDialogModel model,
                                           VSAssemblyInfo oldAssemblyInfo,
                                           ViewsheetSandbox box, Principal principal)
      throws Exception
   {
      VSAssemblyInfo assemblyInfo = (VSAssemblyInfo) Tool.clone(oldAssemblyInfo);
      HighlightModel[] highlights = model.getHighlights();

      if(assemblyInfo instanceof TableDataVSAssemblyInfo) {
         VSTableLens lens = box.getVSTableLens(oldAssemblyInfo.getAbsoluteName(), false);
         TableDataPath dataPath = lens.getTableDataPath(model.getRow(), model.getCol());
         TableHighlightAttr tableHighlightAttr = ((TableDataVSAssemblyInfo) assemblyInfo)
            .getHighlightAttr();

         if(tableHighlightAttr == null) {
            tableHighlightAttr = new TableHighlightAttr();
            ((TableDataVSAssemblyInfo) assemblyInfo).setHighlightAttr(tableHighlightAttr);
         }

         HighlightGroup cells = tableHighlightAttr.getHighlight(dataPath);
         cells = cells == null ? new HighlightGroup() : cells;
         cells.removeHighlights(HighlightGroup.DEFAULT_LEVEL);

         for(HighlightModel highlightModel : highlights) {
            if(!highlightModel.isApplyRow()) {
               cells.addHighlight(highlightModel.getName(),
                                  highlightService.convertModelToHighlight(highlightModel, principal));
            }
         }

         tableHighlightAttr.setHighlight(dataPath, cells);

         if(assemblyInfo instanceof TableVSAssemblyInfo) {
            highlightService.applyToRowHighlight(tableHighlightAttr,
                                                 dataPath, highlights, principal);
         }
      }
      else if(assemblyInfo instanceof ChartVSAssemblyInfo) {
         VSChartInfo chartInfo = ((ChartVSAssemblyInfo) assemblyInfo).getVSChartInfo();
         HighlightGroup highlightGroup = null;
         HighlightRef highlightRef =
            (HighlightRef) getMeasure(chartInfo, model.getMeasure(), false,
                                      model.isAxis(), model.isText());
         boolean geo = GraphTypes.isGeo(chartInfo.getChartType());
         // true to use highlight in chartInfo.
         boolean infoHL = highlightRef == null || (chartInfo instanceof MergedVSChartInfo &&
            !GraphTypes.isRadarOne(chartInfo) &&
            !GraphTypes.isGantt(chartInfo.getChartType())) &&
            (((ChartRef) highlightRef).isMeasure() || geo);

         if(model.isText() && highlightRef != null) {
            highlightGroup = GraphUtil.getTextHighlightGroup(highlightRef, chartInfo);
         }
         else if(infoHL) {
            highlightGroup = model.isText() ? chartInfo.getTextHighlightGroup()
               : chartInfo.getHighlightGroup();
         }
         else {
            highlightGroup = highlightRef.getHighlightGroup();
         }

         highlightGroup = highlightGroup == null ? new HighlightGroup() : highlightGroup;
         highlightGroup.removeHighlights(HighlightGroup.DEFAULT_LEVEL);

         for(HighlightModel highlightModel : highlights) {
            highlightGroup.addHighlight(highlightModel.getName(),
                                        highlightService.convertModelToHighlight(highlightModel, principal));
         }

         if(model.isText() && highlightRef != null) {
            GraphUtil.setTextHighlightGroup(highlightRef, chartInfo, highlightGroup);
         }
         else if(infoHL) {
            if(model.isText()) {
               chartInfo.setTextHighlightGroup(highlightGroup);
            }
            else {
               chartInfo.setHighlightGroup(highlightGroup);
            }
         }
         else {
            highlightRef.setHighlightGroup(highlightGroup);
         }

         chartInfo.clearRuntime();
      }
      else if(assemblyInfo instanceof OutputVSAssemblyInfo) {
         HighlightGroup highlightGroup = ((OutputVSAssemblyInfo) assemblyInfo)
            .getHighlightGroup();
         highlightGroup = highlightGroup == null ? new HighlightGroup() : highlightGroup;
         highlightGroup.removeHighlights(HighlightGroup.DEFAULT_LEVEL);

         for(HighlightModel highlightModel : highlights) {
            highlightGroup.addHighlight(highlightModel.getName(),
                                        highlightService.convertModelToHighlight(highlightModel, principal));
         }

         ((OutputVSAssemblyInfo) assemblyInfo).setHighlightGroup(highlightGroup);
      }

      return assemblyInfo;
   }

   private ChartRef getMeasure(ChartInfo chartInfo, String colName, boolean getPeriodRef,
                               boolean isAxis, boolean isText)
   {
      if(colName == null || GraphTypeUtil.isScatterMatrix(chartInfo)) {
         return null;
      }

      if(colName.startsWith(SumDataSet.ALL_HEADER_PREFIX)) {
         colName = colName.substring(SumDataSet.ALL_HEADER_PREFIX.length());
      }

      colName = BoxDataSet.getBaseName(colName);
      ChartRef ref = null;

      if(chartInfo instanceof VSChartInfo) {
         ChartRef[] refs = ((VSChartInfo) chartInfo).getRuntimeDateComparisonRefs();
         final String column = colName;

         ref = Arrays.stream(refs)
            .filter(chartRef -> chartRef.getFullName().equals(column))
            .findAny()
            .orElse(null);
      }

      // relation highlights defined on source/target instead of textfield, which is
      // shared by source and target.
      if(GraphTypeUtil.isWordCloud(chartInfo) || isText) {
         AestheticRef aref = null;
         boolean noTextBinding = chartInfo instanceof RelationChartInfo &&
            Tool.equals(ref, ((RelationChartInfo) chartInfo).getSourceField());

         if(!noTextBinding) {
            aref = chartInfo.isMultiAesthetic() && ref instanceof ChartBindable
               ? ((ChartBindable) ref).getTextField() : chartInfo.getTextField();
         }

         if(aref != null && (aref.getFullName().equals(colName) || isText)) {
            ref = (ChartRef) aref.getDataRef();
         }
      }

      final String finalColName = colName;

      if(GraphTypes.isTreemap(chartInfo.getRTChartType()) && !isAxis) {
         ChartRef[] groups = chartInfo.getGroupFields();

         if(groups != null) {
            ref = Arrays.stream(groups)
               .filter(f -> f.getFullName().equals(finalColName))
               .findFirst().orElse(null);
         }
      }

      if(ref == null && GraphTypes.isRelation(chartInfo.getRTChartType()) && !isAxis) {
         ChartRef field = ((RelationChartInfo) chartInfo).getTargetField();

         if(isSameRef(field, colName)) {
            ref = field;
         }

         field = ((RelationChartInfo) chartInfo).getSourceField();

         if(ref == null && isSameRef(field, colName)) {
            ref = field;
         }
      }

      if(ref == null && GraphTypes.isGantt(chartInfo.getChartType()) && !isAxis) {
         ChartRef field = ((GanttChartInfo) chartInfo).getStartField();

         if(isSameRef(field, colName)) {
            ref = field;
         }

         field = ((GanttChartInfo) chartInfo).getEndField();

         if(ref == null && isSameRef(field, colName)) {
            ref = field;
         }

         field = ((GanttChartInfo) chartInfo).getMilestoneField();

         if(ref == null && isSameRef(field, colName)) {
            ref = field;
         }
      }

      if(ref == null) {
         // make sure it's axis and not a node. (56974)
         if(isAxis) {
            List<VSDataRef> fields = new ArrayList<>(Arrays.asList(chartInfo.getXFields()));
            fields.addAll(Arrays.asList(chartInfo.getYFields()));
            ref = (ChartRef) fields.stream()
               .filter(f -> f.getFullName().equals(finalColName))
               .findFirst().orElse(null);
         }
         else {
            ref = chartInfo.getFieldByName(colName, false);
         }
      }

      if(chartInfo instanceof VSChartInfo && ref == null) {
         boolean periodPartRef = ((VSChartInfo) chartInfo).isPeriodPartRef(colName);
         ChartRef periodRef = ((VSChartInfo) chartInfo).getPeriodField();

         if(periodPartRef) {
            // for period part ref load the runtime field and set for design field.
            if(getPeriodRef) {
               ref = chartInfo.getFieldByName(colName, true);
            }
            else if(periodRef != null) {
               ChartRef periodField = (ChartRef) periodRef.clone();

               if(periodField instanceof VSDimensionRef) {
                  ((VSDimensionRef) periodField).setDates(null);
               }

               ref = chartInfo.getFieldByName(periodField.getFullName(), false);
            }
         }

         if(ref == null && ((VSChartInfo) chartInfo).isPeriodRef(colName)) {
            ref = periodRef;
         }
      }

      if(ref == null) {
         // get the runtime field first and then find the design time ref based
         // on this runtime field
         ref = chartInfo.getFieldByName(colName, true);

         if(ref instanceof VSChartDimensionRef) {
            VSChartDimensionRef rtDim = (VSChartDimensionRef) ref;
            VSDataRef[] fields = chartInfo.getFields();

            if(GraphTypes.isRadarOne(chartInfo)) {
               ChartRef groupField = chartInfo.getGroupField(0);

               // ensure priority is given to matching group fields rather than aesthetic fields.
               if(groupField instanceof VSChartDimensionRef) {
                  VSDataRef[] arr = new VSDataRef[fields.length + 1];
                  arr[0] = groupField;
                  System.arraycopy(fields, 0, arr, 1, fields.length);
                  fields = arr;
               }
            }

            for(VSDataRef vsDataRef : fields) {
               if(vsDataRef instanceof VSChartDimensionRef) {
                  VSChartDimensionRef dim = (VSChartDimensionRef) vsDataRef;

                  if(Tool.equals(dim.getGroupColumnValue(), rtDim.getGroupColumnValue())) {
                     // in case of dynamic field, multiple dimensions (e.g. state, city) may be
                     // generated from the design time ref, we set the base ref back to
                     // the runtime ref. (61582)
                     dim.setDataRef(rtDim.getDataRef());
                     ref = (ChartRef) vsDataRef;
                     break;
                  }
               }
            }
         }
      }

      return ref;
   }

   private boolean isSameRef(ChartRef ref, String colName) {
      return ref != null && Tool.equals(ref.getFullName(), colName);
   }

   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final HighlightService highlightService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private final ViewsheetService viewsheetService;
   private final VSTrapService vsTrapService;
}
