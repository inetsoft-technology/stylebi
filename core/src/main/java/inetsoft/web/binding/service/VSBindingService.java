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
package inetsoft.web.binding.service;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSBindingHelper;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.SourceInfo;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectBindingEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.model.VSWizardEditModes;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Class that acts as a facade for all registered instances of
 *
 * @since 12.3
 */
@Service
@ClusterProxy
public class VSBindingService {
   @Autowired
   public VSBindingService(VSTrapService trapService,
                           VSTableService vsTableService,
                           GroupingService groupingService,
                           ViewsheetService viewsheetService,
                           List<VSBindingFactory<?, ?>> factories,
                           DataRefModelFactoryService dataRefService,
                           VSObjectModelFactoryService objectModelService,
                           VSWizardTemporaryInfoService wizardTemporaryInfoService,
                           VSSelectionContainerService vsSelectionContainerService,
                           AnalyticAssistant analyticAssistant,
                           VSAssemblyInfoHandler assemblyHandler,
                           VSObjectTreeService vsObjectTreeService,
                           CoreLifecycleService coreLifecycleService)
   {
      this.trapService = trapService;
      this.vsTableService = vsTableService;
      this.dataRefService = dataRefService;
      this.groupingService = groupingService;
      this.viewsheetService = viewsheetService;
      this.objectModelService = objectModelService;
      this.wizardTemporaryInfoService = wizardTemporaryInfoService;
      this.vsSelectionContainerService = vsSelectionContainerService;
      this.analyticAssistant =  analyticAssistant;
      this.assemblyHandler = assemblyHandler;
      this.vsObjectTreeService = vsObjectTreeService;
      this.coreLifecycleService = coreLifecycleService;
      factories.forEach((factory) -> this.registerFactory(factory.getAssemblyClass(), factory));
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void insertChild(@ClusterProxyKey String vsId, InsertSelectionChildEvent event, String linkUri,
                           Principal principal, CommandDispatcher dispatcher, boolean doThrow)
      throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(vsId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      VSAssembly containerAssembly = viewsheet.getAssembly(event.getName());
      List<AssetEntry> bindings = event.getBinding();
      TableTransfer tableData = event.getComponentBinding();

      if((bindings == null || bindings.size() < 1) && tableData == null &&
         event.getColumns() == null)
      {
         return null;
      }

      OutputColumnRefModel[] columnModels = event.getColumns();

      if(columnModels != null) {
         if(columnModels.length > 0) {
            int to = event.getToIndex();

            for(int i = 0; i < columnModels.length; i++) {
               OutputColumnRefModel col = columnModels[i];
               VSAssembly child = getNewAssemblyFromColumn(col, rvs);
               boolean added = addAssembly(rvs, containerAssembly, child, to, linkUri, dispatcher);

               if(added) {
                  to++;
               }
            }
         }

         return null;
      }

      VSAssembly vsassembly;

      if(tableData != null) {
         final VSAssembly tableAssembly = viewsheet.getAssembly(tableData.getAssembly());

         // Table assembly should be from the same viewsheet as the container assembly.
         if(tableAssembly == null ||
            !Objects.equals(tableAssembly.getViewsheet().getAbsoluteName(),
                            containerAssembly.getViewsheet().getAbsoluteName()))
         {
            return null;
         }

         vsassembly = getNewAssemblyFromComponentBinding(
            tableData, viewsheet, event.getX(), event.getY());
      }
      else {
         vsassembly = getNewAssemblyFromBindings(
            bindings, event.getX(), event.getY(), rvs, principal);
      }

      if(vsassembly != null) {
         addAssembly(rvs, containerAssembly, vsassembly, event.getToIndex(), linkUri, dispatcher);
      }

      if(doThrow) {
         List<Exception> exs = WorksheetEngine.ASSET_EXCEPTIONS.get();

         // for mv on-demand (48347)
         if(exs != null) {
            for(Exception ex : exs) {
               throw ex;
            }
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSSelectionTrap(@ClusterProxyKey String runtimeId,
                                                InsertSelectionChildEvent event,
                                                Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      List<AssetEntry> bindings = event.getBinding();
      OutputColumnRefModel[] columns = event.getColumns();
      VSAssembly vsassembly = null;
      VSAssemblyInfo oldAssemblyInfo;
      VSAssemblyInfo newAssemblyInfo;

      if(event.getComponentBinding() != null) {
         vsassembly = getNewAssemblyFromComponentBinding(
            event.getComponentBinding(), viewsheet, event.getX(), event.getY());
      }
      else if(bindings != null) {
         vsassembly = getNewAssemblyFromBindings(
            bindings, event.getX(), event.getY(), rvs, principal);
      }
      else if(columns != null) {
         oldAssemblyInfo = null;

         for(int i = 0; i < columns.length; i++) {
            vsassembly = getNewAssemblyFromColumn(columns[i],  rvs);
            newAssemblyInfo = vsassembly != null ? vsassembly.getVSAssemblyInfo() : null;
            VSTableTrapModel trap = trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);

            if(trap.showTrap()) {
               return trap;
            }
         }
      }

      oldAssemblyInfo = null;
      newAssemblyInfo = vsassembly != null ? vsassembly.getVSAssemblyInfo() : null;

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void changeBinding(@ClusterProxyKey String runtimeId, ChangeVSObjectBindingEvent event,
                             Principal principal, CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(event.getName());
      List<AssetEntry> bindings = event.getBinding();
      TableTransfer tableData = event.getComponentBinding();
      String name;

      if((bindings == null || bindings.size() < 1) && tableData == null) {
         return null;
      }

      if(event.getBinding() != null && event.getBinding().get(0).isVariable()) {
         VSAssembly newAssembly =
            updateVariableBinding(rvs, assembly, event.getBinding(), event, linkUri, null);
         name = newAssembly.getName();
      }
      else if(assembly != null) {
         if(event.getComponentBinding() != null) {
            name = updateBinding(rvs, assembly, tableData, event, dispatcher);
         }
         else {
            name = updateBinding(rvs, assembly, bindings, event, dispatcher, principal);
         }
      }
      else {
         VSAssembly newAssembly;

         if(event.getComponentBinding() != null) {
            newAssembly = getNewAssemblyFromComponentBinding(
               event.getComponentBinding(), viewsheet, event.getX(), event.getY());
         }
         else {
            newAssembly = getNewAssemblyFromBindings(
               bindings, event.getX(), event.getY(), rvs, principal);
         }

         if(newAssembly == null) {
            return null;
         }

         viewsheet.addAssembly(newAssembly);
         coreLifecycleService.addDeleteVSObject(rvs, newAssembly, dispatcher);

         AssemblyEntry assemblyEntry = newAssembly != null ? newAssembly.getAssemblyEntry() : null;
         AssemblyRef[] vrefs = viewsheet.getViewDependings(assemblyEntry);

         if(vrefs != null) {
            for(AssemblyRef aref : vrefs) {
               coreLifecycleService.refreshVSAssembly(rvs, aref.getEntry().getAbsoluteName(),
                                                      dispatcher);
            }
         }

         if(newAssembly instanceof TableDataVSAssembly) {
            ChangedAssemblyList clist = new ChangedAssemblyList();
            ViewsheetSandbox box = rvs.getViewsheetSandbox();
            // reset so association conditions are applied on new table
            box.reset(null, viewsheet.getAssemblies(), clist, true, true, null);
            BaseTableService.loadTableData(
               rvs, newAssembly.getAbsoluteName(), 0, 0, 100, linkUri, dispatcher);
         }

         coreLifecycleService.addDeleteVSObject(rvs, newAssembly, dispatcher);
         assemblyHandler.getGrayedOutFields(rvs, dispatcher);
         VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         dispatcher.sendCommand(treeCommand);
         return null;
      }

      if(name != null) {
         assemblyHandler.getGrayedOutFields(rvs, dispatcher);
         int hint = VSAssembly.INPUT_DATA_CHANGED | VSAssembly.BINDING_CHANGED;
         coreLifecycleService.execute(rvs, name, linkUri, hint, dispatcher);
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSTableTrapModel checkVSTrap(@ClusterProxyKey String runtimeId,
                                       ChangeVSObjectBindingEvent event, String linkUri,
                                       Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) viewsheet.getAssembly(event.getName());
      VSAssemblyInfo oldAssemblyInfo;
      VSAssemblyInfo newAssemblyInfo;

      if(assembly != null) {
         VSAssembly clonedAssembly = (VSAssembly) assembly.clone();
         oldAssemblyInfo = (VSAssemblyInfo) clonedAssembly.getVSAssemblyInfo().clone();
         newAssemblyInfo = clonedAssembly.getVSAssemblyInfo();

         if(event.getComponentBinding() != null) {
            updateBinding(rvs, clonedAssembly, event.getComponentBinding(), event, null);
         }
         else {
            updateBinding(rvs, clonedAssembly, event.getBinding(), event, null, principal);
         }
      }
      else {
         VSAssembly newAssembly;

         if(event.getComponentBinding() != null) {
            newAssembly = getNewAssemblyFromComponentBinding(
               event.getComponentBinding(), viewsheet, event.getX(), event.getY());
         }
         else {
            newAssembly = getNewAssemblyFromBindings(
               event.getBinding(), event.getX(), event.getY(), rvs, principal);
         }

         oldAssemblyInfo = null;
         newAssemblyInfo = newAssembly != null ? newAssembly.getVSAssemblyInfo() : null;
      }

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String getTableTransferDataType(@ClusterProxyKey String runtimeId,
                                          TableTransfer tableData,
                                          Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      VSAssembly bindingAssembly = (VSAssembly) viewsheet.getAssembly(tableData.getAssembly());
      DataRef ref = getDataRefFromComponentBinding(bindingAssembly, tableData);

      return ref != null ? ref.getDataType() : "";
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public BindingPaneData open(@ClusterProxyKey String vsId, String assemblyName, boolean viewer,
                               boolean temporarySheet, Principal principal)
      throws Exception
   {
      String id = createRuntimeSheet(vsId, viewer, temporarySheet,
                                                           principal, assemblyName);
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(id, principal);
      Viewsheet vs = rvs.getViewsheet();
      return new BindingPaneData(id, vs.getViewsheetInfo().isMetadata());
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String commit(@ClusterProxyKey String vsId, String assemblyName,
                        String editMode, String originalMode,
                        Principal principal)
      throws Exception
   {
      return finishEdit(viewsheetService, vsId, assemblyName, editMode, originalMode, principal);
   }

   private String updateBinding(RuntimeViewsheet rvs, VSAssembly assembly,
                                List<AssetEntry> bindings, ChangeVSObjectBindingEvent event,
                                CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();

      AssetEntry binding = bindings.get(0);
      DataRef ref = createDataRef(binding);

      String table = binding.getProperty("assembly");
      String attr = binding.getProperty("attribute");
      String dtype = binding.getProperty("dtype");
      String type = binding.getProperty("type");

      if(info instanceof ScalarBindableVSAssemblyInfo && ref != null) {
         ScalarBindableVSAssemblyInfo assemblyinfo = (ScalarBindableVSAssemblyInfo) info;
         ScalarBindingInfo bindingInfo = new ScalarBindingInfo();
         assemblyinfo.setScalarBindingInfo(bindingInfo);
         int sourceType = inetsoft.uql.asset.SourceInfo.ASSET;

         try {
            sourceType = Integer.parseInt(type);
         }
         catch(NumberFormatException ignore) {
         }

         inetsoft.uql.asset.SourceInfo sinfo = new inetsoft.uql.asset.SourceInfo(sourceType, null, binding.getProperty("table"));
         AnalyticRepository analyticRepository = analyticAssistant.getAnalyticRepository();
         String formula = VSBindingHelper.getModelDefaultFormula(binding, sinfo, rvs, analyticRepository);

         if(formula == null) {
            formula = AssetUtil.getDefaultFormula(ref).getFormulaName();
         }

         bindingInfo.setTableName(table);
         bindingInfo.setColumnValue(attr);
         bindingInfo.setColumn(ref);
         bindingInfo.setAggregateValue(formula);
         bindingInfo.changeColumnType(formula, dtype);
      }
      else if(info instanceof InputVSAssemblyInfo && ref != null) {
         Worksheet ws = viewsheet.getBaseWorksheet();
         Assembly tableAssembly = ws.getAssembly(table);

         // TODO support dragging embedded table cell to list input assembly
         /*
         if(tableAssembly != null && tableAssembly instanceof EmbeddedTableAssembly
            && !(assembly instanceof CheckBoxVSAssembly)
            && !(assembly instanceof RadioButtonVSAssembly))
         {
            InputVSAssemblyInfo inputInfo = (InputVSAssemblyInfo) info;
            int row = 0; // TODO Get row

            inputInfo.setColumnValue(ref.getAttribute());
            inputInfo.setTableName(table);
            inputInfo.setRowValue(row + "");

            if(attr != null) {
               inputInfo.setSelectedObject(attr);
            }
         }
         else
         */
         if(info instanceof ListInputVSAssemblyInfo) {
            ListInputVSAssemblyInfo vinfo = (ListInputVSAssemblyInfo) info;
            int stype = vinfo.getSourceType();
            ListBindingInfo binfo;

            // TODO ListBindingTransferable
//            if(obj is ListBindingTransferable) {
//               ListBindingInfo binfo = obj.getListBindingInfo().clone();
//               vinfo.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
//               vinfo.setListBindingInfo(binfo);
//            }
            if(stype == ListInputVSAssembly.BOUND_SOURCE) {
               binfo = new ListBindingInfo();
               binfo.setLabelColumn(ref);
               binfo.setValueColumn(ref);
               binfo.setTableName(table);
               vinfo.setListBindingInfo(binfo);
            }
            else {
               ListData list = vinfo.getListData() == null ?
                  new ListData(): vinfo.getListData();

               String[] labels = list.getLabels();
               Object[] values = list.getValues();
               ArrayList<String> labelsList = new ArrayList<>(Arrays.asList(labels));
               ArrayList<Object> valuesList = new ArrayList<>(Arrays.asList(values));

               bindings.forEach(bind -> {
                  String caption = null;
                  DataRef dataRef = createDataRef(bind);
                  String attribute = bind.getProperty("attribute");

                  if(dataRef != null && (attribute == null || attribute.isEmpty() ||
                     (dataRef.getRefType() & DataRef.CUBE) != 0))
                  {
                     attribute = dataRef.getName();
                  }

                  if(dataRef instanceof ColumnRef) {
                     ColumnRef cref = (ColumnRef) dataRef;

                     if(cref.getCaption() != null) {
                        caption = cref.getCaption();
                     }
                  }

                  caption = caption == null ? attribute : caption;

                  labelsList.add(caption);
                  valuesList.add(caption);
               });

               list.setLabels(labelsList.toArray(new String[0]));
               list.setValues(valuesList.toArray(new Object[0]));
               list.setDataType(XSchema.STRING);
               vinfo.setListData(list);
               vinfo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
            }
         }
      }
      else if(info instanceof SelectionListVSAssemblyInfo && ref != null) {
         SelectionListVSAssemblyInfo assemblyinfo = (SelectionListVSAssemblyInfo) info;
         assemblyinfo.setDataRef(ref);
         assemblyinfo.setTableName(table);
         assemblyinfo.setTitleValue(getTitle(ref));
      }
      else if(info instanceof TimeSliderVSAssemblyInfo && ref != null) {
         String title = ref.getName();
         TimeSliderVSAssemblyInfo assemblyinfo = (TimeSliderVSAssemblyInfo) info;
         DataRef[] dataRefs = bindings.stream()
            .map(this::createDataRef)
            .toArray(DataRef[]::new);
         TimeInfo tinfo;


         if(dataRefs != null && dataRefs.length > 1) {
            tinfo = new CompositeTimeInfo();
            ((CompositeTimeInfo) tinfo).setDataRefs(dataRefs);
            assemblyinfo.setComposite(true);
         }
         else {
            int reftype = ref.getRefType();
            tinfo = new SingleTimeInfo();
            ((SingleTimeInfo) tinfo).setDataRef(ref);
            assemblyinfo.setComposite(false);

            if((reftype & DataRef.CUBE_DIMENSION) != 0 && !XSchema.isDateType(dtype)) {
               ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MEMBER);
            }
            else if(XSchema.isNumericType(dtype)) {
               // let TimeSliderVSAQuery to set the range size from data
               ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.NUMBER);
            }
            else if(XSchema.TIME.equals(dtype)) {
               ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
            }
            else if(XSchema.isDateType(dtype)) {
               ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MONTH);
            }
            else {
               tinfo = new CompositeTimeInfo();
               ((CompositeTimeInfo) tinfo).setDataRefs(new DataRef[]{ref});
               assemblyinfo.setComposite(true);
            }
         }

         assemblyinfo.setTimeInfo(tinfo);
         assemblyinfo.setTableName(table);
         assemblyinfo.setTitleValue(title);

         if(bindings != null && bindings.size() > 0) {
            String sourceType = bindings.get(0).getProperty("type");

            if(sourceType != null && StringUtils.isNumeric(sourceType)) {
               assemblyinfo.setSourceType(Integer.parseInt(sourceType));
            }
         }

         // in flash, the EditPropertyOverEvent will call this when the binding changes
         ((TimeSliderVSAssembly) assembly).resetSelection();
      }
      else if(info instanceof SelectionTreeVSAssemblyInfo) {
         List<AssetEntry> cols = bindings;

         SelectionTreeVSAssemblyInfo assemblyinfo = (SelectionTreeVSAssemblyInfo) info;
         List<DataRef> levels = new ArrayList<>(Arrays.asList(assemblyinfo.getDataRefs()));

         if(binding.getProperty("DIMENSION_FOLDER") != null) {
            cols = findCubes(binding, principal, viewsheet);
         }

         String newDimension = null;
         ref = createDataRef(cols.get(0));

         // only replace if it's from a cube
         if(cols.size() > 0 && ref != null && (ref.getRefType() & DataRef.CUBE) != 0) {
            String oldDimension = levels.size() > 0 ? getDimension(levels.get(0)) : "";
            newDimension = getDimension(ref);

            // from different dimension, replace it
            if(!Tool.equals(oldDimension, newDimension)) {
               levels = new ArrayList<>();
               assemblyinfo.setTableName(cols.get(0).getProperty("assembly"));
            }
         }

         String newTable = table == null ? cols.get(0).getProperty("assembly") : table;
         boolean dateExist = false;
         boolean changed = false;

         for(int i = 0; i < cols.size(); i++) {
            DataRef colRef = createDataRef(cols.get(i));
            String refTable = cols.get(i).getProperty("assembly");

            if(!newTable.equals(refTable)) {
               if(i == 0) {
                  changed = true;
                  newTable = refTable;
                  levels = new ArrayList<>();
               }
               else {
                  continue;
               }
            }

            // from different dimension, ignore it
            if(newDimension != null && !Tool.equals(newDimension, getDimension(colRef))) {
               continue;
            }

            if(!refTable.equals(assemblyinfo.getTableName())) {
               levels = new ArrayList<>();
               assemblyinfo.setTableName(newTable);
            }

            boolean found = false;

            // ignore if existing
            for(DataRef level : levels) {
               if(isEqualDataRefs(level, colRef)) {
                  found = true;
               }
            }

            if(!found) {
               levels.add(colRef);
               changed = true;
               dateExist = dateExist
                  || (colRef != null && XSchema.isDateType(colRef.getDataType()));
            }
         }

         if(changed) {
            if(assemblyinfo.getMode() == SelectionTreeVSAssemblyInfo.ID) {
               assemblyinfo.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
               assemblyinfo.setParentIDValue(null);
               assemblyinfo.setIDValue(null);
               assemblyinfo.setLabelValue(null);
            }

            if(dateExist) {
               // Confirm
            }

            DataRef[] levs = levels.toArray(new DataRef[levels.size()]);
            boolean valid = VSEventUtil.isValidDataRefs(table, levs);

            if(!valid  && dispatcher != null) {
               MessageCommand command = new MessageCommand();
               command.setType(MessageCommand.Type.ERROR);
               command.setMessage(Catalog.getCatalog().getString(
                  "viewer.viewsheet.editSelectionTree"));
               dispatcher.sendCommand(command);
               return null;
            }

            assemblyinfo.setDataRefs(levs);
         }
      }
      else if(assembly instanceof CalendarVSAssembly && dtype != null &&
         ("timeInstant".equals(dtype) || "date".equals(dtype)) &&
         !table.startsWith(Assembly.CUBE_VS))
      {
         //noinspection ConstantConditions
         CalendarVSAssemblyInfo assemblyinfo = (CalendarVSAssemblyInfo) info;
         assemblyinfo.setDataRef(ref);
         assemblyinfo.setTableName(table);
      }

      return event.getName();
   }

   private String getTitle(DataRef ref) {
      if((ref.getRefType() & DataRef.CUBE) == 0) {
         return VSUtil.trimEntity(ref.getAttribute(), null);
      }

      ref = DataRefWrapper.getBaseDataRef(ref);
      return ref.toView();
   }

   private VSAssembly updateVariableBinding(RuntimeViewsheet rvs, VSAssembly assembly,
                                            List<AssetEntry> bindings,
                                            ChangeVSObjectBindingEvent event,
                                            String linkUri, CommandDispatcher dispatcher)
      throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      AssetEntry binding = bindings.get(0);
      String vname = binding.getName();
      Worksheet ws = viewsheet.getBaseWorksheet();
      VariableAssembly vassembly = (VariableAssembly) ws.getAssembly(vname);

      if(vassembly == null) {
         return null;
      }

      int display = vassembly.getVariable().getDisplayStyle();
      int displayType = AbstractSheet.COMBOBOX_ASSET;

      String type = vassembly.getVariable().getTypeNode().getType();
      type = type == null ? XSchema.STRING : type;

      switch(display) {
      case UserVariable.NONE:
         if(type.equals(XSchema.BOOLEAN)) {
            displayType = AbstractSheet.CHECKBOX_ASSET;
         }
         else if(type.equals(XSchema.STRING) || type.equals(XSchema.CHAR) ||
            type.equals(XSchema.DATE) || type.equals(XSchema.TIME) ||
            type.equals(XSchema.TIME_INSTANT))
         {
            displayType = AbstractSheet.COMBOBOX_ASSET;
         }
         else if(type.equals(XSchema.BYTE) || type.equals(XSchema.INTEGER) ||
            type.equals(XSchema.FLOAT) || type.equals(XSchema.DOUBLE) ||
            type.equals(XSchema.LONG) || type.equals(XSchema.SHORT))
         {
            displayType = AbstractSheet.SPINNER_ASSET;
         }
         break;
      case UserVariable.CHECKBOXES:
      case UserVariable.LIST:
         displayType = AbstractSheet.CHECKBOX_ASSET;
         break;
      case UserVariable.RADIO_BUTTONS:
         displayType = AbstractSheet.RADIOBUTTON_ASSET;
         break;
      case UserVariable.DATE_COMBOBOX:
      case UserVariable.COMBOBOX:
      default:
         displayType = AbstractSheet.COMBOBOX_ASSET;
      }

      VSAssembly nassembly = VSEventUtil.createVSAssembly(rvs, displayType);

      assert nassembly != null;
      nassembly.initDefaultFormat();
      Point offsetPixel = new Point(event.getX(), event.getY());
      nassembly.getInfo().setPixelOffset(offsetPixel);

      if(assembly != null && event.isTab() && dispatcher != null) {
         this.groupingService.groupComponents(rvs, assembly, nassembly, false, linkUri, dispatcher);
         return nassembly;
      }

      Object dValue = vassembly.getVariable().getValueNode() == null ?
         null : vassembly.getVariable().getValueNode().getValue();

      try {
         // use the user input value when default value is expresion or else execute it.
         dValue = executeWSExpressionVariable(dValue, vassembly, box.getAssetQuerySandbox());
      }
      catch(Exception ignore) {
      }

      if(nassembly instanceof InputVSAssembly) {
         InputVSAssembly iassembly = (InputVSAssembly) nassembly;
         iassembly.setTableName("$(" + vname + ")");
         iassembly.setDataType(type);
         iassembly.setVariable(true);

         if(iassembly instanceof ListInputVSAssembly) {
            ListInputVSAssembly lassembly = (ListInputVSAssembly) iassembly;
            AssetVariable avar = vassembly.getVariable();

            // fix bug1284005618690, if the varible has no table, and the
            // choice is from query, execute the varible
            if(avar != null && avar.getTableName() == null &&
               avar.getChoiceQuery() != null)
            {
               avar = (AssetVariable) avar.clone();
               AssetQuerySandbox wbox = box.getAssetQuerySandbox();
               AssetEventUtil.executeVariable(wbox, avar);
            }

            assert avar != null;

            if(avar.getValues() != null && avar.getChoices() != null &&
               avar.getValues().length > 0 && avar.getChoices().length > 0 &&
               avar.getValues().length == avar.getChoices().length)
            {
               String[] labels = new String[avar.getChoices().length];
               Object[] values = new Object[avar.getValues().length];

               for(int i = 0; i < avar.getChoices().length; i++) {
                  labels[i] = Tool.toString(avar.getChoices()[i]);
                  values[i] = Tool.getData(type, avar.getValues()[i]);
               }

               ListData data = new ListData();
               data.setLabels(labels);
               data.setValues(values);
               lassembly.setListData(data);
               lassembly.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
            }
            else if(avar.getValueAttribute() != null) {
               ListBindingInfo binfo = new ListBindingInfo();
               binfo.setTableName(avar.getTableName());
               binfo.setLabelColumn(getVSRef(avar.getLabelAttribute()));
               binfo.setValueColumn(getVSRef(avar.getValueAttribute()));
               lassembly.setListBindingInfo(binfo);
               lassembly.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
            }

            if(lassembly instanceof CheckBoxVSAssembly) {
               CheckBoxVSAssembly cass = (CheckBoxVSAssembly) iassembly;

               if(cass.getListData() == null &&
                  cass.getBindingInfo() == null)
               {
                  ListData data = new ListData();
                  data.setLabels(new String[]{"true", "false"});
                  data.setValues(new Object[]{true, false});
                  cass.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
                  cass.setListData(data);
               }

               cass.setPixelSize(new Dimension(2 * AssetUtil.defw, 2 * AssetUtil.defh));

               if(dValue != null) {
                  ((CheckBoxVSAssemblyInfo) cass.getInfo()).
                     setSelectedObjects(new Object[]{dValue});
               }
            }

            if(lassembly instanceof ComboBoxVSAssembly) {
               if(!type.equals(XSchema.CHAR)) {
                  ((ComboBoxVSAssembly) lassembly).setTextEditable(true);
               }

               if(dValue != null) {
                  ((ComboBoxVSAssemblyInfo) lassembly.getInfo()).
                     setSelectedObject(dValue);
               }
            }

            if(lassembly instanceof RadioButtonVSAssembly) {
               if(dValue != null) {
                  ((RadioButtonVSAssemblyInfo) lassembly.getInfo()).
                     setSelectedObject(dValue);
               }
            }
         }
         else if(iassembly instanceof NumericRangeVSAssembly) {
            if(dValue != null) {
               ((SpinnerVSAssemblyInfo) iassembly.getInfo()).
                  setSelectedObject(dValue);
            }
         }
      }

      return nassembly;
   }

   private Object executeWSExpressionVariable(Object value, VariableAssembly vassembly,
                                              AssetQuerySandbox assetQuerySandbox)
      throws Exception
   {
      if(!(value instanceof ExpressionValue) || assetQuerySandbox == null ||
         assetQuerySandbox.getVariableTable() == null)
      {
         return value;
      }

      Object varValue = assetQuerySandbox.getVariableTable().get(vassembly.getName());

      if(!(varValue instanceof ExpressionValue)) {
         return varValue;
      }

      if(assetQuerySandbox.getScope() != null) {
         VariableTable oldVars = assetQuerySandbox.getScope().getVariableTable() != null ?
            (VariableTable) assetQuerySandbox.getScope().getVariableTable().clone() : null;
         XValueNode node = AssetEventUtil.executeExpressionVariable(assetQuerySandbox.getScope(),
                                                                    assetQuerySandbox.getScriptEnv(), vassembly.getVariable().clone(),
                                                                    AssetEventUtil.getWSAllVariables(assetQuerySandbox.getWorksheet()), new HashMap<>());
         assetQuerySandbox.getScope().setVariableTable(oldVars);

         if(node != null) {
            return node.getValue();
         }
      }

      return value;
   }

   private String updateBinding(RuntimeViewsheet rvs, VSAssembly assembly,
                                TableTransfer tableData, ChangeVSObjectBindingEvent event,
                                CommandDispatcher dispatcher) throws Exception
   {
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssemblyInfo info = assembly == null ? null : assembly.getVSAssemblyInfo();

      VSAssembly bindingAssembly = (VSAssembly) viewsheet.getAssembly(tableData.getAssembly());
      DataRef ref = getDataRefFromComponentBinding(bindingAssembly, tableData);

      if(ref == null) {
         return null;
      }

      if(VSUtil.isVSAssemblyBinding(getBoundTableName(bindingAssembly))) {
         if(dispatcher != null) {
            MessageCommand command = new MessageCommand();
            command.setType(MessageCommand.Type.ERROR);
            command.setMessage(Catalog.getCatalog().getString("composer.vs.cannotBindToVSAssembly"));
            dispatcher.sendCommand(command);
         }

         return null;
      }

      String dtype = ref.getDataType();
      String attr = ref.getName();

      if(info instanceof ScalarBindableVSAssemblyInfo) {
         ScalarBindableVSAssemblyInfo assemblyinfo = (ScalarBindableVSAssemblyInfo) info;
         ScalarBindingInfo bindingInfo = new ScalarBindingInfo();
         assemblyinfo.setScalarBindingInfo(bindingInfo);

         String formula = AssetUtil.getDefaultFormula(ref).getFormulaName();

         bindingInfo.setTableName(getBoundTableName(bindingAssembly));
         bindingInfo.setColumnValue(attr);
         bindingInfo.setColumnType(dtype);
         bindingInfo.setColumn(ref);
         bindingInfo.setAggregateValue(formula);
      }
      else if(info instanceof InputVSAssemblyInfo) {
         Worksheet ws = viewsheet.getBaseWorksheet();
         Assembly tableAssembly = ws.getAssembly(getBoundTableName(bindingAssembly));

         if(tableAssembly instanceof EmbeddedTableAssembly &&
            !(assembly instanceof CheckBoxVSAssembly) &&
            !(assembly instanceof RadioButtonVSAssembly))
         {
            InputVSAssemblyInfo inputInfo = (InputVSAssemblyInfo) info;
            int row = 0;

            inputInfo.setColumnValue(ref.getAttribute());
            inputInfo.setTableName(getBoundTableName(bindingAssembly));
            inputInfo.setRowValue(row + "");

            if(attr != null) {
               inputInfo.setSelectedObject(attr);
            }
         }
         else if(info instanceof ListInputVSAssemblyInfo) {
            ListInputVSAssemblyInfo vinfo = (ListInputVSAssemblyInfo) info;
            int stype = vinfo.getSourceType();
            ListBindingInfo binfo;

            if(stype == ListInputVSAssembly.BOUND_SOURCE) {
               binfo = new ListBindingInfo();
               binfo.setLabelColumn(ref);
               binfo.setValueColumn(ref);
               binfo.setTableName(getBoundTableName(bindingAssembly));
               vinfo.setListBindingInfo(binfo);
            }
            else {
               ListData list = vinfo.getListData() == null ?
                  new ListData(): vinfo.getListData();

               String[] labels = list.getLabels();
               Object[] values = list.getValues();
               ArrayList<String> labelsList = new ArrayList<>(Arrays.asList(labels));
               ArrayList<Object> valuesList = new ArrayList<>(Arrays.asList(values));

               String caption = null;

               if(ref instanceof ColumnRef) {
                  ColumnRef cref = (ColumnRef) ref;

                  if(cref.getCaption() != null) {
                     caption = cref.getCaption();
                  }
               }

               caption = caption == null ? attr : caption;

               labelsList.add(caption);
               valuesList.add(caption);

               list.setLabels(labelsList.toArray(new String[0]));
               list.setValues(valuesList.toArray(new Object[0]));
               list.setDataType(XSchema.STRING);
               vinfo.setListData(list);
               vinfo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
            }
         }
      }
      else if(info instanceof SelectionListVSAssemblyInfo) {
         SelectionListVSAssemblyInfo assemblyinfo = (SelectionListVSAssemblyInfo) info;
         assemblyinfo.setDataRef(ref);
         assemblyinfo.setTableNames(getBoundTables(bindingAssembly));
         assemblyinfo.setTitleValue(getTitle(ref));
      }
      else if(info instanceof TimeSliderVSAssemblyInfo) {
         TimeSliderVSAssemblyInfo assemblyinfo = (TimeSliderVSAssemblyInfo) info;

         TimeInfo tinfo;

         int reftype = ref.getRefType();
         tinfo = new SingleTimeInfo();
         ((SingleTimeInfo) tinfo).setDataRef(ref);
         assemblyinfo.setComposite(false);

         if((reftype & DataRef.CUBE_DIMENSION) != 0 && !XSchema.isDateType(dtype)) {
            ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MEMBER);
         }
         else if(XSchema.isNumericType(dtype)) {
            // let TimeSliderVSAQuery to set the range size from data
            ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.NUMBER);
         }
         else if(XSchema.TIME.equals(dtype)) {
            ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
         }
         else if(XSchema.isDateType(dtype)) {
            ((SingleTimeInfo) tinfo).setRangeTypeValue(TimeInfo.MONTH);
         }
         else {
            tinfo = new CompositeTimeInfo();
            ((CompositeTimeInfo) tinfo).setDataRefs(new DataRef[]{ ref });
            assemblyinfo.setComposite(true);
         }

         assemblyinfo.setTimeInfo(tinfo);
         assemblyinfo.setTableNames(getBoundTables(bindingAssembly));
         assemblyinfo.setTitleValue(getTitle(ref));

         // in flash, the EditPropertyOverEvent will call this when the binding changes
         ((TimeSliderVSAssembly) assembly).resetSelection();
      }
      else if(info instanceof SelectionTreeVSAssemblyInfo) {
         SelectionTreeVSAssemblyInfo assemblyinfo = (SelectionTreeVSAssemblyInfo) info;
         List<DataRef> levels = new ArrayList<>(Arrays.asList(assemblyinfo.getDataRefs()));

         boolean dateExist = false;
         boolean changed = false;
         final String table = bindingAssembly.getTableName();

         if(!table.equals(assemblyinfo.getTableName())) {
            levels = new ArrayList<>();
            assemblyinfo.setTableNames(getBoundTables(bindingAssembly));
         }

         boolean found = false;

         // ignore if existing
         for(DataRef level : levels) {
            if(isEqualDataRefs(level, ref)) {
               found = true;
            }
         }

         if(!found) {
            levels.add(ref);
            changed = true;
            dateExist = (XSchema.isDateType(dtype));
         }

         if(changed) {
            if(assemblyinfo.getMode() == SelectionTreeVSAssemblyInfo.ID) {
               assemblyinfo.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
               assemblyinfo.setParentIDValue(null);
               assemblyinfo.setIDValue(null);
               assemblyinfo.setLabelValue(null);
            }

            if(dateExist) {
               // Confirm
            }

            DataRef[] levs = levels.toArray(new DataRef[levels.size()]);
            boolean valid = VSEventUtil.isValidDataRefs(table, levs);

            if(!valid  && dispatcher != null) {
               MessageCommand command = new MessageCommand();
               command.setType(MessageCommand.Type.ERROR);
               command.setMessage(Catalog.getCatalog().getString(
                  "viewer.viewsheet.editSelectionTree"));
               dispatcher.sendCommand(command);
               return null;
            }

            assemblyinfo.setDataRefs(levs);
         }
      }
      else if(assembly instanceof CalendarVSAssembly &&
         ("timeInstant".equals(dtype) || "date".equals(dtype)))
      {
         CalendarVSAssemblyInfo assemblyinfo = (CalendarVSAssemblyInfo) info;
         assemblyinfo.setDataRef(ref);
         assemblyinfo.setTableNames(getBoundTables(bindingAssembly));
      }

      return event.getName();
   }

   private String getBoundTableName(VSAssembly bindingAssembly) {
      final AssemblyInfo info = bindingAssembly.getInfo();

      if(info instanceof SelectionVSAssemblyInfo) {
         return ((SelectionVSAssemblyInfo) info).getFirstTableName();
      }
      else {
         return bindingAssembly.getTableName();
      }
   }

   /**
    * Check if to data refs for cube dimension member is equal.
    */
   private boolean isEqualDataRefs(DataRef ref1, DataRef ref2) {
      if(ref1.equals(ref2)) {
         return true;
      }

      if((ref1.getRefType() & DataRef.CUBE) != 0 && (ref2.getRefType() & DataRef.CUBE) != 0) {
         if(ref1.getName().equals(ref2.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get dimension name from data ref.
    */
   private String getDimension(DataRef ref) {
      String dim = ref.getEntity();

      if(dim == null || dim.isEmpty()) {
         dim = ref.getName();
         int idx = dim.indexOf(".");

         if(idx >= 0) {
            dim = dim.substring(0, idx);
         }
      }

      return dim;
   }

   private DataRef getVSRef(DataRef ref) {
      DataRef attr = ref;

      if(attr instanceof ColumnRef) {
         attr = VSUtil.getVSColumnRef((ColumnRef) ref);
      }
      else if(ref != null) {
         attr = new AttributeRef(null, ref.getAttribute());
         ((AttributeRef) attr).setDataType(ref.getDataType());
      }

      return attr;
   }

   /**
    * Registers a model factory instance.
    *
    * @param cls the assembly class supported by the factory.
    * @param factory  the factory.
    */
   private void registerFactory(Class<?> cls, VSBindingFactory<?, ?> factory) {
      vsFactories.put(cls, factory);
   }

   /**
    * Creates a DTO model for the specified viewsheet assembly.
    *
    * @param assembly the assembly.
    *
    * @return the DTO model.
    */
   @SuppressWarnings("unchecked")
   public BindingModel createModel(VSAssembly assembly) {
      VSBindingFactory factory = vsFactories.get(assembly.getClass());

      if(factory == null) {
         throw new IllegalArgumentException(
            "No model factory registered for assembly type " +
            assembly.getClass().getName());
      }

      BindingModel model = factory.createModel(assembly);
      model.setSource(createSourceInfo(assembly));
      model.setAvailableFields(createAvailableFields(assembly));

      return model;
   }

   /**
    * Create a availableFields.
    */
   private List<DataRefModel> createAvailableFields(VSAssembly assembly) {
      List<DataRefModel> availableFields = new ArrayList<>();

      // fix Bug #22230.
      // availableFields is used in formula option to support select a second
      // column, but cube don't support second formula, and cube datasource
      // have handreds of fields which size may exceed the max message size
      // of websocket, so don't write the availableFields for cube binding.
      if(VSUtil.getCubeSource(assembly) != null && !VSUtil.isWorksheetCube(assembly)) {
         return availableFields;
      }

      ColumnSelection cols = VSUtil.getBaseColumns(assembly, true);
      XUtil.addDescriptionsFromSource(assembly, cols);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         if(VSUtil.isPreparedCalcField(cols.getAttribute(i))) {
            continue;
         }

         availableFields.add(dataRefService.createDataRefModel(cols.getAttribute(i)));
      }

      // @by davezhang fixBug #20306 add Calculate to availableFields.
      ColumnSelection columnSelection = VSUtil.getColumnsForCalc(assembly);

      for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
         if(VSUtil.isAggregateCalc(columnSelection.getAttribute(i))) {
            CalculateRefModel ref = new CalculateRefModel(
               (CalculateRef) columnSelection.getAttribute(i));
            availableFields.add(ref);
         }
      }

      return availableFields;
   }

   // add bottom border for selections inside container
   private boolean addAssembly(RuntimeViewsheet rvs, VSAssembly container,
                            VSAssembly vsassembly, int toIndex, String linkUri,
                            CommandDispatcher dispatcher) throws Exception
   {
      Viewsheet viewsheet = container.getViewsheet();
      CurrentSelectionVSAssembly containerAssembly = (CurrentSelectionVSAssembly) container;
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo)
         containerAssembly.getInfo();
      AbstractSelectionVSAssembly selection = (AbstractSelectionVSAssembly) vsassembly;
      final String tbl = selection.getTableName();
      final DataRef[] refs = selection.getBindingRefs();

      boolean existing = Arrays.stream(info.getAssemblies())
         .map(name -> viewsheet.getAssembly(name))
         .filter(obj -> {
            AbstractSelectionVSAssembly selection2 = (AbstractSelectionVSAssembly) obj;

            return Objects.equals(tbl, selection2.getTableName()) &&
               Arrays.equals(refs, selection2.getBindingRefs());
         })
         .findFirst().isPresent();

      // no need to have filter on same column
      if(existing) {
         return false;
      }

      viewsheet.addAssembly(vsassembly);
      this.groupingService.groupComponents(rvs, container, vsassembly, true, linkUri, dispatcher);
      int fromIndex = containerAssembly.getAssemblies().length - 1;
      containerAssembly.update(fromIndex, toIndex, false);
      RefreshVSObjectCommand command = new RefreshVSObjectCommand();
      command.setInfo(objectModelService.createModel(containerAssembly, rvs));
      dispatcher.sendCommand(command);

      //Bug #16742 should adjust other assemblies because the new assmbly always show its dropdown
      this.vsSelectionContainerService.applySelection(rvs, vsassembly.getAbsoluteName(), false,
                                                      dispatcher, linkUri);
      return true;
   }

   /**
    * Update assembly.
    *
    * @param model the specified binidng model.
    * @param assembly the specified assembly.
    *
    * @return the assembly.
    */
   public VSAssembly updateAssembly(BindingModel model, VSAssembly assembly) {
      VSBindingFactory factory = vsFactories.get(assembly.getClass());

      if(factory == null) {
         throw new IllegalArgumentException(
            "No model factory registered for assembly type " +
            assembly.getClass().getName());
      }

      VSAssembly oassembly = (VSAssembly) assembly.clone();
      VSAssembly nassembly = factory.updateAssembly(model, assembly);
      updateSourceInfo(model, assembly);
      VSAssemblyInfo nInfo = nassembly == null ? null : nassembly.getVSAssemblyInfo();
      VSAssemblyInfo oInfo = oassembly == null ? null : oassembly.getVSAssemblyInfo();
      return nassembly;
   }

   /**
    * Make a new assembly to add to the viewsheet from binding dropped from a table
    */
   public VSAssembly getNewAssemblyFromComponentBinding(TableTransfer tableData,
                                                        Viewsheet viewsheet, int x, int y)
   {
      VSAssembly bindingAssembly = (VSAssembly) viewsheet.getAssembly(tableData.getAssembly());
      DataRef ref = getDataRefFromComponentBinding(bindingAssembly, tableData);

      if(ref == null) {
         return null;
      }

      if(bindingAssembly instanceof DataVSAssembly &&
         VSUtil.isVSAssemblyBinding(bindingAssembly.getTableName()))
      {
         return null;
      }

      final Viewsheet bindingViewsheet = bindingAssembly.getViewsheet();
      ColumnSelection columns = new ColumnSelection();
      String dtype = ref.getDataType();
      final List<String> tables = getBoundTables(bindingAssembly);
      int assemblyType;

      if(XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)) {
         assemblyType = AbstractSheet.TIME_SLIDER_ASSET;
         columns.addAttribute(ref);
      }
      else {
         assemblyType = AbstractSheet.SELECTION_LIST_ASSET;
         columns.addAttribute(ref);
      }

      String name = AssetUtil.getNextName(bindingViewsheet, assemblyType);
      VSAssembly vsassembly = vsTableService.createSelectionVSAssembly(
         bindingViewsheet, assemblyType, dtype, name, tables, columns);
      vsassembly.initDefaultFormat();
      Point offsetPixel = new Point(x, y);
      vsassembly.getInfo().setPixelOffset(offsetPixel);

      return vsassembly;
   }

   public List<String> getBoundTables(VSAssembly bindingAssembly) {
      if(bindingAssembly instanceof AbstractSelectionVSAssembly) {
         return ((AbstractSelectionVSAssembly) bindingAssembly).getTableNames();
      }
      else {
         return Collections.singletonList(bindingAssembly.getTableName());
      }
   }

   /**
    * Make a new assembly to add to the viewsheet from event bindings
    */
   public VSAssembly getNewAssemblyFromBindings(List<AssetEntry> bindings, int x, int y,
                                                RuntimeViewsheet rvs, Principal principal)
      throws Exception
   {
      int type = 0;
      ColumnSelection columns = new ColumnSelection();
      Viewsheet viewsheet = rvs.getViewsheet();

      AssetEntry binding = bindings.get(0);
      DataRef ref = createDataRef(binding);

      String table = binding.getProperty("assembly");
      String dtype = binding.getProperty("dtype");

      VSAssembly vsassembly = null;

      // Create new.
      if(binding.getType() == AssetEntry.Type.TABLE ||
         binding.getType() == AssetEntry.Type.PHYSICAL_TABLE)
      {
         vsassembly = vsTableService.createTable(rvs, viewsheetService, binding, x, y);
      }
      else if(binding.getProperty("DIMENSION_FOLDER") != null) {
         type = AbstractSheet.SELECTION_TREE_ASSET;

         List<AssetEntry> cubes = findCubes(binding, principal, viewsheet);

         for(AssetEntry cube : cubes) {
            DataRef colRef = createDataRef(cube);
            table = cube.getProperty("assembly");
            columns.addAttribute(colRef);
         }
      }
      else if(bindings.size() > 1) {
         type = AbstractSheet.SELECTION_TREE_ASSET;
         String newTable = table;
         DataRef refs[] = new DataRef[bindings.size()];

         for(int i = 0; i < bindings.size(); i++) {
            DataRef colRef = createDataRef(bindings.get(i));
            String refTable = bindings.get(i).getProperty("assembly");

            if(!newTable.equals(refTable)) {
               if(i == 0) {
                  newTable = refTable;
                  columns = new ColumnSelection();
               }
               else {
                  continue;
               }
            }

            columns.addAttribute(colRef);
            refs[i] = colRef;
         }

         table = newTable;

         if(!VSEventUtil.isValidDataRefs(table, refs)) {
            Catalog catalog = Catalog.getCatalog();
            throw new MessageException(catalog.getString("viewer.viewsheet.createSelectionTreeFailed") +
                    " " + catalog.getString("viewer.viewsheet.editSelectionTree"));
         }
      }
      else if(XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)) {
         type = AbstractSheet.TIME_SLIDER_ASSET;
         columns.addAttribute(ref);
      }
      else {
         type = AbstractSheet.SELECTION_LIST_ASSET;
         columns.addAttribute(ref);
      }

      if(vsassembly == null) {
         String name = AssetUtil.getNextName(viewsheet, type);
         final List<String> tables = Collections.singletonList(table);
         vsassembly = vsTableService.createSelectionVSAssembly(viewsheet, type, dtype,
                                                               name, tables,
                                                               columns);
         vsassembly.initDefaultFormat();
         Point offsetPixel = new Point(x, y);
         vsassembly.getInfo().setPixelOffset(offsetPixel);
      }

      return vsassembly;
   }

   /**
    * Make a new assembly to add to the viewsheet from event column.
   */
   public VSAssembly getNewAssemblyFromColumn(OutputColumnRefModel col, RuntimeViewsheet rvs) {
      Viewsheet viewsheet = rvs.getViewsheet();

      if(col == null) {
         return null;
      }

      ColumnSelection columns = new ColumnSelection();
      String caption = col.getProperties() == null ? null : col.getProperties().get("caption");
      AttributeRef aRef = new AttributeRef(col.getEntity(), col.getAttribute());
      aRef.setCaption(caption);
      aRef.setRefType(col.getRefType());
      ColumnRef cRef = new ColumnRef(aRef);
      String dtype = col.getDataType();
      cRef.setDataType(dtype);
      columns.addAttribute(cRef);
      final int type;

      if(XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)) {
         type = AbstractSheet.TIME_SLIDER_ASSET;
      }
      else {
         type = AbstractSheet.SELECTION_LIST_ASSET;
      }

      final List<String> tables = Collections.singletonList(col.getTable());
      String name = AssetUtil.getNextName(viewsheet, type);
      VSAssembly vsassembly = vsTableService.createSelectionVSAssembly(viewsheet, type, dtype, name,
                                                                       tables, columns);
      vsassembly.initDefaultFormat();
      Point offsetPixel = new Point(0, 0);
      vsassembly.getInfo().setPixelOffset(offsetPixel);

      return vsassembly;
   }


