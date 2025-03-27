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
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.SimpleNamedGroupInfo;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapModel;
import inetsoft.web.composer.ws.assembly.ConditionTrapValidator;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class VSConditionDialogService {

   public VSConditionDialogService(
      DataRefModelFactoryService dataRefModelFactoryService,
      VSAssemblyInfoHandler vsAssemblyInfoHandler,
      ViewsheetService viewsheetService)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.vsAssemblyInfoHandler = vsAssemblyInfoHandler;
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSConditionDialogModel getModel(@ClusterProxyKey String runtimeId, String assemblyName,
                                          Principal principal) throws Exception
   {
      RuntimeViewsheet rvs;
      Viewsheet vs;
      VSAssembly vsAssembly;
      VSAssemblyInfo vsAssemblyInfo;

      try {
         rvs = viewsheetService.getViewsheet(runtimeId, principal);
         vs = rvs.getViewsheet();
         vsAssembly = vs.getAssembly(assemblyName);
         vsAssemblyInfo = vsAssembly.getVSAssemblyInfo();
      }
      catch(Exception e) {
         // Failed to get assembly
         throw e;
      }

      VSConditionDialogModel model = new VSConditionDialogModel();

      if(!(vsAssembly instanceof DynamicBindableVSAssembly)) {
         return model;
      }

      int sourceType = SourceInfo.ASSET;

      if(vsAssembly instanceof DataVSAssembly) {
         SourceInfo sourceInfo = ((DataVSAssembly) vsAssembly).getSourceInfo();

         if(sourceInfo != null) {
            sourceType = sourceInfo.getType();
         }
      }

      if(sourceType == SourceInfo.VS_ASSEMBLY) {
         ColumnSelection cols =
            VSUtil.getColumnsForVSAssemblyBinding(rvs, vsAssembly.getTableName());
         model.setFields(ConditionUtil
                            .getDataRefModelsFromColumnSelection(cols, this.dataRefModelFactoryService, 0));
      }
      else {
         Worksheet ws = vs.getBaseWorksheet();

         if(ws != null &&
            VSEventUtil.checkBaseWSPermission(vs, principal, viewsheetService.getAssetRepository(),
                                              ResourceAction.READ))
         {
            ColumnSelection cols = VSUtil.getBaseColumns(vsAssembly, true);

            if(vsAssembly instanceof TableVSAssembly) {
               VSUtil.mergeColumnAlias(cols, ((TableVSAssembly)vsAssembly).getColumnSelection());
            }

            model.setFields(ConditionUtil.getDataRefModelsFromColumnSelection(
               cols, this.dataRefModelFactoryService, 0));
         }
      }

      if(vsAssemblyInfo instanceof DataVSAssemblyInfo) {
         SourceInfo sourceInfo = ((DataVSAssemblyInfo) vsAssemblyInfo).getSourceInfo();

         if(sourceInfo != null && sourceInfo.getSource() != null) {
            model.setTableName(sourceInfo.getSource());
         }

         ConditionList conditionList = ((DataVSAssemblyInfo) vsAssemblyInfo)
            .getPreConditionList();

         if(conditionList != null) {
            model.setConditionList(ConditionUtil
                                      .fromConditionListToModel(conditionList, this.dataRefModelFactoryService));
         }
      }
      else if(vsAssemblyInfo instanceof OutputVSAssemblyInfo) {
         if(((OutputVSAssemblyInfo) vsAssemblyInfo).getBindingInfo() != null) {
            String tableName = ((OutputVSAssemblyInfo) vsAssemblyInfo).getBindingInfo()
               .getTableName();
            model.setTableName(tableName);
         }

         ConditionList conditionList = ((OutputVSAssemblyInfo) vsAssemblyInfo)
            .getPreConditionList();

         if(conditionList != null) {
            model.setConditionList(ConditionUtil
                                      .fromConditionListToModel(conditionList, this.dataRefModelFactoryService));
         }
      }

      return model;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setModel(@ClusterProxyKey String runtimeId, String assemblyName,
                        VSConditionDialogModel model, String linkUri,
                        Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      box.lockWrite();

      try {
         VSAssembly assembly = vs.getAssembly(assemblyName);
         VSAssemblyInfo info = assembly.getVSAssemblyInfo().clone();
         RuntimeWorksheet rws = assembly != null && assembly.isEmbedded() ?
            VSUtil.getRuntimeWorksheet(assembly.getViewsheet(), rvs.getViewsheetSandbox()) :
            rvs.getRuntimeWorksheet();
         String tName = assembly.getTableName();

         if(info instanceof DataVSAssemblyInfo) {
            ((DataVSAssemblyInfo) info)
               .setPreConditionList(ConditionUtil.fromModelToConditionList(
                  model.getConditionList(), ((DataVSAssemblyInfo) info).getSourceInfo(),
                  viewsheetService, principal, rws, tName));
         }
         else if(info instanceof OutputVSAssemblyInfo) {
            ((OutputVSAssemblyInfo) info)
               .setPreConditionList(ConditionUtil.fromModelToConditionList(
                  model.getConditionList(), null, viewsheetService, principal, rws, tName));
         }

         this.vsAssemblyInfoHandler.apply(rvs, info, viewsheetService, false, false,
                                          true, false, commandDispatcher, null, null, linkUri, null);
      }
      finally {
         box.unlockWrite();
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BrowseDataModel getDateRanges(@ClusterProxyKey String runtimeId, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      List<String> dateRanges = new ArrayList<>();

      for(DateCondition dateCondition : DateCondition.getBuiltinDateConditions()) {
         dateRanges.add(dateCondition.getName());
      }

      List<Assembly> dateranges = WorksheetEngine.getDateRanges(
         rvs.getAssetRepository(), principal);

      for(Assembly assembly : dateranges) {
         if(assembly instanceof DateRangeAssembly && assembly.isVisible()) {
            dateRanges.add(assembly.getName());
         }
      }

      return BrowseDataModel.builder()
         .values(dateRanges.toArray(new Object[0]))
         .build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BrowseDataModel browseData(@ClusterProxyKey String runtimeId, String tableName, String assemblyName,
                                     Boolean highlight, DataRefModel dataRefModel, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(assemblyName);
      RuntimeWorksheet rws = assembly != null && assembly.isEmbedded() ?
         VSUtil.getRuntimeWorksheet(assembly.getViewsheet(), rvs.getViewsheetSandbox()) :
         rvs.getRuntimeWorksheet();

      BrowseDataController browseDataController = new BrowseDataController();
      processNamedGroupInfo(dataRefModel, assembly.getVSAssemblyInfo());
      DataRef dataRef = dataRefModel.createDataRef();

      // for calc table, map cell name to column name
      if(assembly instanceof CalcTableVSAssembly) {
         TableCellBinding binding = VSUtil.getCalcCellBindingFromCellName(dataRef.getName(),
                                                                          (CalcTableVSAssembly) assembly);
         boolean refChanged = false;

         if(highlight != null && highlight) {
            ViewsheetSandbox box = rvs.getViewsheetSandbox();
            TableLens table = (TableLens) box.getData(assembly.getAbsoluteName());
            CalcTableLens calc = (CalcTableLens) Util.getNestedTable(table, CalcTableLens.class);

            if(calc != null) {
               FormulaTable formulaTable = calc.getElement();
               DataRef ref = VSLayoutTool.findAttribute(formulaTable, binding, dataRef.getName());

               if(ref != null && binding.getBType() == CellBinding.GROUP &&
                  XSchema.isDateType(ref.getDataType()))
               {
                  dataRef = new DateRangeRef(dataRef.getName(), new AttributeRef(binding.getValue()),
                                             binding.getDateOption());
                  ((DateRangeRef) dataRef).setOriginalType(ref.getDataType());
                  refChanged = true;
               }
            }
         }

         if(!refChanged) {
            dataRef = new AttributeRef(binding != null ? binding.getValue() :
                                          dataRef.getName());
         }
      }
      // support browse data for calc field (49018).
      else if(!dataRef.getName().startsWith("Range@") && assembly instanceof ChartVSAssembly) {
         VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
         GroupRef group = cinfo.getAggregateInfo().getGroup(dataRef);

         if(group != null) {
            if(!group.getName().startsWith("Range@")) {
               dataRef = group.getDataRef();
            }
         }
         else {
            CalculateRef calc = vs.getCalcField(tableName, dataRef.getName());

            if(calc != null) {
               dataRef = calc;
            }
         }
      }

      if(!(dataRef instanceof ColumnRef)) {
         dataRef = new ColumnRef(dataRef);
      }

      ((ColumnRef) dataRef).setApplyingAlias(false);
      browseDataController.setColumn((ColumnRef) dataRef);
      browseDataController.setName(tableName);
      AssetEntry wentry = vs.getBaseEntry();
      SourceInfo sourceInfo = null;

      if(wentry != null && wentry.isLogicModel()) {
         sourceInfo = new SourceInfo(XSourceInfo.MODEL, wentry.getParentPath(), wentry.getName());
      }
      else {
         sourceInfo = assembly instanceof DataVSAssembly ?
            ((DataVSAssembly) assembly).getSourceInfo() : null;
      }

      browseDataController.setSourceInfo(sourceInfo);
      browseDataController.setVSAssemblyName(assemblyName);

      try {
         return browseDataController.process(rws.getAssetQuerySandbox());
      }
      catch(Exception ex) {
         return null;
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ConditionTrapValidator checkConditionTrap(@ClusterProxyKey String runtimeId, ConditionTrapModel model,
                                                    Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs.getAssembly(model.tableName());
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      RuntimeWorksheet rws = assembly != null && assembly.isEmbedded() ?
         VSUtil.getRuntimeWorksheet(assembly.getViewsheet(), rvs.getViewsheetSandbox()) :
         rvs.getRuntimeWorksheet();
      String tName = assembly.getTableName();

      if(!(assembly instanceof DynamicBindableVSAssembly)) {
         return null;
      }

      SourceInfo sourceInfo = info instanceof DataVSAssemblyInfo ?
         ((DataVSAssemblyInfo) info).getSourceInfo() : null;

      ConditionList oldConditionList = ConditionUtil.fromModelToConditionList(
         model.oldConditionList(), sourceInfo, viewsheetService, principal, rws, tName);
      ConditionList newConditionList = ConditionUtil.fromModelToConditionList(
         model.newConditionList(), sourceInfo, viewsheetService, principal, rws, tName);

      VSModelTrapContext mtc = new VSModelTrapContext(rvs);

      if(mtc.isCheckTrap()) {
         AbstractModelTrapContext.TrapInfo trapInfo =
            mtc.checkTrap(getFixInfo(info, oldConditionList),
                          getFixInfo(info, newConditionList));
         boolean trap = trapInfo.showWarning();

         DataRefModel[] trapFields = Arrays.stream(mtc.getGrayedFields())
            .map(this.dataRefModelFactoryService::createDataRefModel)
            .toArray(DataRefModel[]::new);

         return ConditionTrapValidator.builder()
            .trapFields(trapFields)
            .showTrap(trap)
            .build();
      }

      return null;
   }

   /**
    * Get assembly info with applied conditions.
    */
   private VSAssemblyInfo getFixInfo(VSAssemblyInfo info, ConditionList conds) {
      VSAssemblyInfo newInfo = info.clone();

      if(newInfo instanceof DataVSAssemblyInfo) {
         ((DataVSAssemblyInfo) newInfo).setPreConditionList(conds);
      }
      else if(newInfo instanceof OutputVSAssemblyInfo) {
         ((OutputVSAssemblyInfo) newInfo).setPreConditionList(conds);
      }

      return newInfo;
   }

   public void processNamedGroupInfo(DataRefModel dataRefModel, VSAssemblyInfo vsAssemblyInfo) {
      if(!(dataRefModel instanceof ColumnRefModel) ||
         !(((ColumnRefModel) dataRefModel).getDataRefModel() instanceof NamedRangeRefModel))
      {
         return;
      }

      VSDataRef dcRangeRef = getDCRangeRef(vsAssemblyInfo);
      NamedRangeRefModel namedRangeRefModel = (NamedRangeRefModel) ((ColumnRefModel) dataRefModel).getDataRefModel();
      NamedGroupInfoModel groupInfoModel = namedRangeRefModel.getNamedGroupInfoModel();
      int type = groupInfoModel.getType();

      if(dcRangeRef != null &&
         dcRangeRef.getFullName().equals(dataRefModel.getName()))
      {
         VSDimensionRef rangeRef = (VSDimensionRef) dcRangeRef;
         groupInfoModel.setXNamedGroupInfo(rangeRef.getNamedGroupInfo());
      }
      else if(type == XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO) {
         SNamedGroupInfo sinfo = new SimpleNamedGroupInfo();
         List<GroupCondition> groups = groupInfoModel.getGroups();

         for(GroupCondition group : groups) {
            sinfo.setGroupValue(group.getName(), group.getValue());
         }

         groupInfoModel.setXNamedGroupInfo(sinfo);
      }
   }

   private VSDataRef getDCRangeRef(VSAssemblyInfo info) {
      DataRef[] refs = null;

      if(info instanceof CrosstabVSAssemblyInfo) {
         refs = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo().getRuntimeDateComparisonRefs();
      }
      else if(info instanceof ChartVSAssemblyInfo) {
         refs =
            ((ChartVSAssemblyInfo) info).getVSChartInfo().getRuntimeDateComparisonRefs();
      }


      for(int i = 0; i < refs.length; i++) {
         DataRef ref = refs[i];

         if(ref != null && ref instanceof VSDimensionRef && ref instanceof VSDataRef &&
            ((VSDimensionRef) ref).isDcRange())
         {
            return (VSDataRef) ref;
         }
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private DataRefModelFactoryService dataRefModelFactoryService;
   private VSAssemblyInfoHandler vsAssemblyInfoHandler;
}
