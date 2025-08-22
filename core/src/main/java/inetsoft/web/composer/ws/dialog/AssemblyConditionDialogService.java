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

package inetsoft.web.composer.ws.dialog;

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.VSScriptableController;
import inetsoft.web.binding.drm.*;
import inetsoft.web.binding.model.GroupRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.composer.model.condition.SubqueryTableModel;
import inetsoft.web.composer.model.ws.AssemblyConditionDialogModel;
import inetsoft.web.composer.model.ws.MVConditionPaneModel;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.joins.JoinUtil;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class AssemblyConditionDialogService extends WorksheetControllerService {

   public AssemblyConditionDialogService(ViewsheetService viewsheetService,
                                         DataRefModelFactoryService dataRefModelFactoryService,
                                         XRepository xrepository,
                                         VSScriptableController scriptController)
   {
      super(viewsheetService);
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.xrepository = xrepository;
      this.scriptController = scriptController;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public AssemblyConditionDialogModel getModel(@ClusterProxyKey String runtimeId, String assemblyName,
                                                Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      final TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(assemblyName);
      String source = tableAssembly == null ? null : tableAssembly.getSource();

      return XUtil.withFixedDateFormat(source, () -> {
         try {
            return createAssemblyConditionDialogModel(rws, tableAssembly, principal);
         }
         catch(Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
         }
      });
   }

   private AssemblyConditionDialogModel createAssemblyConditionDialogModel(RuntimeWorksheet rws,
                                                                           TableAssembly tableAssembly,
                                                                           Principal principal)
      throws Exception
   {
      Worksheet ws = tableAssembly.getWorksheet();
      ConditionList preConds =
         getConditionList(tableAssembly.getPreConditionList());
      ConditionList postConds =
         getConditionList(tableAssembly.getPostConditionList());
      ConditionList rankConds =
         getConditionList(tableAssembly.getRankingConditionList());
      ConditionList mvUpdatePreConds =
         getConditionList(tableAssembly.getMVUpdatePreConditionList());
      ConditionList mvUpdatePostConds =
         getConditionList(tableAssembly.getMVUpdatePostConditionList());
      ConditionList mvDeletePreConds =
         getConditionList(tableAssembly.getMVDeletePreConditionList());
      ConditionList mvDeletePostConds =
         getConditionList(tableAssembly.getMVDeletePostConditionList());

      List<SubqueryTableModel> subqueryTableModels = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         if(!assembly.isVisible()) {
            continue;
         }

         if(assembly instanceof TableAssembly) {
            SubqueryTableModel subqueryTableModel = new SubqueryTableModel();
            subqueryTableModel.setName(assembly.getName());
            subqueryTableModel.setDescription(
               ((TableAssembly) assembly).getDescription());
            subqueryTableModel.setColumns(ConditionUtil.getDataRefModelsFromColumnSelection(
               ((TableAssembly) assembly).getColumnSelection(),
               this.dataRefModelFactoryService, 0));
            subqueryTableModel.setCurrentTable(assembly.equals(tableAssembly));
            subqueryTableModels.add(subqueryTableModel);
         }
      }

      ColumnSelection columns = tableAssembly.getColumnSelection().clone();
      int boundCols = columns.getAttributeCount();

      if(tableAssembly instanceof BoundTableAssembly) {
         final SourceInfo sourceInfo = (SourceInfo) ((BoundTableAssembly) tableAssembly)
            .getSourceInfo().clone();
         ColumnSelection cinfo = AssetEventUtil.getAttributesBySource(
            super.getWorksheetEngine(), rws.getUser(), sourceInfo);

         for(int i = 0; i < cinfo.getAttributeCount(); i++) {
            if(!columns.containsAttribute(cinfo.getAttribute(i))) {
               columns.addAttribute(cinfo.getAttribute(i));
            }
         }
      }

      columns = VSUtil.sortColumns(columns);
      // keep the columns bound to this table in the front of the list.
      DataRefModel[] preAggregateFields = ConditionUtil
         .getDataRefModelsFromColumnSelection(columns, this.dataRefModelFactoryService, boundCols);
      clearNoneFormula(preAggregateFields);
      AggregateInfo aggregateInfo = tableAssembly.getAggregateInfo();
      DataRefModel[] postAggregateFields =
         new DataRefModel[aggregateInfo.getGroupCount() + aggregateInfo .getAggregateCount()];
      DataRefModel[] groupFields = new DataRefModel[aggregateInfo.getGroupCount()];

      for(int i = 0; i < aggregateInfo.getGroupCount(); i++) {
         postAggregateFields[i] =
            dataRefModelFactoryService.createDataRefModel(aggregateInfo.getGroup(i));

         final GroupRefModel group = (GroupRefModel) dataRefModelFactoryService.createDataRefModel(
            aggregateInfo.getGroup(i));
         final ColumnRefModel columnRef = group.getRef();

         if(group.getAssemblyName() != null && !group.getAssemblyName().isEmpty()
            && columnRef != null)
         {
            group.setDataType(columnRef.getDataType());
         }

         groupFields[i] = group;

         // preAggregateFields has been sort.
         int idx = findIndex(preAggregateFields, aggregateInfo.getGroup(i));

         if(idx >= 0) {
            preAggregateFields[idx] = groupFields[i];
         }
      }

      List<DataRefModel> newPreAggregates = new ArrayList<>();

      for(int i = 0; i < preAggregateFields.length; i++) {
         DataRefModel nagg = preAggregateFields[i];
         boolean postAgg = nagg instanceof GroupRefModel &&
            nagg.getRefType() == DataRef.CUBE_TIME_DIMENSION;

         if(!postAgg) {
            newPreAggregates.add(nagg);
         }
      }

      preAggregateFields = newPreAggregates.toArray(new DataRefModel[0]);

      for(int i = 0, j = aggregateInfo.getGroupCount(); i < aggregateInfo.getAggregateCount();
          i++, j++)
      {
         postAggregateFields[j] =
            dataRefModelFactoryService.createDataRefModel(aggregateInfo.getAggregate(i));
      }

      AssemblyConditionDialogModel model = new AssemblyConditionDialogModel();
      MVConditionPaneModel mvConditionPaneModel = model.getMvConditionPaneModel();
      model.setAdvanced(!isConditionListSimple(preConds) || !isConditionListSimple(postConds));
      model.setPreAggregateFields(preAggregateFields);
      model.setPostAggregateFields(postAggregateFields);
      model.setPreAggregateConditionList(ConditionUtil.fromConditionListToModel(preConds,
                                                                                dataRefModelFactoryService));
      model.setPostAggregateConditionList(
         ConditionUtil.fromConditionListToModel(postConds, dataRefModelFactoryService));
      model.setRankingConditionList(
         ConditionUtil.fromConditionListToModel(rankConds, dataRefModelFactoryService));
      mvConditionPaneModel.setPreAggregateFields(
         groupFields.length > 0 || postAggregateFields.length > 0 ? groupFields : ConditionUtil
            .getDataRefModelsFromColumnSelection(
               tableAssembly.getColumnSelection(), this.dataRefModelFactoryService, 0));
      mvConditionPaneModel.setPostAggregateFields(postAggregateFields);
      mvConditionPaneModel.setAppendPreAggregateConditionList(
         ConditionUtil.fromConditionListToModel(mvUpdatePreConds, dataRefModelFactoryService));
      mvConditionPaneModel.setAppendPostAggregateConditionList(
         ConditionUtil.fromConditionListToModel(mvUpdatePostConds, dataRefModelFactoryService));
      mvConditionPaneModel.setDeletePreAggregateConditionList(
         ConditionUtil.fromConditionListToModel(mvDeletePreConds, dataRefModelFactoryService));
      mvConditionPaneModel.setDeletePostAggregateConditionList(
         ConditionUtil.fromConditionListToModel(mvDeletePostConds, dataRefModelFactoryService));
      mvConditionPaneModel.setForceAppendUpdates(tableAssembly.isMVForceAppendUpdates());
      model.setSubqueryTables(subqueryTableModels.toArray(
         new SubqueryTableModel[subqueryTableModels.size()]));
      model.setVariableNames(getVariableNames(rws, principal));
      model.setExpressionFields(getExpressionFields(tableAssembly));
      model.setScriptDefinitions(getScriptDefinitions(rws, tableAssembly, principal));

      return model;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setModel(@ClusterProxyKey String runtimeId, String assemblyName,
                        AssemblyConditionDialogModel model, Principal principal,
                        CommandDispatcher commandDispatcher) throws Exception
   {
      final String tableAssemblyName = Tool.byteDecode(assemblyName);
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      final TableAssembly tableAssembly = (TableAssembly) ws.getAssembly(tableAssemblyName);
      String source = tableAssembly == null ? null : tableAssembly.getSource();

      XUtil.withFixedDateFormat(source, () -> {
         try {
            updateByModel(rws, tableAssembly, tableAssemblyName, model, principal, commandDispatcher);
         }
         catch(Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
         }
      });

      return null;
   }

   private void updateByModel(RuntimeWorksheet rws, TableAssembly tableAssembly, String assemblyName,
                              AssemblyConditionDialogModel model, Principal principal,
                              CommandDispatcher commandDispatcher)
      throws Exception
   {
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      VariableTable variableTable = box.getVariableTable();
      Worksheet ws = rws.getWorksheet();
      SourceInfo sourceInfo = tableAssembly instanceof BoundTableAssembly ?
         ((BoundTableAssembly) tableAssembly).getSourceInfo() : null;
      ConditionList preConds = ConditionUtil
         .fromModelToConditionList(model.getPreAggregateConditionList(),
                                   sourceInfo, super.getWorksheetEngine(), principal, rws);
      ConditionList postConds = ConditionUtil
         .fromModelToConditionList(model.getPostAggregateConditionList(),
                                   sourceInfo, super.getWorksheetEngine(), principal, rws);
      ConditionList rankConds = ConditionUtil
         .fromModelToConditionList(model.getRankingConditionList(),
                                   sourceInfo, super.getWorksheetEngine(), principal, rws);

      MVConditionPaneModel mvPaneModel = model.getMvConditionPaneModel();
      ConditionList mvUpdatePreConds = ConditionUtil.fromModelToConditionList(
         mvPaneModel.getAppendPreAggregateConditionList(), sourceInfo, super.getWorksheetEngine(),
         principal, rws);
      ConditionList mvUpdatePostConds = ConditionUtil.fromModelToConditionList(
         mvPaneModel.getAppendPostAggregateConditionList(), sourceInfo, super.getWorksheetEngine(),
         principal, rws);
      ConditionList mvDeletePreConds = ConditionUtil.fromModelToConditionList(
         mvPaneModel.getDeletePreAggregateConditionList(), sourceInfo, super.getWorksheetEngine(),
         principal, rws);
      ConditionList mvDeletePostConds = ConditionUtil.fromModelToConditionList(
         mvPaneModel.getDeletePostAggregateConditionList(), sourceInfo, super.getWorksheetEngine(),
         principal, rws);
      VSUtil.removeVariable(variableTable, (ConditionList) tableAssembly.getPreConditionList(), preConds);
      VSUtil.removeVariable(variableTable, (ConditionList) tableAssembly.getPostConditionList(), postConds);
      VSUtil.removeVariable(variableTable, (ConditionList) tableAssembly.getRankingConditionList(), rankConds);
      VSUtil.removeVariable(variableTable,
                            (ConditionList) tableAssembly.getMVUpdatePreConditionList(), mvUpdatePreConds);
      VSUtil.removeVariable(variableTable,
                            (ConditionList) tableAssembly.getMVUpdatePostConditionList(), mvUpdatePostConds);
      VSUtil.removeVariable(variableTable,
                            (ConditionList) tableAssembly.getMVDeletePreConditionList(), mvDeletePreConds);
      VSUtil.removeVariable(variableTable,
                            (ConditionList) tableAssembly.getMVDeletePostConditionList(), mvDeletePostConds);
      boolean conditionChanged = !Tool.equals(preConds, tableAssembly.getPreConditionList()) ||
         !Tool.equals(postConds, tableAssembly.getPostConditionList()) ||
         !Tool.equals(rankConds, tableAssembly.getRankingConditionList()) ||
         !Tool.equals(mvUpdatePreConds, tableAssembly.getMVUpdatePreConditionList()) ||
         !Tool.equals(mvUpdatePostConds, tableAssembly.getMVUpdatePostConditionList()) ||
         !Tool.equals(mvDeletePreConds, tableAssembly.getMVDeletePreConditionList()) ||
         !Tool.equals(mvDeletePostConds, tableAssembly.getMVDeletePostConditionList()) ||
         !Tool.equals(mvPaneModel.isForceAppendUpdates(), tableAssembly.isMVForceAppendUpdates());
      tableAssembly.setPreConditionList(preConds);
      tableAssembly.setPostConditionList(postConds);
      tableAssembly.setRankingConditionList(rankConds);
      tableAssembly.setMVUpdatePreConditionList(mvUpdatePreConds);
      tableAssembly.setMVUpdatePostConditionList(mvUpdatePostConds);
      tableAssembly.setMVDeletePreConditionList(mvDeletePreConds);
      tableAssembly.setMVDeletePostConditionList(mvDeletePostConds);
      tableAssembly.setMVForceAppendUpdates(mvPaneModel.isForceAppendUpdates());
      ColumnSelection columns = tableAssembly.getColumnSelection();

      for(int i = 0; i < preConds.getSize(); i += 2) {
         ConditionItem cond = preConds.getConditionItem(i);

         if(cond.getAttribute() instanceof ColumnRef) {
            ColumnRef column = (ColumnRef) cond.getAttribute();

            if(!columns.containsAttribute(column)) {
               column = (ColumnRef) column.clone();
               column.setVisible(false);
               columns.addAttribute(column);
            }
         }
      }

      ws.checkDependencies();

      if(WorksheetEventUtil.refreshVariables(
         rws, super.getWorksheetEngine(), assemblyName, commandDispatcher))
      {
         return;
      }

      WorksheetEventUtil.refreshColumnSelection(rws, assemblyName, true, commandDispatcher);

      if(conditionChanged && JoinUtil.hasLiveDataJoinMember(ws, tableAssembly)) {
         JoinUtil.confirmJoinDependingsToMeta(assemblyName, commandDispatcher);
      }
      else {
         WorksheetEventUtil.loadTableData(rws, assemblyName, true, true);
         WorksheetEventUtil.refreshAssembly(rws, assemblyName, true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
      }

      AssetEventUtil.refreshTableLastModified(ws, assemblyName, true);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BrowseDataModel browseData(@ClusterProxyKey String runtimeId, String assemblyName, DataRefModel dataRefModel, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);
      BrowseDataController browseDataController = new BrowseDataController();
      DataRef dataRef = dataRefModel.createDataRef();

      if(!(dataRef instanceof ColumnRef)) {
         dataRef = new ColumnRef(dataRef);
      }

      browseDataController.setColumn((ColumnRef) dataRef);
      browseDataController.setName(assemblyName);
      return browseDataController.process(rws.getAssetQuerySandbox());
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BrowseDataModel getDateRanges(@ClusterProxyKey String runtimeId, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      List<String> dateRanges = new ArrayList<>();
      List<String> dateRangeLabels = new ArrayList<>();

      for(DateCondition dateCondition : DateCondition.getBuiltinDateConditions()) {
         dateRanges.add(dateCondition.getName());
         dateRangeLabels.add(Catalog.getCatalog().getString(dateCondition.getLabel()));
      }

      for(Assembly assembly : ws.getAssemblies()) {
         if(assembly instanceof DateRangeAssembly && assembly.isVisible()) {
            dateRanges.add(assembly.getName());
         }
      }

      return BrowseDataModel.builder()
         .values(dateRanges.toArray(new Object[0]))
         .labels(dateRangeLabels.toArray(new Object[0]))
         .build();
   }

   private ConditionList getConditionList(ConditionListWrapper wrapper) {
      return wrapper == null ? null :
         (ConditionList) wrapper.getConditionList().clone();
   }

   /**
    * Check if a condition list is simple. A simple condition list is one that does not
    * contain OR junctions and any indentation.
    */
   private boolean isConditionListSimple(ConditionList conditionList) {
      for(int i = 0; i < conditionList.getSize(); i++) {
         if(conditionList.isConditionItem(i) &&
            conditionList.getConditionItem(i).getLevel() != 0)
         {
            return false;
         }

         if(conditionList.isJunctionOperator(i) &&
            conditionList.getJunctionOperator(i).getJunction() !=
               JunctionOperator.AND)
         {
            return false;
         }
      }

      return true;
   }

   private ObjectNode getScriptDefinitions(RuntimeWorksheet rws, TableAssembly table,
                                           Principal principal) throws Exception
   {
      return this.scriptController.getScriptDefinition(rws, table, principal);
   }

   /**
    * Get a list of variable names for this worksheet.
    */
   private List<String> getVariableNames(RuntimeWorksheet rws, Principal principal) {
      Worksheet ws = rws.getWorksheet();
      Assembly[] arr = ws.getAssemblies();
      List<String> list = new ArrayList<>();
      Set added = new HashSet();

      for(int i = 0; i < arr.length; i++) {
         WSAssembly assembly = (WSAssembly) arr[i];

         if(assembly.isVariable() && assembly.isVisible()) {
            VariableAssembly vassembly = (VariableAssembly) assembly;
            UserVariable var = vassembly.getVariable();

            if(var != null && !added.contains(var.getName())) {
               added.add(var.getName());
               list.add(var.getName());
            }
         }
      }

      // for bug1291823096435, display the parameters which is defined in
      // SRPrincipal
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      box.setBaseUser(principal);
      VariableTable vtable = box.getVariableTable();
      Enumeration keys = vtable.keys();

      while(keys.hasMoreElements()) {
         String key = (String) keys.nextElement();

         if(!list.contains(key)) {
            list.add(key);
         }
      }

      return list;
   }

   // from ExpressionValueEditor.as editBtnListener
   private List<ColumnRefModel> getExpressionFields(TableAssembly table) {
      boolean isMV = false; // TODO
      ColumnSelection columns = table.getColumnSelection(false);
      List<ColumnRefModel> fields = new ArrayList<>();

      if(isMV) {
//         for(var i:int = 0; i < MV_PROPS.length; i++) {
//            fields.push(new AttributeRef(null, MV_PROPS[i]));
//         }
      }
      else {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef ref = columns.getAttribute(i);
            String dataType = ref.getDataType();

            while(ref instanceof DataRefWrapper) {
               ref = ((DataRefWrapper) ref).getDataRef();

               if(ref instanceof ColumnRef) {
                  break;
               }
            }

            if(ref != null && !(ref instanceof ColumnRef)) {
               ref = new ColumnRef(ref);
               ((ColumnRef) ref).setDataType(dataType);
            }

            ColumnRef dref = (ColumnRef) ref;

            if(dref == null || dref != null && dref.isExpression()) {
               continue;
            }

            // fix Bug #33556, should check duplicate.
            // column may be done group by another column, getting it`s ColumnRef may be duplicate.
            boolean contains = false;

            for(ColumnRefModel columnRef : fields) {
               if(columnRef.getName() != null && columnRef.getName().equals(dref.getName())) {
                  contains = true;
               }
            }

            if(!contains) {
               ColumnRefModel model = (ColumnRefModel)
                  this.dataRefModelFactoryService.createDataRefModel(dref);

               if(XSchema.isDateType(dref.getDataType())) {
                  model.setDataType(dref.getDataType());
                  model.getDataRefModel().setDataType(ref.getDataType());
               }

               if(model != null && StringUtils.isEmpty(model.getAlias()) &&
                  StringUtils.isEmpty(model.getAttribute()))
               {
                  model.setView("Column[" + i + "]");
               }

               fields.add(model);
            }
         }
      }

      return fields;
   }

   private void clearNoneFormula(DataRefModel[] refs) {
      for(int i = 0; i < refs.length; i++) {
         DataRefModel ref = refs[i];

         if(ref instanceof ColumnRefModel) {
            ColumnRefModel col = (ColumnRefModel) ref;
            AbstractDataRefModel aref = (AbstractDataRefModel) col.getDataRefModel();

            if("None".equalsIgnoreCase(col.getDefaultFormula())) {
               col.setDefaultFormula(null);
            }

            if("None".equalsIgnoreCase(aref.getDefaultFormula())) {
               aref.setDefaultFormula(null);
            }

            col.setVisible(true);
         }
      }
   }

   private int findIndex(DataRefModel[] preAggregateFields, GroupRef groupField) {
      if(ObjectUtils.isEmpty(preAggregateFields)) {
         return -1;
      }

      for (int i = 0; i < preAggregateFields.length; i++) {
         if(preAggregateFields[i].getName().equals(groupField.getName())) {
            return i;
         }
      }

      return -1;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
   private XRepository xrepository;
   private VSScriptableController scriptController;
}