   public DataRef getDataRefFromComponentBinding(VSAssembly assembly, TableTransfer tableData) {
      int index = tableData.getDragIndex();

      if(assembly instanceof TableVSAssembly) {
         ColumnSelection tableColumns = ((TableVSAssembly) assembly).getColumnSelection();
         index = ComposerVSTableController.getActualColIndex(tableColumns, index);

         return index >= tableColumns.getAttributeCount() ? null :
            tableColumns.getAttribute(index);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         ColumnSelection columns = getColumnSelection((CrosstabVSAssembly) assembly);
         String dragType = tableData.getDragType();
         DataRef[] refs = null;

         if("rows".equals(dragType)) {
            refs = cinfo.getRowHeaders();
         }
         else if("cols".equals(dragType)) {
            refs = cinfo.getColHeaders();
         }
         else if("aggregates".equals(dragType)) {
            refs = cinfo.getAggregates();
         }

         DataRef ref = refs != null && index < refs.length ? refs[index] : null;
         return ref != null ? columns.getAttribute(ref.getName()) : null;
      }
      else if(assembly instanceof SelectionVSAssembly) {
         DataRef[] refs = ((SelectionVSAssembly) assembly).getDataRefs();

         if(refs != null && refs.length > index) {
            return refs[index];
         }
      }

      return null;
   }

