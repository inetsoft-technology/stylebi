/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.AnalyticAssistant;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.dnd.TableTransfer;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSBindingHelper;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.model.vs.VSTableTrapModel;
import inetsoft.web.composer.vs.VSObjectTreeNode;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.command.PopulateVSObjectTreeCommand;
import inetsoft.web.composer.vs.objects.event.ChangeVSObjectBindingEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.event.InsertSelectionChildEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * Controller that provides a REST endpoint for object actions.
 */
@Controller
public class ComposerBindingController {
   /**
    * Creates a new instance of <tt>ComposerBindingController</tt>.
    *  @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param placeholderService  general viewsheet actions service.
    * @param groupingService     service for grouping objects.
    */
   @Autowired
   public ComposerBindingController(RuntimeViewsheetRef runtimeViewsheetRef,
                                    PlaceholderService placeholderService,
                                    GroupingService groupingService,
                                    ViewsheetService engine,
                                    VSObjectTreeService vsObjectTreeService,
                                    VSTrapService trapService,
                                    VSAssemblyInfoHandler assemblyHandler,
                                    VSBindingService bindingService,
                                    AnalyticAssistant analyticAssistant)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.groupingService = groupingService;
      this.engine = engine;
      this.vsObjectTreeService = vsObjectTreeService;
      this.trapService = trapService;
      this.assemblyHandler = assemblyHandler;
      this.bindingService = bindingService;
      this.analyticAssistant = analyticAssistant;
   }

   /**
    * Move object in the composer.
    *
    * @param event             the event parameters.
    * @param principal         a principal identifying the current user.
    *
    * @throws Exception if unable to get/edit runtime viewsheet
    */
   @Undoable
   @LoadingMask
   @HandleAssetExceptions
   @MessageMapping("composer/viewsheet/objects/changeBinding")
   public void changeBinding(@Payload ChangeVSObjectBindingEvent event, Principal principal,
                             CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      VSAssembly assembly = viewsheet.getAssembly(event.getName());
      List<AssetEntry> bindings = event.getBinding();
      TableTransfer tableData = event.getComponentBinding();
      String name;

      if((bindings == null || bindings.size() < 1) && tableData == null) {
         return;
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
            newAssembly = bindingService.getNewAssemblyFromComponentBinding(
               event.getComponentBinding(), viewsheet, event.getX(), event.getY());
         }
         else {
            newAssembly = bindingService.getNewAssemblyFromBindings(
               bindings, event.getX(), event.getY(), rvs, principal);
         }

         if(newAssembly == null) {
            return;
         }

         viewsheet.addAssembly(newAssembly);
         placeholderService.addDeleteVSObject(rvs, newAssembly, dispatcher);

         AssemblyEntry assemblyEntry = newAssembly != null ? newAssembly.getAssemblyEntry() : null;
         AssemblyRef[] vrefs = viewsheet.getViewDependings(assemblyEntry);

         if(vrefs != null) {
            for(AssemblyRef aref : vrefs) {
               placeholderService.refreshVSAssembly(rvs, aref.getEntry().getAbsoluteName(),
                                                    dispatcher);
            }
         }

         if(newAssembly instanceof TableDataVSAssembly) {
            ChangedAssemblyList clist = new ChangedAssemblyList();
            ViewsheetSandbox box = rvs.getViewsheetSandbox();
            // reset so association conditions are applied on new table
            box.reset(null, viewsheet.getAssemblies(), clist, true, true, null);
            BaseTableController.loadTableData(
               rvs, newAssembly.getAbsoluteName(), 0, 0, 100, linkUri, dispatcher);
         }

         placeholderService.addDeleteVSObject(rvs, newAssembly, dispatcher);
         assemblyHandler.getGrayedOutFields(rvs, dispatcher);
         VSObjectTreeNode tree = vsObjectTreeService.getObjectTree(rvs);
         PopulateVSObjectTreeCommand treeCommand = new PopulateVSObjectTreeCommand(tree);
         dispatcher.sendCommand(treeCommand);
         return;
      }

      if(name != null) {
         assemblyHandler.getGrayedOutFields(rvs, dispatcher);
         int hint = VSAssembly.INPUT_DATA_CHANGED | VSAssembly.BINDING_CHANGED;
         placeholderService.execute(rvs, name, linkUri, hint, dispatcher);
      }
   }

   /**
    * Insert a child into a selection container between two other selection children
    * @param assemblyName  the name of the selection container
    * @param event         the InsertSelectionChildEvent object
    * @param principal     the principal
    * @param dispatcher    the command dispatcher
    * @throws Exception    if failed to insert the child
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/selectionContainer/insertChild/{name}")
   public void insertChild(@DestinationVariable("name") String assemblyName,
                              @Payload InsertSelectionChildEvent event,
                              @LinkUri String linkUri,
                              Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      this.bindingService.insertChild(event, linkUri, principal, dispatcher);

      List<Exception> exs = WorksheetEngine.ASSET_EXCEPTIONS.get();

      // for mv on-demand (48347)
      if(exs != null) {
         for(Exception ex : exs) {
            throw ex;
         }
      }
   }

   /**
    * Check whether the changing the data bound to the assembly will cause a trap.
    *
    * @param event     the proposed binding change
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @SuppressWarnings("UnusedReturnValue")
   @PostMapping("/api/composer/viewsheet/objects/checkTrap/**")
   @ResponseBody
   public VSTableTrapModel checkVSTrap(@RequestBody ChangeVSObjectBindingEvent event,
                                       @RemainingPath String runtimeId,
                                       @LinkUri String linkUri,
                                       Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
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
            newAssembly = bindingService.getNewAssemblyFromComponentBinding(
               event.getComponentBinding(), viewsheet, event.getX(), event.getY());
         }
         else {
            newAssembly = bindingService.getNewAssemblyFromBindings(
               event.getBinding(), event.getX(), event.getY(), rvs, principal);
         }

         oldAssemblyInfo = null;
         newAssemblyInfo = newAssembly != null ? newAssembly.getVSAssemblyInfo() : null;
      }

      return trapService.checkTrap(rvs, oldAssemblyInfo, newAssemblyInfo);
   }

   /**
    * Check whether the inserting a new child into a selection container will cause a trap.
    *
    * @param event     the proposed binding change
    * @param principal the user principal
    *
    * @return the table trap model stating whether or not here is a trap.
    */
   @SuppressWarnings("UnusedReturnValue")
   @PostMapping("/api/composer/viewsheet/objects/checkSelectionTrap/**")
   @ResponseBody
   public VSTableTrapModel checkVSSelectionTrap(@RequestBody InsertSelectionChildEvent event,
                                                @RemainingPath String runtimeId,
                                                @LinkUri String linkUri,
                                                Principal principal)
      throws Exception
   {
      return this.bindingService.checkVSSelectionTrap(event, runtimeId, principal);
   }

   /**
    * Return the data type of the table column being dropped
    *
    * @param tableData the table column data dropped
    * @param principal the user principal
    *
    * @return the data type of the TableTransfer column
    */
   @PostMapping("/api/composer/viewsheet/objects/getTableTransferDataType/**")
   @ResponseBody
   public String getTableTransferDataType(@RequestBody TableTransfer tableData,
                                    @RemainingPath String runtimeId,
                                    Principal principal)
      throws Exception {
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();

      VSAssembly bindingAssembly = (VSAssembly) viewsheet.getAssembly(tableData.getAssembly());
      DataRef ref = bindingService.getDataRefFromComponentBinding(bindingAssembly, tableData);

      return ref != null ? ref.getDataType() : "";
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
      DataRef ref = bindingService.createDataRef(binding);

      String table = binding.getProperty("assembly");
      String attr = binding.getProperty("attribute");
      String dtype = binding.getProperty("dtype");
      String type = binding.getProperty("type");

      if(info instanceof ScalarBindableVSAssemblyInfo && ref != null) {
         ScalarBindableVSAssemblyInfo assemblyinfo = (ScalarBindableVSAssemblyInfo) info;
         ScalarBindingInfo bindingInfo = new ScalarBindingInfo();
         assemblyinfo.setScalarBindingInfo(bindingInfo);
         int sourceType = SourceInfo.ASSET;

         try {
            sourceType = Integer.parseInt(type);
         }
         catch(NumberFormatException ignore) {
         }

         SourceInfo sinfo = new SourceInfo(sourceType, null, binding.getProperty("table"));
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
                  DataRef dataRef = bindingService.createDataRef(bind);
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
                                      .map(bindingService::createDataRef)
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
            cols = bindingService.findCubes(binding, principal, viewsheet);
         }

         String newDimension = null;
         ref = bindingService.createDataRef(cols.get(0));

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
            DataRef colRef = bindingService.createDataRef(cols.get(i));
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
              ("timeInstant".equals(dtype) || "date".equals(dtype)))
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
      DataRef ref = bindingService.getDataRefFromComponentBinding(bindingAssembly, tableData);

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
         assemblyinfo.setTableNames(bindingService.getBoundTables(bindingAssembly));
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
         assemblyinfo.setTableNames(bindingService.getBoundTables(bindingAssembly));
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
            assemblyinfo.setTableNames(bindingService.getBoundTables(bindingAssembly));
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
         assemblyinfo.setTableNames(bindingService.getBoundTables(bindingAssembly));
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

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final GroupingService groupingService;
   private final PlaceholderService placeholderService;
   private final ViewsheetService engine;
   private final VSObjectTreeService vsObjectTreeService;
   private final VSTrapService trapService;
   private final VSAssemblyInfoHandler assemblyHandler;
   private final VSBindingService bindingService;
   private final AnalyticAssistant analyticAssistant;
}
