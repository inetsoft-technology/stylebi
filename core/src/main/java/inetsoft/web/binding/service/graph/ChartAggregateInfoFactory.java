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
package inetsoft.web.binding.service.graph;

import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.ColorFrameModel;
import inetsoft.web.binding.model.graph.aesthetic.TextureFrameModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ChartAggregateInfoFactory<A extends ChartAggregateRef>
   extends ChartRefModelFactory<A, ChartAggregateRefModel>
{
   ChartAggregateInfoFactory(VisualFrameModelFactoryService visualService,
                             DataRefModelFactoryService refModelService)
   {
      this.visualService = visualService;
      this.refModelService = refModelService;
   }

   @Override
   protected ChartAggregateRefModel createChartRefModel0(A chartRef, ChartInfo cinfo,
      OriginalDescriptor des)
   {
      ChartAggregateRefModel aggrInfo = new ChartAggregateRefModel(chartRef, cinfo);
      aggrInfo.setOriginal(des);
      boolean isBreakBy = OriginalDescriptor.GROUP.equals(des.getSource());
      DataRef ref = chartRef.getDataRef();

      if(ref != null) {
         aggrInfo.setDataRefModel(refModelService.createDataRefModel(ref));
      }

      aggrInfo.setBuildInCalcs(getBuildInCalcs(chartRef, cinfo, isBreakBy));

      return aggrInfo;
   }

   private List<CalculateInfo> getBuildInCalcs(A chartRef, ChartInfo cinfo,
      boolean isBreakBy)
   {
      Calculator[] calcs =
         AbstractCalc.getDefaultCalcs(findDateDimensions(chartRef, cinfo, isBreakBy));
      List<CalculateInfo> list = new ArrayList<>();

      for(int i = 0; i < calcs.length; i++) {
         initCalculateInfo(calcs[i]);
         CalculateInfo calcInfo = CalculateInfo.createCalcInfo(calcs[i]);
         list.add(calcInfo);
      }

      return list;
   }

   private void initCalculateInfo(Calculator calc) {
      if(calc instanceof RunningTotalCalc) {
         ((RunningTotalCalc) calc).setBreakBy("");
      }
   }

   private void getAllDimensions(DataRef[] refs, List<XDimensionRef> list) {
      for(DataRef ref : refs) {
         if(ref instanceof XDimensionRef) {
            if(!(ref instanceof VSDimensionRef) || !((VSDimensionRef) ref).isVariable()) {
               list.add((XDimensionRef) ref);
            }
         }
         else if(ref instanceof AestheticRef) {
            DataRef ref0 = ((AestheticRef) ref).getDataRef();

            if(ref0 instanceof XDimensionRef &&
               (!(ref0 instanceof VSDimensionRef) || !((VSDimensionRef) ref0).isVariable()))
            {
               list.add((XDimensionRef) ref0);
            }
         }
      }
   }

   /**
    * Find year dimension in binding refs.
    */
   private XDimensionRef[] findDateDimensions(A chartRef, ChartInfo cinfo, boolean isBreakBy) {
      List<XDimensionRef> list = new ArrayList<>();

      ChartRef[] bindingRefs;
      boolean appliedDC = false;

      if(cinfo instanceof VSChartInfo) {
         appliedDC = ((VSChartInfo) cinfo).getRuntimeDateComparisonRefs() != null &&
            ((VSChartInfo) cinfo).getRuntimeDateComparisonRefs().length > 0;
         bindingRefs = ((VSChartInfo) cinfo).getBindingRefs(!appliedDC);
      }
      else {
         bindingRefs = cinfo.getBindingRefs(true);
      }

      getAllDimensions(bindingRefs, list);

      if(cinfo.isMultiAesthetic() && !isBreakBy) {
         getAllDimensions(AbstractChartInfo.getAestheticRefs(chartRef).toArray(
            new DataRef[0]), list);
      }
      else {
         getAllDimensions(cinfo.getAestheticRefs(!appliedDC), list);
      }

      return list.stream()
         .filter(d -> isDateDim(d, cinfo))
         .toArray(XDimensionRef[]::new);
   }

   private boolean isDateDim(XDimensionRef ref, ChartInfo cinfo) {
      if(ref == null ||
         cinfo instanceof VSChartInfo && ((VSChartInfo) cinfo).isPeriodRef(ref.getFullName()))
      {
         return false;
      }

      return XUtil.isDateDim(ref) && !ref.isNamedGroupAvailable();
   }

   @Override
   public void pasteChartRef(ChartInfo cinfo, ChartAggregateRefModel model, A ref) {
      ref.setSecondaryY(model.isSecondaryY());
      ref.setDiscrete(model.isDiscrete());
      ref.setChartType(model.getChartType());

      if(model.getDataRefModel() != null) {
         ref.setDataRef(model.getDataRefModel().createDataRef());
      }

      CalculateInfo calcInfo = model.getCalculateInfo();
      ref.setCalculator(calcInfo != null ? calcInfo.toCalculator() : null);
      updateVisualFrames(model, cinfo, ref);
   }

   private void updateVisualFrames(ChartAggregateRefModel model, ChartInfo cinfo, A ref) {
      aesService.updateVisualFrames(model, cinfo, ref);

      ColorFrameModel scFrameModel = model.getSummaryColorFrame();

      if(scFrameModel != null) {
         ColorFrameWrapper owrapper = ref.getSummaryColorFrameWrapper();
         ColorFrameWrapper nwrapper =
            visualService.updateVisualFrameWrapper(owrapper, scFrameModel);

         if(nwrapper instanceof StaticColorFrameWrapper) {
           ((StaticColorFrameWrapper) nwrapper).setDefaultColor(new Color(0x7030a0));
         }

         ref.setSummaryColorFrameWrapper(nwrapper);
      }

      TextureFrameModel stFrameModel = model.getSummaryTextureFrame();

      if(stFrameModel != null) {
         TextureFrameWrapper owrapper = ref.getSummaryTextureFrameWrapper();
         TextureFrameWrapper nwrapper =
            visualService.updateVisualFrameWrapper(owrapper, stFrameModel);
         ref.setSummaryTextureFrameWrapper(nwrapper);
      }
   }

   @Autowired
   @Lazy
   public void setChartAestheticService(ChartAestheticService aesService) {
      this.aesService = aesService;
   }

   private ChartAestheticService aesService;
   private final VisualFrameModelFactoryService visualService;
   protected final DataRefModelFactoryService refModelService;

   @Component
   public static final class VSChartAggregateInfoFactory
      extends ChartAggregateInfoFactory<VSChartAggregateRef>
   {
      @Autowired
      public VSChartAggregateInfoFactory(VisualFrameModelFactoryService visualService,
                                         DataRefModelFactoryService refModelService)
      {
         super(visualService, refModelService);
      }

      @Override
      public Class<VSChartAggregateRef> getChartRefClass() {
         return VSChartAggregateRef.class;
      }

      @Override
      protected ChartAggregateRefModel createChartRefModel0(VSChartAggregateRef ref,
         ChartInfo cinfo, OriginalDescriptor des)
      {
         ChartAggregateRefModel aggInfo = super.createChartRefModel0(ref, cinfo, des);
         DataRef ref2 = ref.getSecondaryColumn();

         if(ref2 != null) {
            aggInfo.setSecondaryColumn(refModelService.createDataRefModel(ref2));
         }

         return aggInfo;
      }

      @Override
      public void pasteChartRef(ChartInfo cinfo, ChartAggregateRefModel model,
         VSChartAggregateRef ref)
      {
         super.pasteChartRef(cinfo, model, ref);

         ref.setFormulaValue(model.getFormula());
         ref.setColumnValue(model.getColumnValue());
         ref.setCaption(model.getCaption());
         ref.setSecondaryColumnValue(model.getSecondaryColumnValue());
         ref.setSecondaryColumn(model.getSecondaryColumn() != null ?
            model.getSecondaryColumn().createDataRef() : null);
         ref.setOriginalDataType(model.getOriginalDataType());

         try {
            int n = Integer.parseInt(model.getNumValue());
            ref.setNValue(Math.max(1, n) + "");
         }
         catch(Exception ex) {
            ref.setNValue(model.getNumValue());
         }
      }
   }

}