   /**
    * Get dimension name from data ref.
    */
   public List<AssetEntry> findCubes(AssetEntry parent, Principal principal, Viewsheet viewsheet)
      throws Exception
   {
      @SuppressWarnings("unchecked")
      List<AssetTreeModel.Node> cubes = AssetEventUtil.getCubes(principal, null, viewsheet);
      AssetTreeModel.Node[] children = getAssetTreeNode(parent.getPath(), cubes);
      List<AssetEntry> refs = new ArrayList<>();

      for(AssetTreeModel.Node cube : children) {
         refs.add(cube.getEntry());
      }

      return refs;
   }

   /**
    * Create a dataref by assetentry.
    */
   public DataRef createDataRef(AssetEntry entry) {
      if(!entry.isColumn()) {
         return null;
      }

      String entity = entry.getProperty("entity");
      String attr = entry.getProperty("attribute");
      String refType = entry.getProperty("refType");
      String caption = entry.getProperty("caption");
      String dtype = entry.getProperty("dtype");
      AttributeRef ref = new AttributeRef(entity, attr);

      if(refType != null) {
         ref.setRefType(Integer.parseInt(refType));
      }

      ref.setCaption(caption);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(dtype);

      return col;
   }

   /**
    * Create a SourceInfo.
    */
   private SourceInfo createSourceInfo(VSAssembly assembly) {
      DataVSAssembly dassembly = assembly instanceof DataVSAssembly ?
         (DataVSAssembly) assembly : null;
      inetsoft.uql.asset.SourceInfo sinfo = dassembly != null ?
         dassembly.getSourceInfo() : null;
      SourceInfo nsource = sinfo != null && !sinfo.isEmpty() ?
         new SourceInfo(sinfo) : null;

      if(sinfo != null) {
         try {
            String prefix = sinfo.getPrefix();
            String source = sinfo.getSource();
            XCube cube = AssetUtil.getCube(prefix, source);

            if(cube != null && nsource != null) {
               boolean sqlServer = "SQLServer".equals(cube.getType()) ? true : false;
               nsource.setSqlServer(sqlServer);
            }
         }
         catch(Exception ignore) {
         }
      }

      return nsource;
   }

