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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetVSBindingModelCommand;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.objects.event.GroupFieldsEvent;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller that processes adhoc filter events for tables and charts.
 */
@Controller
public class ComposerGroupColumnsController {
   /**
    * Creates a new instance of <tt>ComposerAdhocFilterController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public ComposerGroupColumnsController(ViewsheetService viewsheetService,
                                         RuntimeViewsheetRef runtimeViewsheetRef,
                                         PlaceholderService placeholderService,
                                         VSBindingService bfactory,
                                         VSObjectPropertyService vsObjectPropertyService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.vsObjectPropertyService = vsObjectPropertyService;
      this.bfactory = bfactory;
   }

   /**
    * Group rows/columns.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/viewsheet/groupFields")
   public void group(@Payload GroupFieldsEvent event, @LinkUri String linkUri,
                     Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = (VSAssembly) Tool.clone(vs.getAssembly(event.getName()));
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Object cinfo = null;
      String groupName = event.getPrevGroupName();
      String group = event.getGroupName();
      String[] value = event.getLabels();
      List<VSDimensionRef> refs = new ArrayList<>();

      if(assembly instanceof ChartVSAssembly) {
         cinfo = ((ChartVSAssemblyInfo) info).getVSChartInfo();
         VSChartInfo chartInfo = (VSChartInfo) cinfo;
         vs.clearSharedFrames();
         VSChartHandler.clearColorFrame(chartInfo, false, null);

         if(event.isLegend()) {
            refs = Arrays.stream(chartInfo.getAestheticRefs(false))
               .filter(ref -> ((VSDataRef) ref.getDataRef()).getFullName()
                  .equals(event.getColumnName()))
               .filter(ref -> ref.getDataRef() instanceof VSDimensionRef)
               .map(ref -> (VSDimensionRef) ref.getDataRef())
               .collect(Collectors.toList());
         }
         else {
            ChartRef chartRef = null;

            if(chartInfo instanceof RelationChartInfo && event.isAxis()) {
               ChartRef[] fields = (ChartRef[]) ArrayUtils.addAll(chartInfo.getXFields(),
                  chartInfo.getYFields());

               if(fields != null) {
                  Optional<ChartRef> find = Arrays.stream(fields).
                     filter(field -> field != null && Tool.equals(event.getColumnName(),
                        field.getFullName()))
                     .findFirst();

                  if(find.isPresent()) {
                     chartRef = find.get();
                  }
               }
            }

            if(chartRef == null) {
               chartRef = chartInfo.getFieldByName(event.getColumnName(), false);
            }

            chartRef = findDynamicChartRef(event.getColumnName(), chartInfo, chartRef);

            if(chartRef instanceof VSChartDimensionRef) {
               refs.add((VSChartDimensionRef) chartRef);
            }
         }
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         VSDimensionRef ref = (VSDimensionRef) getCellDataRef(rvs, (CrosstabVSAssembly) assembly,
            event.getRow(), event.getCol());
         TableDataPath tpath = getTablePath(rvs, (CrosstabVSAssembly) assembly,
            event.getRow(), event.getCol());
         cinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
         VSCrosstabInfo cross = (VSCrosstabInfo) cinfo;

         if(ref == null && tpath != null && tpath.getType() == TableDataPath.GROUP_HEADER) {
            String path = tpath.getPath()[tpath.getPath().length - 1];
            DataRef[] rows = cross.getRowHeaders();

            for(int i = 0; i < rows.length; i++) {
               VSDimensionRef row = (VSDimensionRef)rows[i];

               if(Tool.equals(row.getFullName(), path)) {
                  ref = row;
               }
            }
         }

         if(ref != null) {
            refs.add(ref);
         }

         TableLens lens = (TableLens) rvs.getViewsheetSandbox().getData(assembly.getAbsoluteName());
         value = getCrosstabValues(value, lens);
      }

      List<VSDimensionRef> oldRefs = (List<VSDimensionRef>) Tool.clone(refs);

      if(refs.stream().anyMatch(dim -> dim.getNamedGroupInfo() instanceof DCNamedGroupInfo)) {
         return;
      }

      for(VSDimensionRef ref : refs) {
         List manualOrder = ref.getManualOrderList();
         boolean manualSort = manualOrder != null && manualOrder.size() > 0;

         // Rename existing group.
         if(value.length == 1 && groupName != null) {
            List cols = ((SNamedGroupInfo) ref.getNamedGroupInfo()).getGroupValue(value[0]);
            ref.getNamedGroupInfo().removeGroup(value[0]);
            ((SNamedGroupInfo) ref.getNamedGroupInfo()).setGroupValue(group, cols);

            int index = manualOrder != null ? manualOrder.indexOf(value[0]) : -1;

            if(manualSort && index != -1) {
               manualOrder.remove(index);
               ref.setManualOrderList(manualOrder);
            }
         }
         else {
            if(manualSort) {
               for(String val: value) {
                  int index = manualOrder.indexOf(val);

                  if(index >= 0) {
                     manualOrder.remove(index);
                  }
               }

               manualOrder.add(group);
               ref.setManualOrderList(manualOrder);
            }

            if(group == null && ((SNamedGroupInfo) ref.getNamedGroupInfo()).getGroupValue(value[0]) != null) {
               ref.getNamedGroupInfo().removeGroup(value[0]);
            }
            else {
               setGroupInfo(ref, group, value);
               ref.setDataType(XSchema.STRING);
               ref.setGroupType("0");
            }
         }

         syncDimensionRef(cinfo, ref);
      }

      if(assembly instanceof CrosstabVSAssembly) {
         final VSCrosstabInfo crosstabInfo = ((CrosstabVSAssembly) assembly).getCrosstabInfo().getVSCrosstabInfo();
         final DataRef[] aggregates = crosstabInfo.getAggregates();

         for(DataRef aggregate : aggregates) {
            if(!(aggregate instanceof VSAggregateRef)) {
               continue;
            }

            Calculator calc = ((VSAggregateRef) aggregate).getCalculator();

            if(calc != null) {
               calc.updateRefs(oldRefs, refs);
            }
         }
      }

      if(cinfo instanceof VSChartInfo) {
         new ChangeChartProcessor().syncNamedGroup((VSChartInfo) cinfo, event.isLegend());
      }

      fixVSAssembliesBinding(vs, event.getName(), oldRefs, refs);
      BindingModel binding = bfactory.createModel(assembly);
      SetVSBindingModelCommand bcommand = new SetVSBindingModelCommand(binding);
      dispatcher.sendCommand(bcommand);
      vsObjectPropertyService.editObjectProperty(
         rvs, info, event.getName(), event.getName(), linkUri, principal, dispatcher, false);
   }

   /**
    * check runtime cols for ref and if it exists try to derive the design time ref from
    * the runtime ref group column name (script)
   */
   private ChartRef findDynamicChartRef(String columnName, VSChartInfo info, ChartRef chartRef) {
      if(chartRef == null) {
         chartRef = info.getFieldByName(columnName, true);

         if(chartRef instanceof VSDimensionRef && ((VSChartDimensionRef) chartRef).isDynamic()) {
            final String val = ((VSDimensionRef) chartRef).getGroupColumnValue();
            final VSDataRef[] fields = info.getFields();

            for(VSDataRef field : fields) {
               if(field instanceof VSDimensionRef && ((VSDimensionRef) field).isDynamic()) {
                  if(Objects.equals(((VSDimensionRef) field).getGroupColumnValue(), val) &&
                     field instanceof ChartRef)
                  {
                     chartRef = (ChartRef) field;
                  }
               }
            }
         }
      }

      return chartRef;
   }

