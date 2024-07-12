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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.CalculatorHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.DimensionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Controller that provides a REST endpoint for composer viewsheet actions.
 */
@RestController
public class VSCalculatorController {
   @RequestMapping(value = "/api/composer/dims", method = RequestMethod.GET)
   @ResponseBody
   public Map<String, List<DimensionInfo>> getDimensionInfos(@RequestParam("vsId") String vsId,
                                               @RequestParam("assemblyName") String assemblyName,
                                               Principal principal)
      throws Exception
   {
      Assembly assembly = getAssembly(Tool.byteDecode(vsId), assemblyName, principal);
      Map<String, List<DimensionInfo>> map = new HashMap<>();
      map.put(CalculatorHandler.PERCENT_LEVEL_TAG, new ArrayList<>());
      map.put(CalculatorHandler.PERCENT_DIMS_TAG, new ArrayList<>());
      map.put(CalculatorHandler.VALUE_OF_TAG, new ArrayList<>());
      map.put(CalculatorHandler.BREAK_BY_TAG, new ArrayList<>());
      map.put(CalculatorHandler.MOVING_TAG, new ArrayList<>());
      appendFixedOptions(map, assembly);

      List<XDimensionRef> list = null;

      if(assembly instanceof ChartVSAssembly) {
         list = getAllDimensions((ChartVSAssembly) assembly);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         list = getAllDimensions((CrosstabVSAssembly) assembly);
      }

      List<DimensionInfo> dinfos = new ArrayList<>();
      List<String> variables = new ArrayList<>();

      for(int i = 0; i < list.size(); i++) {
         XDimensionRef dim = list.get(i);

         if(!(dim instanceof VSDimensionRef)) {
            continue;
         }

         VSDimensionRef vdim = (VSDimensionRef) dim;

         if(vdim.isVariable()) {
            String group = vdim.getGroupColumnValue();

            if(variables.contains(group)) {
               continue;
            }

            variables.add(group);
         }

         String desc = null;

         if(dim.getDataRef() instanceof ColumnRef) {
            desc = ((ColumnRef) dim.getDataRef()).getDescription();
         }

         dinfos.add(new DimensionInfo(dim, chartHandler, desc));
      }

      map.get(CalculatorHandler.PERCENT_DIMS_TAG).addAll(dinfos);
      map.get(CalculatorHandler.VALUE_OF_TAG).addAll(dinfos);
      map.get(CalculatorHandler.BREAK_BY_TAG).addAll(dinfos);

      return map;
   }

   private void appendFixedOptions(Map<String, List<DimensionInfo>> map, Assembly assembly) {
      List<DimensionInfo> percLevels = map.get(CalculatorHandler.PERCENT_LEVEL_TAG);
      List<DimensionInfo> valueOfDatas = map.get(CalculatorHandler.VALUE_OF_TAG);
      List<DimensionInfo> breakByDims = map.get(CalculatorHandler.BREAK_BY_TAG);
      List<DimensionInfo> movingDims = map.get(CalculatorHandler.MOVING_TAG);

      if(assembly instanceof ChartVSAssembly) {
         percLevels.add(new DimensionInfo(
            catalog.getString("Grand Total"), PercentCalc.GRAND_TOTAL + ""));
         percLevels.add(new DimensionInfo(
            catalog.getString("Sub Total"), PercentCalc.SUB_TOTAL + ""));
         valueOfDatas.add(new DimensionInfo(catalog.getString("Series"), ""));
         breakByDims.add(
            new DimensionInfo(catalog.getString("Inner Dimension (Default)"), ""));
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         percLevels.add(new DimensionInfo(
            catalog.getString("Grand Total"), PercentCalc.GRAND_TOTAL + ""));
         percLevels.add(new DimensionInfo(
            catalog.getString("Sub Total"), PercentCalc.SUB_TOTAL + ""));

         VSCrosstabInfo info = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         DataRef[] rowHeaders = info.getRowHeaders();
         DataRef[] colHeaders = info.getColHeaders();
         boolean hasRow = rowHeaders != null && rowHeaders.length > 0;
         boolean hasCol = colHeaders != null && colHeaders.length > 0;

         if(hasRow) {
            valueOfDatas.add(new DimensionInfo(
               catalog.getString(ValueOfCalc.ROW_SERIES), AbstractCalc.ROW_INNER));
            movingDims.add(new DimensionInfo(
               catalog.getString("Row Inner Dimension"), AbstractCalc.ROW_INNER));
            breakByDims.add(new DimensionInfo(
               catalog.getString("Row Inner Dimension (Default)"), AbstractCalc.ROW_INNER));
         }

         if(hasCol) {
            valueOfDatas.add(new DimensionInfo(
               catalog.getString(ValueOfCalc.COLUMN_SERIES), AbstractCalc.COLUMN_INNER));
            movingDims.add(new DimensionInfo(
               catalog.getString("Column Inner Dimension"), AbstractCalc.COLUMN_INNER));
            String label = "Column Inner Dimension";

            if(!hasRow) {
               label = label + " (Default)";
            }

            breakByDims.add(new DimensionInfo(catalog.getString(label), AbstractCalc.COLUMN_INNER));
         }
      }
   }

   private Assembly getAssembly(String vsId, String assemblyName, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(Tool.byteDecode(vsId), principal);
      Viewsheet vs = rvs.getViewsheet();
      return vs.getAssembly(assemblyName);
   }