   /**
    * Update SourceInfo.
    */
   private void updateSourceInfo(BindingModel model, VSAssembly assembly) {
      DataVSAssembly dassembly = assembly instanceof DataVSAssembly ?
         (DataVSAssembly) assembly : null;
      inetsoft.uql.asset.SourceInfo osinfo = dassembly != null ? dassembly.getSourceInfo() : null;
      SourceInfo nsinfo = model.getSource();

      if(dassembly != null && nsinfo != null) {
         dassembly.setSourceInfo(nsinfo.toSourceAttr(osinfo));
      }
   }

   /**
    * According to the path and AssetTreeModel.Node list, get the AssetTreeNode you need.
    * @param ppath is parent path
    * @param cubes is needs AssetEntry.
    */
   private AssetTreeModel.Node[] getAssetTreeNode(String ppath, List<AssetTreeModel.Node> cubes) {
      AssetTreeModel.Node[] children = null;

      for(AssetTreeModel.Node cube : cubes) {
         if(ppath.startsWith(cube.getEntry().getPath())) {
            children = cube.getNodes();
         }
      }

      assert children != null;
      String[] path = ppath.split("/");

      for(int i = 3; i <= path.length; i++) {
         String paths = "";

         for(int j = 0; j < i; j++) {
            if(j == 0) {
               paths = path[0];
            }
            else {
               paths += "/" + path[j];
            }
         }

         for(int j = 0; j < children.length; j++) {
            AssetTreeModel.Node cube = children[j];

            if(cube.getEntry().getPath().equals(paths)) {
               children = cube.getNodes();
            }
         }
      }

      return children;
   }

