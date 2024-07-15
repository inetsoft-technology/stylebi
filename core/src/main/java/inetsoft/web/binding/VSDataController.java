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
package inetsoft.web.binding;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.internal.ManualOrderComparer;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.event.GetAvailableValuesEvent;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.binding.model.ValueLabelListModel;
import inetsoft.web.binding.model.ValueLabelModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class VSDataController {
   /*
   * Injection variable in constructor
   */
   @Autowired
   public VSDataController(
      ChartRefModelFactoryService chartRefService,
      VSChartDataHandler chartDataHandler,
      ViewsheetService viewsheetService) {
      this.chartRefService = chartRefService;
      this.chartDataHandler = chartDataHandler;
      this.viewsheetService = viewsheetService;
   }

   /**
    * Converts elements of empty strings in the the manual order list collection to null.
    * @param list the manual order list.
    * @return the manual order after successful conversion.
    */
   public static List fixNull(List list) {
      if(list == null || list.isEmpty()) {
         return list;
      }

      for(int i = 0 ; i <list.size(); i++) {
         list.set(i, "".equals(list.get(i)) ? null : list.get(i));
      }

      return list;
   }

   /**
    * Get all available attributes.
    */
   @RequestMapping(value = "/api/vsdata/availableValues", method = RequestMethod.PUT)
   public ValueLabelListModel getAvailableValues(@RequestParam("vsId") String vsId,
                                                 @RequestParam("assemblyName") String assemblyName,
                                                 @RequestParam(value = "row", required = false) Integer row,
                                                 @RequestParam(value = "col", required = false) Integer col,
                                                 @RequestParam(value = "dateLevel", required = false) Integer dateLevel,
                                                 @RequestBody GetAvailableValuesEvent event, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly assembly = viewsheet.getAssembly(assemblyName);
      DataRefModel dimension = event.dimension();
      VSDimensionRef dim = null;
      List<VariableAssemblyModelInfo> varInfos = event.variables();
      VariableTable vars = rvs.getViewsheetSandbox().getVariableTable();
      VariableTable collectedVars = VariableAssemblyModelInfo.getVariableTable(varInfos);

      if(collectedVars != null) {
         vars.addAll(collectedVars);
      }

      if(assembly instanceof ChartVSAssembly) {
         ChartVSAssembly chart = (ChartVSAssembly) viewsheet.getAssembly(assemblyName);
         VSChartInfo cinfo = chart.getVSChartInfo();
         ChartDimensionRefModel chartDim = (ChartDimensionRefModel) dimension;
         dim = (VSDimensionRef) chartRefService.pasteChartRef(cinfo, chartDim);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo crossInfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         DataRef[] rows = crossInfo.getRowHeaders();
         DataRef[] cols = crossInfo.getColHeaders();
         VSDimensionRef rowRef = getVSDimensionRef(rows, dimension);
         VSDimensionRef colRef = getVSDimensionRef(cols, dimension);
         VSDimensionRef odim = rowRef != null ? rowRef : colRef;
         dim = (VSDimensionRef) dimension.createDataRef();

         //Update Group Info of crosstab data ref.
         if(odim != null) {
            dim.setNamedGroupInfo(
               (XNamedGroupInfo) Tool.clone(odim.getNamedGroupInfo()));
            dim.setGroupType(odim.getGroupType());
            dim.setDataRef((DataRef) Tool.clone(odim.getDataRef()));
         }
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         return getFreehandTableValues((CalcTableVSAssembly) assembly, dimension, row, col,
                                       dateLevel, rvs);
      }

      if(dim.getNamedGroupInfo() == null || dim.getRealNamedGroupInfo().isEmpty()) {
         dim.setOrder(XConstants.SORT_ASC);
      }

      return chartDataHandler.browseDimensionData(rvs, assemblyName, dim);
   }

   /**
    * Get all variables required to run the query
    */
   @GetMapping(value = "/api/vsdata/check-variables")
   public List<VariableAssemblyModelInfo> getRequiredVariables(@RequestParam("vsId") String vsId,
                                                               @RequestParam("assemblyName") String assemblyName,
                                                               Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      Assembly assembly = viewsheet.getAssembly(assemblyName);

      if(!(assembly instanceof DataVSAssembly)) {
         return null;
      }

      DataVSAssembly dataAssembly = (DataVSAssembly) assembly;
      XSourceInfo sourceInfo = dataAssembly.getSourceInfo();
      UserVariable[] vars = BrowsedData.getVariables(sourceInfo,
                                                     dataAssembly.getTableName(), null,
                                                     rvs.getViewsheetSandbox().getAssetQuerySandbox(),
                                                     principal, rvs.getViewsheetSandbox().getVariableTable());

      if(vars == null) {
         return null;
      }

      return Arrays.stream(vars)
         .map(VariableAssemblyModelInfo::new)
         .collect(Collectors.toList());
   }

   private TableAssembly getBindingSourceTable(DataVSAssembly chart) {
      String tname = chart.getTableName();
      Viewsheet vs = chart.getViewsheet();
      WorksheetWrapper ws = new WorksheetWrapper(chart.getWorksheet());
      VSUtil.shrinkTable(vs, ws);

      return tname == null || ws == null ? null :
         VSAQuery.getVSTableAssembly(tname, false, vs, ws);
   }

   /**
    * Get the manual ordering list for a freehand table cell
    */
   private ValueLabelListModel getFreehandTableValues(CalcTableVSAssembly assembly,
                                                      DataRefModel dimension,
                                                      int r, int c, int dateLevel,
                                                      RuntimeViewsheet rvs)
      throws Exception
   {
      final String name = assembly.getName();
      TableAssembly bindingSourceTable = getBindingSourceTable(assembly);
      ColumnSelection sourceColumnSelection = null;

      if(bindingSourceTable != null) {
         sourceColumnSelection = bindingSourceTable.getColumnSelection();
      }

      final DataRef[] bindingRefs = assembly.getBindingRefs(sourceColumnSelection);
      final DataRef dataRef = Arrays.stream(bindingRefs)
            .filter(ref -> ref.getName().equals(dimension.getName()) ||
               Tool.equals(DateRangeRef.getName(ref.getName(), dateLevel), dimension.getName()))
            .findAny()
            .orElse(null);

      if(dataRef == null) {
         return ValueLabelListModel.builder().build();
      }

      final TableLayout tableLayout = assembly.getTableLayout();
      final TableCellBinding tableCellBinding = (TableCellBinding) tableLayout.getCellBinding(r, c);
      final OrderInfo orderInfo = tableCellBinding.getOrderInfo(true);
      LayoutTool.syncNamedGroup(orderInfo);
      final List<String> manualOrder = orderInfo.getManualOrder();
      final XNamedGroupInfo namedGroupInfo = orderInfo.getNamedGroupInfo();
      final VSDimensionRef dim = new VSDimensionRef(dataRef);

      String columnName = dataRef.getName();

      if(!Tool.equals(columnName, dimension.getName())) {
         columnName = DateRangeRef.getName(columnName, dateLevel);
      }

      dim.setGroupColumnValue(columnName);
      dim.setManualOrderList(VSDataController.fixNull(manualOrder));
      dim.setDateLevel(dateLevel);
      final List<String> values = new ArrayList<>();
      List<ValueLabelModel> dimensionData =
         chartDataHandler.browseDimensionData(rvs, name, dim).list();

      if(namedGroupInfo != null) {
         final String[] groups = namedGroupInfo.getGroups();
         boolean groupOthers = orderInfo.getOthers() == OrderInfo.GROUP_OTHERS;
         final String otherLabel = orderInfo.getOtherLabel();

         if(namedGroupInfo instanceof AssetNamedGroupInfo) {
            groupOthers =
               ((AssetNamedGroupInfo) namedGroupInfo).getOthers() == OrderInfo.GROUP_OTHERS;
         }

         // leave others label in the manual list but don't show it in the dialog
         ValueLabelModel otherObj =
            dimensionData.stream().filter((valLabel) -> Tool.equals(valLabel.value(), otherLabel))
               .findFirst().orElse(null);

         if(otherObj != null) {
            dimensionData = dimensionData.stream()
               .filter(valLabel -> !Tool.equals(valLabel, otherObj))
               .collect(Collectors.toList());
         }

         for(String group : groups) {
            if(!values.contains(group)) {
               values.add(group);
            }

            ConditionGroup conds = new ConditionGroup(namedGroupInfo.getGroupCondition(group));

            // if detail item is in a named group, it should not be listed since it's already
            // covered by the named groups
            dimensionData = dimensionData.stream()
               .filter(valLabel -> !conds.evaluate(new Object[] { valLabel.value() }))
               .collect(Collectors.toList());
         }

         if(groupOthers) {
            if(dimensionData.size() > 0) {
               values.add(otherLabel);
            }

            final ManualOrderComparer comp = new ManualOrderComparer(XSchema.STRING, manualOrder);
            values.sort(comp);
            List<ValueLabelModel> list = values.stream().map((val) -> ValueLabelModel.builder()
               .value(val).label(val).build()).collect(Collectors.toList());
            return ValueLabelListModel.builder().list(list).build();
         }
      }

      List<ValueLabelModel> list = values.stream().map((val) -> ValueLabelModel.builder()
         .value(val).label(val).build()).collect(Collectors.toCollection(ArrayList::new));
      list.addAll(dimensionData);
      return ValueLabelListModel.builder()
         .list(list)
         .build();
   }

   private VSDimensionRef getVSDimensionRef(DataRef[] refs, DataRefModel dimension) {
      return Arrays.stream(refs)
         .filter((ref) -> ref.getName().equals(dimension.getName()))
         .map((ref) -> (VSDimensionRef) ref)
         .findFirst()
         .orElse(null);
   }

   private final ChartRefModelFactoryService chartRefService;
   private final VSChartDataHandler chartDataHandler;
   private final ViewsheetService viewsheetService;
}
