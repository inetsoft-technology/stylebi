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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSChartDataHandler;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Controller that provides the REST endpoints for the color mapping dialog.
 *
 * @since 12.3
 */
@Controller
public class ColorMappingDialogController {
   /**
    * Creates a new instance of <tt>ColorMappingDialogController</tt>.
    */
   @Autowired
   public ColorMappingDialogController(
      VSBindingService bindingService,
      VSChartDataHandler chartDataHandler, ChartRefModelFactoryService chartRefService,
      ViewsheetService viewsheetService)
   {
      this.bindingService = bindingService;
      this.chartDataHandler = chartDataHandler;
      this.chartRefService = chartRefService;
      this.viewsheetService = viewsheetService;
   }

   @RequestMapping(
      value = "/api/composer/vs/getColorMappingDialogModel",
      method = RequestMethod.POST
   )
   @ResponseBody
   public ColorMappingDialogModel getColorMappingDialogModel(
      @RequestParam("assemblyName") String assemblyName,
      @RequestParam("vsId") String vsId,
      @RequestParam("dimensionName") String dimensionName,
      @RequestBody CategoricalColorModel frame,
      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet vs = rvs.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(assemblyName);
      VSChartInfo cinfo = chart.getVSChartInfo();

      if(DateComparisonUtil.appliedDateComparison(chart.getVSAssemblyInfo())) {
         cinfo = cinfo.clone();
         cinfo.clearDCRuntime();
      }

      AestheticRef cfield = (AestheticRef) GraphUtil.getChartAesRef(cinfo, dimensionName, false);
      List list = null;

      if(cfield == null) {
         LOG.warn("Color field not found in binding: " + dimensionName + " in " +
                  cinfo.getColorField() + " " + cinfo.isMultiStyles());
         return null;
      }

      String browseDataErrorMsg = null;

      if(cfield.getDataRef() instanceof VSDimensionRef) {
         VSDimensionRef dim = (VSDimensionRef) cfield.getDataRef();

         try {
            list = chartDataHandler.browseDimensionData(rvs, assemblyName, dim).list();
         }
         catch(TimeoutException ex) {
            browseDataErrorMsg = ex.getMessage();
         }
      }

      ValueLabelModel[] dimData = list == null ? new ValueLabelModel[0] :
         (ValueLabelModel[]) list.toArray(new ValueLabelModel[0]);
      ColorMapModel[] colormaps = frame.getColorMaps();
      final Map<String, Color> dimensionColors = vs.getDimensionColors(frame.getField());
      final Set<Map.Entry<String, Color>> globalEntries = dimensionColors.entrySet();
      final ColorMapModel[] globalColorMaps;

      if(dimensionColors.isEmpty()) {
         if(dimData.length > 0) {
            globalColorMaps = new ColorMapModel[]{
               new ColorMapModel(dimData[0].value() + "", null)
            };
         }
         else {
            globalColorMaps = new ColorMapModel[0];
         }
      }
      else {
         globalColorMaps =
            globalEntries.stream()
                   .map(entry -> new ColorMapModel(entry.getKey(), Tool.toString(entry.getValue())))
                   .toArray(ColorMapModel[]::new);
      }

      if(colormaps.length == 0 && dimData.length > 0) {
         ColorMapModel model = new ColorMapModel();
         model.setOption(dimData[0].value() + "");
         colormaps = Arrays.copyOf(colormaps, colormaps.length + 1);
         colormaps[0] = model;
      }

      ColorMappingDialogModel model = new ColorMappingDialogModel(colormaps, dimData);
      model.setGlobalModel(new ColorMappingDialogModel(globalColorMaps, dimData));
      model.setUseGlobal(frame.isUseGlobal());
      model.setShareColors(frame.isShareColors());
      model.setBrowseDataErrorMsg(browseDataErrorMsg);

      return model;
   }

   private final ChartRefModelFactoryService chartRefService;
   private final VSChartDataHandler chartDataHandler;
   private final VSBindingService bindingService;
   private final ViewsheetService viewsheetService;

   private static final Logger LOG = LoggerFactory.getLogger(ColorMappingDialogController.class);
}