   private ColumnSelection getColumnSelection(BindableVSAssembly assembly) {
      Viewsheet vs = assembly == null ? null : assembly.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(ws == null) {
         return new ColumnSelection();
      }

      return VSUtil.getColumnsForVSAssembly(vs, assembly, false);
   }

   /**
    * Create new runtime viewsheet base the target rvs.
    * @param  vsId           the base target rvs id.
    * @param  viewer         true if edit from viewer, else not.
    * @param  temporarySheet true if the edit sheet is a temporary sheet, else not.
    * @param  principal      current user.
    * @return                the runtime id of the new create rvs.
    */
   public String createRuntimeSheet(String vsId, boolean viewer,
                                    boolean temporarySheet, Principal principal, String assembly)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      AssetEntry entry = rvs.getEntry();
      Viewsheet vs = (Viewsheet) rvs.getViewsheet().cloneForBindingEditor();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      VariableTable variables = box.getVariableTable();

      box.cancel();

      if(variables != null) {
         variables = (VariableTable) variables.clone();
      }

      box.lockWrite();
      String id;
      RuntimeViewsheet nrvs;

      try {
         id = rvs.getBindingID();

         // check if it's still valid
         try {
            if(id != null && engine.getViewsheet(id, principal) == null) {
               id = null;
            }
         }
         catch(ExpiredSheetException ex) {
            id = null;
         }

         if(id == null) {
            if(!temporarySheet) {
               id = engine.openViewsheet(entry, principal, viewer);
            }
            else {
               id = engine.openTemporaryViewsheet(entry, principal, null);
            }
         }

         nrvs = engine.getViewsheet(id, principal);
         // if base vs renamed, the rvs get by BindingID may not have the newest entry.
         nrvs.setEntry(entry);
         nrvs.setBinding(true);
         rvs.setBindingID(id);
      }
      finally {
         box.unlockWrite();
      }

