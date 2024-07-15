/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.*;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.AggregateInfoModel;
import inetsoft.web.binding.model.GroupRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.ws.AggregateDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.joins.JoinUtil;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class AggregateDialogController extends WorksheetController {
   @GetMapping("/api/composer/ws/dialog/aggregate-dialog-model/{runtimeid}")
   @ResponseBody
   public AggregateDialogModel getModel(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tname,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      AggregateDialogModel model = new AggregateDialogModel();
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      model.setMaxCol(Util.getOrganizationMaxColumn());

      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      model.setName(tname);

      if(table != null) {
         AggregateInfo groupInfo = table.getAggregateInfo();

         if(groupInfo != null) {
            groupInfo = (AggregateInfo) groupInfo.clone();
         }

         ColumnSelection columns = table.getColumnSelection();
         ArrayList<ColumnRefModel> availableColumns = new ArrayList<>();
         Enumeration<?> e = columns.getAttributes();

         while(e.hasMoreElements()) {
            ColumnRef ref = (ColumnRef) e.nextElement();
            final boolean isRangeColumn = isAutoRangeColumn(ref);

            if(!isRangeColumn) {
               if(!StringUtils.isEmpty(ref.getAlias()) && groupInfo != null &&
                  groupInfo.getAggregate(ref.getDataRef()) != null)
               {
                  ColumnRef clone = ref.clone();
                  clone.setAlias(null);
                  addColumnRefModel(rws, table, clone, availableColumns);
               }

               addColumnRefModel(rws, table, ref, availableColumns);
            }
         }

         model.setColumns(availableColumns.toArray(new ColumnRefModel[0]));
         Map<String, String[]> groupMap = new OrderedMap<>();

         // 1. groups, 2. aggregates, 3. other columns
         if(groupInfo != null) {
            GroupRef[] groups = groupInfo.getGroups();

            for(final GroupRef group : groups) {
               DataRef column = columns.findAttribute(group);
               final boolean isRangeColumn = isAutoRangeColumn(column);

               if(column != null && !isRangeColumn) {
                  String[] names = getNameGroups(table, column);
                  groupMap.put(column.getName(), names);
               }
            }

            AggregateRef[] aggregates = groupInfo.getAggregates();

            for(final AggregateRef aggregate : aggregates) {
               DataRef column = columns.findAttribute(aggregate);

               if(column != null) {
                  String[] names = getNameGroups(table, column);
                  groupMap.put(column.getName(), names);
               }
            }
         }

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef column = columns.getAttribute(i);
            String[] names = getNameGroups(table, column);

            if(!groupMap.containsKey(column)) {
               groupMap.put(column.getName(), names);
            }
         }

         AssetQuerySandbox box = rws.getAssetQuerySandbox();
         Map<String, String> aliasmap = box.getColumnInfoMapping(tname);

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef column = columns.getAttribute(i);
            String name = column.getName();
            String alias = aliasmap.get(name);
            alias = alias == null ? name : alias;
            alias = AssetUtil.fixOuterName(alias);

            if((column instanceof ColumnRef) &&
               (column.getRefType() & DataRef.CUBE) == DataRef.CUBE)
            {
               ColumnRef col = (ColumnRef) column;
               alias = alias.equals(col.getAlias()) ? alias : col.getAttribute();
            }

            if(!name.equals(alias)) {
               aliasmap.put(name, alias);
            }
         }
         if(groupInfo != null) {
            for(GroupRef ref : groupInfo.getGroups()) {
               model.getInfo().getGroups().add(
                  (GroupRefModel) dataRefModelFactoryService.createDataRefModel(ref));
            }

            for(AggregateRef ref : groupInfo.getAggregates()) {
               model.getInfo().getAggregates().add(
                  (AggregateRefModel) dataRefModelFactoryService.createDataRefModel(ref));
            }

            model.getInfo().setCrosstab(groupInfo.isCrosstab());
         }

         model.setGroupMap(groupMap);
         model.setAliasMap(aliasmap);
      }

      return model;
   }

   private void addColumnRefModel(RuntimeWorksheet rws, TableAssembly table, ColumnRef ref,
                                  ArrayList<ColumnRefModel> availableColumns)
      throws Exception
   {
      ColumnRefModel refModel = (ColumnRefModel) dataRefModelFactoryService.createDataRefModel(ref);

      if(table instanceof BoundTableAssembly) {
         AnalyticRepository analyticRepository =
            AnalyticAssistant.getAnalyticAssistant().getAnalyticRepository();
         String formula = WSBindingHelper.getModelDefaultFormula((BoundTableAssembly) table,
            ref, rws, analyticRepository);
         refModel.setDefaultFormula(formula);
      }

      if(refModel.getName().isEmpty()) {
         refModel.setView("Column[" + availableColumns.size() + "]");
      }
      else if(refModel.getAttribute().isEmpty()) {
         refModel.setView(refModel.getView() + "Column[" + availableColumns.size() + "]");
      }

      availableColumns.add(refModel);
   }

   private boolean isAutoRangeColumn(DataRef group) {
      DateRangeRef dateRangeRef = getDateRangeRef(group);
      return dateRangeRef != null && dateRangeRef.isAutoCreate();
   }

   /**
    * Get all the named groups.
    */
   private String[] getNameGroups(TableAssembly table, DataRef ref) {
      List<String> nameslist = new ArrayList<>();
      Worksheet ws = table.getWorksheet();
      Assembly[] assemblies = ws.getAssemblies();
      AttachedAssembly attached =
         AssetUtil.getColumnAttachedAssembly(table, (ColumnRef) ref);

      for(Assembly assembly : assemblies) {
         if(!assembly.isVisible()) {
            continue;
         }

         if(assembly instanceof NamedGroupAssembly) {
            NamedGroupAssembly nassembly = (NamedGroupAssembly) assembly;
            boolean matched = false;

            switch(nassembly.getAttachedType()) {
               case NamedGroupAssembly.COLUMN_ATTACHED:
                  matched = attached != null && attached.equals(nassembly);
                  break;
               case NamedGroupAssembly.DATA_TYPE_ATTACHED:
                  matched = AssetUtil.isCompatible(nassembly.getAttachedDataType(),
                     ref.getDataType());
                  break;
            }

            if(matched) {
               nameslist.add(nassembly.getName());
            }
         }
      }

      String[] names = new String[nameslist.size()];
      nameslist.toArray(names);
      return names;
   }

   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/ws/dialog/aggregate-dialog-model")
   public void setAggregateInfo(
      @Payload AggregateDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tname = model.getName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table != null) {
         AggregateInfo oldAggInfo = table.getAggregateInfo();
         AggregateInfo ginfo = getAggregateInfo(model.getInfo(), table.getColumnSelection());

         if(Tool.equalsContent(oldAggInfo, ginfo) &&
            Tool.equalsContent(oldAggInfo.getSecondaryAggregates() ,ginfo.getSecondaryAggregates())
            || !checkDeleteColumns(ws, ginfo, table, commandDispatcher))
         {
            return;
         }

         boolean oaggregate = table.isAggregate();
         applyAggregateInfo(table, ginfo);

         if(ginfo != null && !ginfo.isEmpty() && table.isEditMode()) {
            table.setEditMode(false);
         }

         boolean naggregate = table.isAggregate();

         if(WorksheetEventUtil.refreshVariables(
            rws, super.getWorksheetEngine(), tname, commandDispatcher, true))
         {
            return;
         }

         if(oaggregate != naggregate &&
            (table instanceof ComposedTableAssembly) &&
            ((ComposedTableAssembly) table).isHierarchical())
         {
            ((ComposedTableAssembly) table).setHierarchical(false);
         }

         refreshMirrorTableDateGroupRef(rws, tname, commandDispatcher);
         WorksheetEventUtil.refreshColumnSelection(rws, tname, true, commandDispatcher);

         if(!ginfo.equals(oldAggInfo) && JoinUtil.hasLiveDataJoinMember(ws, table)) {
            JoinUtil.confirmJoinDependingsToMeta(tname, commandDispatcher);
         }
         else {
            WorksheetEventUtil.loadTableData(rws, tname, true, true);
            WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
            WorksheetEventUtil.layout(rws, commandDispatcher);
         }

         AssetEventUtil.refreshTableLastModified(ws, tname, true);
      }
   }

   private void applyAggregateInfo(TableAssembly table, AggregateInfo ginfo) {
      updateAggregateInfo(ginfo, table);
      ginfo = table.getAggregateInfo();
      table.setAggregate(ginfo != null && !ginfo.isEmpty());
   }

   private void refreshMirrorTableDateGroupRef(RuntimeWorksheet rws, String tname,
                                               CommandDispatcher dispatcher)
      throws InvalidDependencyException
   {
      if(rws == null) {
         return;
      }

      Worksheet ws = rws.getWorksheet();

      if(ws == null) {
         return;
      }

      WorksheetEventUtil.refreshColumnSelection(rws, tname, false, dispatcher);
      ws.checkDependencies();

      Assembly assembly = ws.getAssembly(tname);

      if(!(assembly instanceof TableAssembly)) {
         return;
      }

      AssemblyRef[] arr = ws.getDependings(assembly.getAssemblyEntry());
      Assembly[] assemblies = new Assembly[arr.length];

      for(int i = 0; i < arr.length; i++) {
         assemblies[i] = ws.getAssembly(arr[i].getEntry().getName());
      }

      // sort assemblies according to dependencies
      Arrays.sort(assemblies, new DependencyComparator(ws, true));

      for(int i = 0; i < assemblies.length; i++) {
         WSAssembly sub = (WSAssembly) assemblies[i];
         sub.dependencyChanged(tname);

         if(!(sub instanceof MirrorTableAssembly)) {
            continue;
         }

         MirrorTableAssembly table = (MirrorTableAssembly) sub;
         WSAssemblyInfo wsAssemblyInfo = table.getWSAssemblyInfo();

         if(!(wsAssemblyInfo instanceof MirrorTableAssemblyInfo)) {
            continue;
         }

         MirrorTableAssemblyInfo mirrorTableInfo = (MirrorTableAssemblyInfo) wsAssemblyInfo;

         if(mirrorTableInfo.getImpl() == null ||
            mirrorTableInfo.getImpl().getAssembly() != assembly)
         {
            continue;
         }

         AggregateInfo aggregateInfo = table.getAggregateInfo();

         if(aggregateInfo == null) {
            continue;
         }

         ColumnSelection columnSelection = ((TableAssembly) assembly).getColumnSelection(true);

         if(columnSelection == null) {
            continue;
         }

         boolean aggChanged = false;
         AggregateInfo newAggregateInfo = (AggregateInfo) aggregateInfo.clone();

         for(int j = newAggregateInfo.getGroupCount() - 1; j >= 0; j--) {
            GroupRef group = newAggregateInfo.getGroup(j);

            if(group == null) {
               continue;
            }

            DateRangeRef dateRangeRef = getDateRangeRef(group);

            if(dateRangeRef == null || dateRangeRef.getDataRef() == null) {
               continue;
            }

            if(columnSelection.getAttribute(dateRangeRef.getDataRef().getAttribute()) == null) {
               newAggregateInfo.removeGroup(j);
               aggChanged |= true;
            }
         }

         updateAggregateInfo(newAggregateInfo, (TableAssembly) sub);
         table.setAggregate(newAggregateInfo != null && !newAggregateInfo.isEmpty());

         if(aggChanged) {
            refreshMirrorTableDateGroupRef(rws, sub.getName(), dispatcher);
         }
      }
   }

   private boolean checkDeleteColumns(Worksheet ws, AggregateInfo ainfo, TableAssembly table,
                                      CommandDispatcher dispatcher)
   {
      if(ainfo.isEmpty()) {
         return true;
      }

      ColumnSelection cols = table.getColumnSelection();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);
         String col = ref.getName();

         if(ainfo.getGroup(col) != null || getAggregateRef(ainfo, col) != null) {
            continue;
         }

         if(!(ref instanceof ColumnRef)) {
            continue;
         }

         ColumnRef colRef = (ColumnRef) ref;

         if(colRef.getDataRef() instanceof DateRangeRef) {
            DateRangeRef dateRangeRef = (DateRangeRef) colRef.getDataRef();
            String innerRef = dateRangeRef.getDataRef().getName();

            if(ainfo.getGroup(innerRef) != null) {
               continue;
            }
         }


         if(!allowsDeletion(ws, table, (ColumnRef) ref)) {
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.columnsDependency"));
            command.setType(MessageCommand.Type.WARNING);
            command.setAssemblyName(table.getName());
            dispatcher.sendCommand(command);
            return false;
         }
      }

      return true;
   }

   private AggregateRef getAggregateRef(AggregateInfo ainfo, String gname) {
      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         if(Tool.equals(gname, ainfo.getAggregate(i).getName())) {
            return ainfo.getAggregate(i);
         }
      }

      return null;
   }

   public static AggregateInfo getAggregateInfo(
      AggregateInfoModel ginfo,
      ColumnSelection columns)
   {
      AggregateInfo info = new AggregateInfo();
      info.setCrosstab(ginfo.isCrosstab());
      HashMap<String, ColumnRef> availableColumns = new HashMap<>();
      Enumeration e = columns.getAttributes();

      while(e.hasMoreElements()) {
         ColumnRef ref = (ColumnRef) e.nextElement();
         availableColumns.put(ref.getName(), ref);

         if(!StringUtils.isEmpty(ref.getAlias())) {
            ColumnRef clone = ref.clone();
            clone.setApplyingAlias(false);

            if(!availableColumns.containsKey(clone.getName())) {
               availableColumns.put(clone.getName(), ref);
            }
         }
      }

      for(GroupRefModel ref : ginfo.getGroups()) {
         DataRef original = availableColumns.get(ref.getRef().getName());
         GroupRef group = (GroupRef) ref.createDataRef();
         group.setDataRef(original);

         if(ginfo.isCrosstab()) {
            info.addGroup(group, false);
         }
         else {
            info.addGroup(group,
               ref.getRef() != null && !XSchema.isDateType(ref.getRef().getDataType()));
         }
      }

      for(AggregateRefModel ref : ginfo.getAggregates()) {
         DataRef original = availableColumns.get(ref.getRef().getName());
         DataRef secondary = null;

         if(ref.getSecondaryColumn() != null) {
            secondary = availableColumns.get(ref.getSecondaryColumn().getName());
         }

         AggregateRef agg = (AggregateRef) ref.createDataRef();
         agg.setDataRef(original);
         agg.setSecondaryColumn(secondary);

         if(info.containsAggregate(agg)) {
            info.addSecondaryAggregate(agg);
         }
         else {
            info.addAggregate(agg, false);
         }
      }

      return info;
   }

   /**
    * Update the aggregate info.
    */
   private void updateAggregateInfo(AggregateInfo ginfo, TableAssembly table) {
      processDateGrouping(table, ginfo);
      table.setAggregateInfo(ginfo);
      AggregateRef[] secondaryAggregates = ginfo.getSecondaryAggregates();

      if(secondaryAggregates.length == 0) {
         return;
      }

      ColumnSelection columns = table.getColumnSelection();

      for(AggregateRef sref : secondaryAggregates) {
         ColumnRef cref = (ColumnRef) sref.getDataRef();
         DataRef secondaryRef = sref.getSecondaryColumn();
         String base = cref.getAttribute();
         String name = getNextName(base, columns);
         ExpressionRef exp = new ExpressionRef(sref.getEntity(), name);
         String formula = sref.isEntityBlank() ? sref.getAttribute() :
            sref.getEntity() + "." + sref.getAttribute();
         exp.setExpression("field['" + formula + "']");
         ColumnRef column = new ColumnRef(exp);
         column.setDataType(cref.getDataType());
         columns.addAttribute(column);

         AggregateFormula aformula = sref.getFormula();
         AggregateRef aref = new AggregateRef(column, secondaryRef, aformula);
         aref.setN(sref.getN());
         aref.setPercentageOption(sref.getPercentageOption());
         ginfo.addAggregate(aref);
      }

      ginfo.removeSecondaryAggregates();
      table.setColumnSelection(columns);
   }

   /**
    * Process date grouping.
    */
   private void processDateGrouping(TableAssembly table, AggregateInfo ginfo) {
      int gcount = ginfo.getGroupCount();

      ColumnSelection columns = table.getColumnSelection();

      AggregateInfo oaginfo = table.getAggregateInfo();
      oaginfo = oaginfo == null ? new AggregateInfo() : oaginfo;
      ArrayList<ColumnRef> list = new ArrayList<>();

      for(int i = 0; i < gcount; i++) {
         GroupRef gref = ginfo.getGroup(i);
         ColumnRef column = (ColumnRef) gref.getDataRef();
         list.add(column);
         int dgroup = gref.getDateGroup();

         if(dgroup == -1 || dgroup == GroupRef.NONE_DATE_GROUP) {
            continue;
         }

         // only a created date range ref to do group, or rename a group ref,
         // the dateRef will be not null, otherwise is null
         DateRangeRef dateRef = getDateRangeRef(gref);
         DataRef bref = column;

         if(dateRef != null && !renamedRef(oaginfo, dateRef)) {
            dateRef = null;
         }

         int idx = -1;
         int bidx = -1;

         if(dateRef != null) {
            idx = columns.indexOfAttribute(column);
         }

         // if base ref is date range ref, we just modify its date option,
         // instead of create a new date range ref, so user's specified name
         // will be hold
         if(dateRef == null) {
            // use DateRangeRef to markup a column
            String alias = column.getAlias();
            String name = DateRangeRef.getName(alias != null ? alias : column.getAttribute(), dgroup);
            DataRef ref = column.getDataRef();
            dateRef = new DateRangeRef(name, ref, dgroup);
            dateRef.setOriginalType(column.getDataType());

            if(bref != null) {
               bidx = columns.indexOfAttribute(bref);
            }
         }
         else {
            dateRef.setDateOption(dgroup);
         }

         String dtype = dateRef.getDataType();

         if(XSchema.TIME.equals(dateRef.getOriginalType()) &&
            dateRef.getDateOption() != DateRangeRef.HOUR_OF_DAY_DATE_GROUP)
         {
            dtype = dateRef.getOriginalType();
         }

         column = new ColumnRef(dateRef);
         column.setDataType(dtype);

         if(idx != -1) {
            ColumnRef oref = (ColumnRef) columns.getAttribute(idx);
            column.copyAttributes(oref);
            column.setDataType(dtype);
            columns.setAttribute(idx, column);
         }
         else {
            if(bidx >= 0) {
               columns.addAttribute(bidx, column);
            }
            else {
               columns.addAttribute(column);
            }
         }

         list.set(list.size() - 1, column);
         ginfo.removeGroup(i);
         boolean timeSeries = gref.isTimeSeries();
         gref = new GroupRef(column);
         gref.setTimeSeries(timeSeries);
         // @by stephenwebster, Fix bug1397037101172
         // Reset the date group so it is not lost the next time
         // the Edit Aggregate dialog is loaded.
         gref.setDateGroup(dgroup);

         if(timeSeries && table.getSortInfo() != null &&
            table.getSortInfo().getSort(column) != null)
         {
            table.getSortInfo().removeSort(column);
         }

         ginfo.setGroup(i, gref);
      }

      // remove useless range column
      for(int i = columns.getAttributeCount() - 1; i >= 0; i--) {
         DataRef ref0 = columns.getAttribute(i);
         String name0 = ref0.getName();
         boolean range = ref0 instanceof DataRefWrapper &&
            ((DataRefWrapper) ref0).getDataRef() instanceof  DateRangeRef;

         if(!range) {
            for(int j = 0; j < oaginfo.getGroupCount(); j++) {
               GroupRef gref = oaginfo.getGroup(j);
               DateRangeRef dateRef = getDateRangeRef(gref);

               if(dateRef != null && dateRef.isAutoCreate() &&
                  name0.equals(dateRef.getName()))
               {
                  range = true;
                  break;
               }
            }
         }

         if(range) {
            if(list.contains(ref0)) {
               continue;
            }

            DateRangeRef dateRangeRef = getInnerDateRangeRef(ref0);

            if(dateRangeRef != null && !isAutoRangeColumn(ref0) &&
               columns.containsAttribute(dateRangeRef.getDataRef()))
            {
               continue;
            }

            columns.removeAttribute(i);

            if(table instanceof SnapshotEmbeddedTableAssembly) {
               SnapshotEmbeddedTableAssembly snapshot = (SnapshotEmbeddedTableAssembly) table;
               ColumnSelection def = snapshot.getDefaultColumnSelection();

               if(def.indexOfAttribute(ref0) >= 0) {
                  int idx = def.indexOfAttribute(ref0);
                  def.removeAttribute(idx);
               }
            }
         }
      }

      AssetUtil.validateConditions(columns, table);
      // clear old aggregate info since it my cause new columns to be mistakenly modified
      // aggregate info will be set to the new aggregate info after this call
      table.setAggregateInfo(new AggregateInfo());
      table.setColumnSelection(columns);
   }

   private boolean containsDateRangeRef(List<ColumnRef> refs, DataRef dataRef) {
      return refs.stream().map(ref -> getInnerDateRangeRef(ref))
         .filter(ref -> ref != null)
         .anyMatch(ref -> Tool.equals(ref, getInnerDateRangeRef(dataRef)));
   }

   /**
    * Check the ref is renamed group ref.
    */
   private boolean renamedRef(AggregateInfo ainfo, DateRangeRef ref) {
      String name = ref.getName();

      // name match XXX(xxx), not a user specified data ref
      if(name.contains("(") && name.contains(")")) {
         return false;
      }

      // check the old aggregate info contains the name, if true, means
      // user renamed a old group ref, otherwise, means user first create
      // a group ref on date range ref
      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         DateRangeRef dref = getDateRangeRef(gref);

         if(dref == null) {
            continue;
         }

         if(dref.getName().equals(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get base date range ref.
    */
   private DateRangeRef getDateRangeRef(DataRef ref0) {
      while(ref0 instanceof DataRefWrapper) {
         if(ref0 instanceof DateRangeRef) {
            break;
         }

         ref0 = ((DataRefWrapper) ref0).getDataRef();
      }

      return ref0 instanceof DateRangeRef ? (DateRangeRef) ref0 : null;
   }

   private DateRangeRef getInnerDateRangeRef(DataRef ref0) {
      while(ref0 instanceof DataRefWrapper) {
         if(ref0 instanceof DateRangeRef && !(((DateRangeRef) ref0).getDataRef() instanceof DateRangeRef)) {
            break;
         }

         ref0 = ((DataRefWrapper) ref0).getDataRef();
      }

      return ref0 instanceof DateRangeRef ? (DateRangeRef) ref0 : null;
   }

   /**
    * Get next available name for a column.
    */
   private String getNextName(String base, ColumnSelection columns) {
      int suffix = 1;
      String name = base + "_1";

      while(AssetUtil.findColumnConflictingWithAlias(columns, null, name) != null) {
         name = base + "_" + (suffix++);
      }

      return name;
   }

   @Autowired
   public void setDataRefModelFactoryService(
      DataRefModelFactoryService dataRefModelFactoryService)
   {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   private DataRefModelFactoryService dataRefModelFactoryService;
}