   private void fixVSAssembliesBinding(Viewsheet vs, String groupedAssembly,
                                       List<VSDimensionRef> oldRefs, List<VSDimensionRef> newRefs)
   {
      if(vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof DataVSAssembly) {
            SourceInfo sourceInfo = ((DataVSAssembly) assembly).getSourceInfo();

            if(sourceInfo != null && sourceInfo.getType() == XSourceInfo.VS_ASSEMBLY) {
               String source = VSUtil.getVSAssemblyBinding(sourceInfo.getSource());

               if(StringUtils.equals(groupedAssembly, source)) {
                  fixVSAssembliesBinding((DataVSAssembly) assembly, oldRefs, newRefs);
               }
            }
         }
      }
   }

   private void fixVSAssembliesBinding(DataVSAssembly assembly, List<VSDimensionRef> oldRefs,
                                       List<VSDimensionRef> newRefs)
   {
      for(int i = 0; i < oldRefs.size(); i++) {
         String oldFullName = oldRefs.get(i).getFullName();
         String oldBindRefName = oldFullName;
         String newFullName = newRefs.get(i).getFullName();
         // see DataWrapperTableLens
         String newBindRefName = newFullName.replaceAll("[()/]", "_");

         // ungroup
         if(oldRefs.get(i).isNameGroup() && oldFullName != null) {
            oldBindRefName = oldFullName.replaceAll("[()/]", "_");
         }

         if(assembly instanceof ChartVSAssembly || assembly instanceof CrosstabVSAssembly) {
            DataRef[] allBindings = null;

            if(assembly instanceof ChartVSAssembly) {
               ChartVSAssembly chartVSAssembly = (ChartVSAssembly) assembly;
               VSChartInfo vsChartInfo = chartVSAssembly.getVSChartInfo();
               allBindings = vsChartInfo.getFields();
            }
            else if(assembly instanceof CrosstabVSAssembly) {
               CrosstabVSAssembly crosstabVSAssembly = (CrosstabVSAssembly) assembly;
               VSCrosstabInfo crosstabInfo = crosstabVSAssembly.getVSCrosstabInfo();
               allBindings = (DataRef[]) ArrayUtils.addAll(allBindings, crosstabInfo.getRowHeaders());
            }

            if(allBindings == null) {
               continue;
            }

            List<VSDataRef> findRefs = getVSBindingRefs(allBindings, oldBindRefName);

            for(VSDataRef findRef : findRefs) {
               if(!(findRef instanceof VSDimensionRef)) {
                  continue;
               }

               VSDimensionRef dimensionRef = (VSDimensionRef) findRef;
               dimensionRef.setGroupColumnValue(newBindRefName);
            }
         }
         else if(assembly instanceof TableVSAssembly) {
            TableVSAssembly tableVSAssembly = (TableVSAssembly) assembly;
            TableVSAssemblyInfo tableVSInfo = ((TableVSAssemblyInfo) tableVSAssembly.getInfo());
            ColumnSelection columnSelection = tableVSInfo.getColumnSelection();
            DataRef dataRef = columnSelection.getAttribute(oldBindRefName);

            if(dataRef instanceof ColumnRef &&
               ((ColumnRef) dataRef).getDataRef() instanceof AttributeRef)
            {
               AttributeRef attributeRef = (AttributeRef) ((ColumnRef) dataRef).getDataRef();
               AttributeRef newRef = new AttributeRef(attributeRef.getEntity(), newBindRefName);
               newRef.setCaption(attributeRef.getCaption());
               newRef.setRefType(attributeRef.getRefType());
               ((ColumnRef) dataRef).setDataRef(newRef);
            }
         }
         else if(assembly instanceof CalcTableVSAssembly) {
            CalcTableVSAssembly calcTableVSAssembly = (CalcTableVSAssembly) assembly;
            CalcTableVSAssemblyInfo calcInfo = (CalcTableVSAssemblyInfo) calcTableVSAssembly.getInfo();
            List<CellBindingInfo> cells = calcInfo.getTableLayout().getCellInfos(true);
            List<CellBindingInfo> findCells = getCellBindingInfos(cells, oldBindRefName);

            for(CellBindingInfo cell : findCells) {
               cell.setValue(newBindRefName);
            }
         }
      }
   }

   private List<CellBindingInfo> getCellBindingInfos(List<CellBindingInfo> cells, String name) {
      List<CellBindingInfo> findCells = new ArrayList<>();

      for(CellBindingInfo cell : cells) {
         if(cell == null || cell.getType() != TableCellBinding.BIND_COLUMN) {
            continue;
         }

         if(cell.getValue() == name) {
            findCells.add(cell);
         }
      }

      return cells;
   }

   private List<VSDataRef> getVSBindingRefs(DataRef[] dataRefs, String name) {
      List<VSDataRef> refs = new ArrayList<>();

      for(int i = 0; i < dataRefs.length; i++) {
         DataRef dataRef = dataRefs[i];

         if(!(dataRef instanceof VSDataRef)) {
            continue;
         }

         VSDataRef vsDataRef = (VSDataRef) dataRef;

         if(vsDataRef != null && vsDataRef.getName().equals(name) ||
            vsDataRef.getFullName().equals(name))
         {
            refs.add(vsDataRef);
         }
      }

      return refs;
   }

   /**
    * Check whether the name input for the group is a duplicate.
    *
    * @param event     the model containing group info
    * @param runtimeId the runtime id
    * @param principal the user principal
    *
    * @return whether the name is a duplicate
    */
   @PostMapping("/composer/viewsheet/groupFields/checkDuplicates/**")
   @ResponseBody
   public boolean checkVSTableTrap(
      @RequestBody() GroupFieldsEvent event,
      @RemainingPath String runtimeId,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      String assemblyName = event.getName();
      Object data = box.getData(assemblyName);

      if(!(data instanceof TableLens)) {
         return false;
      }

      TableLens lens = (TableLens) data;
      String groupName = event.getGroupName();

      for(int r = 0; lens.moreRows(r); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            Object obj = lens.getObject(r, c);

            if(Tool.equals(groupName, obj)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Get the ref associated with this cell.
    */
   private DataRef getCellDataRef(RuntimeViewsheet rvs, CrosstabVSAssembly table, int row,
      int col) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String oname = table.getAbsoluteName();
      boolean detail = oname.startsWith(Assembly.DETAIL);

      if(detail) {
         oname = oname.substring(Assembly.DETAIL.length());
      }

      VSTableLens lens = box.getVSTableLens(oname, detail);
      TableDataPath tpath = lens.getTableDataPath(row, col);

      boolean dc = DateComparisonUtil.appliedDateComparison(table.getCrosstabInfo());

      // For dc crosstab, the row and col index is after dc, so should using runtime refs to get
      // right ref, but for group actions, it should using design ref to group, so should return
      // design ref in crosstab.
      if(dc) {
         DataRef ref = VSTableService
            .getCrosstabCellDataRef(table.getVSCrosstabInfo(), tpath, row, col, true);

         VSCrosstabInfo cinfo = table.getVSCrosstabInfo();
         DataRef[] rows = cinfo.getDesignRowHeaders();
         DataRef[] cols = cinfo.getDesignColHeaders();

         for(int r = 0; r < rows.length ; r++) {
            if(Tool.equals(ref.getName(), rows[r].getName())) {
               return rows[r];
            }
         }

         for(int c = 0; c < cols.length; c++) {
            if(Tool.equals(ref.getName(), cols[c].getName())) {
               return cols[c];
            }
         }
      }

      return VSTableService
         .getCrosstabCellDataRef(table.getVSCrosstabInfo(), tpath, row, col, false);
   }

   private TableDataPath getTablePath(RuntimeViewsheet rvs, CrosstabVSAssembly table, int row,
      int col) throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String oname = table.getAbsoluteName();
      boolean detail = oname.startsWith(Assembly.DETAIL);

      if(detail) {
         oname = oname.substring(Assembly.DETAIL.length());
      }

      VSTableLens lens = box.getVSTableLens(oname, detail);
      TableDataPath tpath = lens.getTableDataPath(row, col);

      return tpath;
   }

   /**
    * Get the values of the crosstab cells to be grouped
    * @param cells   list of comma separated coordinates of the selected cells
    * @param lens    the crosstab lens
    * @return
    */
   private String[] getCrosstabValues(String[] cells, TableLens lens) {
      String[] values = new String[cells.length];

      for(int i = 0; i < cells.length; i++) {
         String[] index = cells[i].split(",");
         int row = Integer.parseInt(index[0]);
         int col = Integer.parseInt(index[1]);

         values[i] = lens.getObject(row, col).toString();
      }

      return values;
   }

   /**
    * Copy the group definition to the aggregateInfo.
    */
   private void syncDimensionRef(Object cinfo, DataRef dataRef) {
      AggregateInfo ainfo = null;

      if(cinfo == null) {
         return;
      }

      if(cinfo instanceof VSChartInfo) {
         ainfo = ((VSChartInfo) cinfo).getAggregateInfo();
         ((VSChartInfo) cinfo).clearRuntime();
      }
      else if(cinfo instanceof VSCrosstabInfo) {
         ainfo = ((VSCrosstabInfo) cinfo).getAggregateInfo();
      }

      GroupRef gref = findGroupRef(ainfo, dataRef.getName());

      if(gref != null) {
         if(dataRef instanceof XDimensionRef) {
            gref.setNamedGroupInfo(((XDimensionRef) dataRef).getNamedGroupInfo());
         }
         else {
            gref.setNamedGroupInfo(((GroupRef) dataRef).getNamedGroupInfo());
         }
      }
   }

   /**
    * Update group info in ref
    */
   private void setGroupInfo(VSDimensionRef ref, String group, String[] val) {
      if(group == null) {
         return;
      }

      SNamedGroupInfo groupInfo = (SNamedGroupInfo) ref.getNamedGroupInfo();

      if(groupInfo == null) {
         groupInfo = new SNamedGroupInfo();
         ref.setNamedGroupInfo(groupInfo);
      }

      List<String> value = new ArrayList<>(Arrays.asList(val));
      List<String> value0 = new ArrayList<>();
      List<String> removeGroups = new ArrayList<>();

      for(int i = value.size() - 1; i >= 0; i--) {
         List<String> arr = groupInfo.getGroupValue(value.get(i));
         boolean found = removeGroups.contains(value.get(i));

         if(found || arr != null && arr.size() > 0) {
            if(!found) {
               value0.addAll(arr);
               groupInfo.removeGroup(value.get(i));
               removeGroups.add(value.get(i));
            }

            value.remove(i);
            continue;
         }

         value0.add(value.get(i));
      }

      groupInfo.setGroupValue(group, value0);
   }

   /**
    * Find the data ref from the specified aggregate info.
    */
   private GroupRef findGroupRef(AggregateInfo ainfo, String name) {
      for(int i = 0; ainfo != null && i < ainfo.getGroupCount(); i++) {
         GroupRef group = ainfo.getGroup(i);

         if(group.getDataRef() != null && group.getDataRef().getName().equals(name)) {
            return group;
         }
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final VSBindingService bfactory;
   private final VSObjectPropertyService vsObjectPropertyService;
}