      nrvs.setOriginalID(vsId);
      nrvs.setViewsheet(vs);
      nrvs.setVSTemporaryInfo(wizardTemporaryInfoService.getVSTemporaryInfo(rvs));

      AssetQuerySandbox wbox = nrvs.getViewsheetSandbox().getAssetQuerySandbox();

      if(wbox != null) {
         wbox.refreshVariableTable(variables);
      }

      ViewsheetSandbox nbox = nrvs.getViewsheetSandbox();

      if(nbox != null) {
         nbox.processOnInit();
         nbox.reset(null, vs.getAssemblies(), new ChangedAssemblyList(),
            true, true, null);
      }

      return id;
   }

   /**
    * Return the original runtimeid.
    */
   private String getOriginalRuntimeId(String rid, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(rid, principal);
      return rvs != null && rvs.getOriginalID() != null ? rvs.getOriginalID() : rid;
   }

   /**
    * Finish editing in binding editor or vs wizard, update the original runtime viewsheet.
    */
   public String finishEdit(ViewsheetService engine, String nid, String assemblyName,
                            String editMode, String originalMode, Principal principal)
      throws Exception
   {
      RuntimeViewsheet nrvs = engine.getViewsheet(nid, principal);
      String oid = getOriginalRuntimeId(nid, principal);
      RuntimeViewsheet orvs = oid == null ? nrvs : engine.getViewsheet(oid, principal);
      orvs.getViewsheetSandbox().getVariableTable().addAll(nrvs.getViewsheetSandbox().getVariableTable());
      updateVSAssemblyBoundAssemblies(nrvs, orvs, assemblyName);
      Viewsheet nvs = nrvs.getViewsheet().clone();
      VSAssembly assembly = nvs.getAssembly(assemblyName);
      AssemblyInfo info = assembly.getInfo();
      // In any case to clear wizard object editing flag.
      assembly.setWizardEditing(false);

      if(info instanceof ChartVSAssemblyInfo) {
         ChartVSAssemblyInfo chartInfo = (ChartVSAssemblyInfo) info;
         chartInfo.setMaxSize(null);
         chartInfo.setSummarySortCol(-1);
         chartInfo.setSummarySortVal(0);
      }

      if(VSUtil.isVSAssemblyBinding(assembly) && assembly instanceof CalcTableVSAssembly) {
         String source = VSUtil.getVSAssemblyBinding(assembly.getTableName());
         nrvs.getViewsheetSandbox().resetDataMap(source);
      }

      if(info instanceof DataVSAssemblyInfo && !VSWizardEditModes.VIEWSHEET_PANE.equals(editMode)) {
         //once in binding, click finish, the editedByWizard is false
         ((DataVSAssemblyInfo) info).setEditedByWizard(false);
      }

      orvs.setViewsheet(nvs);
      orvs.setBindingID(null);
      orvs.getViewsheet().setMaxMode(false);

      if(!VSWizardEditModes.WIZARD_DASHBOARD.equals(originalMode)) {
         orvs.addCheckpoint(nvs.prepareCheckpoint());
      }

      engine.closeViewsheet(nid, principal);

      return oid;
   }

   /**
    *  Whether Source table is hidden(ws table).
    * @param rvs
    * @param assembly
    * @return
    */
   public boolean isSourceVisible(RuntimeViewsheet rvs, VSAssembly assembly) {
      if(rvs == null || assembly == null) {
         return true;
      }

      Worksheet ws = null;

      if(rvs.getRuntimeWorksheet() != null) {
         ws = rvs.getRuntimeWorksheet().getWorksheet();
      }

      String source = null;

      if(assembly instanceof DataVSAssembly) {
         inetsoft.uql.asset.SourceInfo sourceInfo = ((DataVSAssembly) assembly).getSourceInfo();

         if(sourceInfo != null && sourceInfo.getType() == XSourceInfo.ASSET) {
            source = sourceInfo.getSource();
         }
      }

      if(source != null && ws != null) {
         Assembly wsAssembly = ws.getAssembly(source);

         if(wsAssembly instanceof AbstractTableAssembly) {
            return ((AbstractTableAssembly) wsAssembly).isVisibleTable();
         }
      }

      return true;
   }

   /**
    * update the original rvs by the new rvs.
    * @param nrvs  the new rvs.
    * @param orvs  the old rvs.
    * @param assemblyName abosulate name of the current editing assembly.
    */
   private void updateVSAssemblyBoundAssemblies(RuntimeViewsheet nrvs,
                                               RuntimeViewsheet orvs,
                                               String assemblyName)
   {
      Viewsheet vs = nrvs.getViewsheet();
      Assembly[] assemblies = vs.getAssemblies();

      if(assemblies == null) {
         return;
      }

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof DataVSAssembly)) {
            continue;
         }

         DataVSAssembly dataVSAssembly = (DataVSAssembly) assembly;
         String dataVSAssemblyName = dataVSAssembly.getName();
         String tableName = dataVSAssembly.getTableName();

         if(!VSUtil.isVSAssemblyBinding(tableName) ||
            !assemblyName.equals(VSUtil.getVSAssemblyBinding(tableName)))
         {
            continue;
         }

         ColumnSelection cols = VSUtil.getColumnsForVSAssemblyBinding(nrvs, tableName);
         // get the vs assembly from the original rvs so that the binding refs are
         // still there
         DataVSAssembly oldDataVSAssembly =
            (DataVSAssembly) orvs.getViewsheet().getAssembly(dataVSAssemblyName);
         DataRef[] refs = oldDataVSAssembly.getBindingRefs();
         boolean changed = false;

         for(DataRef ref : refs) {
            // check if the old bindings still apply given the new column selection
            if(!cols.containsAttribute(AssetUtil.getColumn(ref))) {
               dataVSAssembly.removeBindingCol(ref.getName());
               changed = true;
            }
         }

         if(!changed) {
            continue;
         }

         try {
            nrvs.getViewsheetSandbox().updateAssembly(dataVSAssemblyName);
         }
         catch(Exception e) {
            LOG.warn("Failed to update assembly: {}", dataVSAssemblyName, e);
         }

         if(dataVSAssembly instanceof TableDataVSAssembly) {
            updateVSAssemblyBoundAssemblies(nrvs, orvs,
                                            dataVSAssemblyName);
         }
      }
   }

   private final Map<Class<?>, VSBindingFactory> vsFactories = new HashMap<>();

   private final VSTrapService trapService;
   private final VSTableService vsTableService;
   private final GroupingService groupingService;
   private final ViewsheetService viewsheetService;
   private final DataRefModelFactoryService dataRefService;
   private final VSObjectModelFactoryService objectModelService;
   private final VSWizardTemporaryInfoService wizardTemporaryInfoService;
   private final VSSelectionContainerService vsSelectionContainerService;
   private final AnalyticAssistant analyticAssistant;
   private final VSAssemblyInfoHandler assemblyHandler;
   private final VSObjectTreeService vsObjectTreeService;
   private final CoreLifecycleService coreLifecycleService;

   private static final Logger LOG = LoggerFactory.getLogger(VSBindingService.class);
}