   private List<XDimensionRef> getAllDimensions(CrosstabVSAssembly crosstab) {
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      DataRef[] rows = cinfo.getRuntimeRowHeaders();
      DataRef[] cols = cinfo.getRuntimeColHeaders();
      DataRef[] periodRows = cinfo.getPeriodRuntimeRowHeaders();

      final List<XDimensionRef> dims = Stream.of(Stream.of(rows), Stream.of(cols), Stream.of(periodRows))
                                                .flatMap(Function.identity())
                                                .filter(ref -> ref instanceof XDimensionRef)
                                                .map(XDimensionRef.class::cast)
                                                .collect(Collectors.toList());

      return getUniqueDimensions(dims);
   }

   private List<XDimensionRef> getAllDimensions(ChartVSAssembly chart) {
      VSChartInfo cinfo = chart.getVSChartInfo();
      List<XDimensionRef> dims = chartHandler.getAllDimensions(cinfo, true);
      dims = getUniqueDimensions(dims);
      fixDescription(chart, dims);
      return dims;
   }

   private List<XDimensionRef> getUniqueDimensions(List<XDimensionRef> refs) {
      final Set<String> names = new HashSet<>();
      final List<XDimensionRef> uniqueRefs = new ArrayList<>();

      for(XDimensionRef ref : refs) {
         if(!names.contains(ref.getFullName())) {
            uniqueRefs.add(ref);
            names.add(ref.getFullName());
         }
      }

      return uniqueRefs;
   }

   private void fixDescription(ChartVSAssembly chart, List<XDimensionRef> refs) {
      ColumnSelection cols = new ColumnSelection();

      for(int i = 0; i < refs.size(); i++) {
         cols.addAttribute(new ColumnRef(refs.get(i)));
      }

      XUtil.addDescriptionsFromSource(chart, cols);
   }

   @RequestMapping(value = "/api/composer/supportReset", method = RequestMethod.GET)
   public Map supportReset(@RequestParam("vsId") String vsId,
                               @RequestParam("assemblyName") String assemblyName,
                               @RequestParam("aggreName") String aggreName, Principal principal)
      throws Exception
   {
      Assembly assembly = getAssembly(Tool.byteDecode(vsId), assemblyName, principal);
      Map map = new HashMap();

      if(assembly instanceof ChartVSAssembly) {
         VSAssemblyInfo info = ((ChartVSAssembly) assembly).getVSAssemblyInfo();
         VSChartInfo cinfo = ((ChartVSAssemblyInfo) info).getVSChartInfo();
         map.put(CalculatorHandler.INNER_DIMENSION, chartHandler.supportReset(cinfo, aggreName));
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         VSAssemblyInfo info = ((CrosstabVSAssembly) assembly).getVSAssemblyInfo();
         VSCrosstabInfo cinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
         map.put(AbstractCalc.ROW_INNER, supportReset(cinfo.getRowHeaders()));
         map.put(AbstractCalc.COLUMN_INNER, supportReset(cinfo.getColHeaders()));
      }

      return map;
   }

   /**
    * Return if support reset for running total.
    */
   private boolean supportReset(DataRef[] refs) {
      if(refs == null || refs.length == 0) {
         return false;
      }

      DataRef ref = refs[refs.length - 1];

      if(!(ref instanceof XDimensionRef)) {
         return false;
      }

      return calculatorHandler.supportReset((XDimensionRef) ref);
   }

   @RequestMapping(value = "/api/composer/resetOptions", method = RequestMethod.GET)
   @ResponseBody
   public Map getResetOptions(@RequestParam("vsId") String vsId,
                                         @RequestParam("assemblyName") String assemblyName,
                                         @RequestParam("aggreName") String aggreName, Principal principal)
      throws Exception
   {
      Assembly assembly = getAssembly(Tool.byteDecode(vsId), assemblyName, principal);
      Map map = new HashMap();

      if(assembly instanceof ChartVSAssembly) {
         VSAssemblyInfo info = ((ChartVSAssembly) assembly).getVSAssemblyInfo();
         VSChartInfo cinfo = ((ChartVSAssemblyInfo) info).getVSChartInfo();

         map.put(CalculatorHandler.INNER_DIMENSION, chartHandler.getResetOptions(cinfo, aggreName));
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         VSAssemblyInfo info = ((CrosstabVSAssembly) assembly).getVSAssemblyInfo();
         VSCrosstabInfo cinfo = ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
         DataRef[] rowHeaders = cinfo.getRowHeaders();
         DataRef[] colHeaders = cinfo.getColHeaders();
         XDimensionRef dref = null;

         if(supportReset(rowHeaders)) {
            dref = (XDimensionRef) rowHeaders[rowHeaders.length - 1];
            map.put(AbstractCalc.ROW_INNER, calculatorHandler.getResetOptions(dref));
         }

         if(supportReset(colHeaders)) {
            dref = (XDimensionRef) colHeaders[colHeaders.length - 1];
            map.put(AbstractCalc.COLUMN_INNER, calculatorHandler.getResetOptions(dref));
         }
      }

      return map;
   }

   @Autowired
   public void setViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Autowired
   private VSChartHandler chartHandler;
   @Autowired
   private CalculatorHandler calculatorHandler;
   private ViewsheetService viewsheetService;
   private Catalog catalog = Catalog.getCatalog();
}
