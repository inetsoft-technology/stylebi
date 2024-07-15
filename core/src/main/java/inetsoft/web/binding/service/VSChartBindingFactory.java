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

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.ColorMapModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;
import inetsoft.web.binding.model.graph.aesthetic.VisualFrameModel;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.List;
import java.util.*;

@Component
public class VSChartBindingFactory extends VSBindingFactory<ChartVSAssembly, ChartBindingModel> {
   @Autowired
   public VSChartBindingFactory(ChartRefModelFactoryService refService,
                                ChartAestheticService aesService,
                                DataRefModelFactoryService dataRefService,
                                VisualFrameModelFactoryService visualService)
   {
      this.refService = refService;
      this.aesService = aesService;
      this.dataRefService = dataRefService;
      this.visualService = visualService;
   }

   @Override
   public Class<ChartVSAssembly> getAssemblyClass() {
      return ChartVSAssembly.class;
   }

   /**
    * Creates a new model instance for the specified assembly.
    *
    * @param assembly the assembly.
    *
    * @return a new model.
    */
   @Override
   public ChartBindingModel createModel(ChartVSAssembly assembly) {
      VSChartInfoModelBuilder infoBuild =
         new VSChartInfoModelBuilder(this.refService,
                                     this.aesService,
                                     this.dataRefService,
                                     this.visualService,
                                     assembly);

      VSChartInfo vsChartInfo = assembly.getVSChartInfo();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getVSAssemblyInfo();
      PlotDescriptor plotDescriptor = info.getChartDescriptor().getPlotDescriptor();
      boolean wizard = WizardRecommenderUtil.isWizardTempBindingAssembly(assembly.getName());
      ChartBindingModel chartBinding = infoBuild.createChartBinding(
         vsChartInfo, plotDescriptor, wizard);
      chartBinding.setHasDateComparison(info.getDateComparisonRef() != null);
      return applyColorsToFrame(chartBinding, assembly);
   }

   /**
    * Update a chart vs assembly.
    *
    * @param model the specified chart binding model.
    * @param assembly the specified chart vs assembly.
    *
    * @return the chart vs assembly.
    */
   @Override
   public ChartVSAssembly updateAssembly(ChartBindingModel model,
      ChartVSAssembly assembly)
   {
      VSChartInfoModelBuilder infoBuild =
         new VSChartInfoModelBuilder(this.refService,
                                     this.aesService,
                                     this.dataRefService,
                                     this.visualService,
                                     assembly);
      VSChartInfo ocinfo = assembly.getVSChartInfo();
      VSChartInfo ncinfo = ocinfo.clone();
      VSChartInfo cinfo = (VSChartInfo) infoBuild.updateChartInfo(model, ocinfo, ncinfo,
         assembly.getChartDescriptor());
      applyColorsToViewsheet(model, assembly);
      cinfo.clearRuntime();
      cinfo.setRuntime(false);
      assembly.setVSChartInfo(cinfo);
      return assembly;
   }

   /**
    * Copy global color mapping from the viewsheet to the new color frame
    */
   private ChartBindingModel applyColorsToFrame(ChartBindingModel chartBinding,
                                                ChartVSAssembly assembly)
   {
      final Viewsheet viewsheet = assembly.getViewsheet();

      if(chartBinding.isMultiStyles()) {
         List<ChartRefModel> aggrs = new ArrayList<>(chartBinding.getXFields());
         aggrs.addAll(chartBinding.getYFields());

         for(ChartRefModel ref : aggrs) {
            if(ref instanceof ChartAggregateRefModel) {
               AestheticInfo colorField = ((ChartAggregateRefModel) ref).getColorField();

               if(colorField != null) {
                  applyColorsToFrame(viewsheet, colorField);
               }
            }
         }
      }
      else {
         final AestheticInfo colorField = chartBinding.getColorField();

         if(colorField != null) {
            applyColorsToFrame(viewsheet, colorField);
         }

         final AestheticInfo nodeColorField = chartBinding.getNodeColorField();

         if(nodeColorField != null) {
            applyColorsToFrame(viewsheet, nodeColorField);
         }
      }

      return chartBinding;
   }

   private void applyColorsToFrame(Viewsheet viewsheet, AestheticInfo colorField) {
      final VisualFrameModel frame = colorField.getFrame();

      if(frame instanceof CategoricalColorModel) {
         if(((CategoricalColorModel) frame).isUseGlobal()) {
            final Map<String, Color> dimensionColors = viewsheet.getDimensionColors(frame.getField());
            final ArrayList<ColorMapModel> colors = new ArrayList<>();

            for(Map.Entry<String, Color> colorMap : dimensionColors.entrySet()) {
               final String dimension = colorMap.getKey();
               final String color = Tool.colorToHTMLString(colorMap.getValue());
               final ColorMapModel e = new ColorMapModel(dimension, color);
               colors.add(e);
            }

            final ColorMapModel[] globalColorMaps = colors.toArray(new ColorMapModel[0]);
            ((CategoricalColorModel) frame).setGlobalColorMaps(globalColorMaps);
         }
      }
   }

   /**
    * Update viewsheet with global colors from the updated color binding
    */
   private void applyColorsToViewsheet(ChartBindingModel model, ChartVSAssembly assembly) {
      final Viewsheet viewsheet = assembly.getViewsheet();
      ChartInfo info = assembly.getVSChartInfo();

      if(info.isMultiAesthetic()) {
         List<ChartRefModel> aggrs = new ArrayList<>(model.getXFields());
         aggrs.addAll(model.getYFields());
         aggrs.add(model.getStartField());
         aggrs.add(model.getMilestoneField());

         for(ChartRefModel ref : aggrs) {
            if(ref instanceof ChartAggregateRefModel) {
               AestheticInfo colorField = ((ChartAggregateRefModel) ref).getColorField();

               if(colorField != null) {
                  applyColorsToViewsheet(viewsheet, colorField);
               }
            }
         }
      }
      else {
         AestheticInfo colorField = model.getColorField();

         if(colorField != null) {
            applyColorsToViewsheet(viewsheet, colorField);
         }

         if(info instanceof RelationChartInfo) {
            colorField = model.getNodeColorField();

            if(colorField != null) {
               applyColorsToViewsheet(viewsheet, colorField);
            }
         }
      }
   }

   private void applyColorsToViewsheet(Viewsheet viewsheet, AestheticInfo colorField) {
      final VisualFrameModel frame = colorField.getFrame();

      if(frame instanceof CategoricalColorModel) {
         if(((CategoricalColorModel) frame).isUseGlobal()) {
            final ColorMapModel[] models = ((CategoricalColorModel) frame).getGlobalColorMaps();
            final HashMap<String, Color> colors = new HashMap<>();

            for(ColorMapModel colorMap : models) {
               final Color color = Tool.getColorFromHexString(colorMap.getColor());
               final String dimension = colorMap.getOption();
               colors.put(dimension, color);
            }

            viewsheet.setDimensionColors(colorField.getFrame().getField(), colors);
         }
      }
   }

   private final ChartRefModelFactoryService refService;
   private final ChartAestheticService aesService;
   private final DataRefModelFactoryService dataRefService;
   private final VisualFrameModelFactoryService visualService;
}
