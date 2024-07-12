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
package inetsoft.report.internal.graph;

import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Fix information when the swap chart xy binding.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class SwapXYBindingProcessor extends ChangeChartProcessor {
   /**
    * Default constructor.
    */
   public SwapXYBindingProcessor() {
      super();
   }

   /**
    * Create a processor.
    */
   public SwapXYBindingProcessor(ChartInfo info, ChartDescriptor desc) {
      this.cinfo = info;
      this.desc = desc;
   }

   public void process() {
      String tip = cinfo instanceof VSChartInfo ? ((VSChartInfo) cinfo).getToolTipValue()
         : cinfo.getToolTip();

      if(tip != null && !tip.isEmpty() && !VSUtil.isDynamicValue(tip)) {
         tip = updateTip(tip, cinfo.getRTXFields().length, cinfo.getRTYFields().length);

         if(cinfo instanceof VSChartInfo) {
            ((VSChartInfo) cinfo).setToolTipValue(tip);
         }
         else {
            cinfo.setToolTip(tip);
         }
      }

      if(cinfo instanceof MapInfo) {
         swapMapXYFields();
      }
      else {
         swapChartXYFields();

         TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();
         TitleDescriptor xtitle = titlesDesc.getXTitleDescriptor();
         TitleDescriptor ytitle = titlesDesc.getYTitleDescriptor();
         TitleDescriptor x2title = titlesDesc.getX2TitleDescriptor();
         TitleDescriptor y2title = titlesDesc.getY2TitleDescriptor();

         String xtitle0 = xtitle.getTitleValue();
         String ytitle0 = ytitle.getTitleValue();
         String x2title0 = x2title.getTitleValue();
         String y2title0 = y2title.getTitleValue();

         xtitle.setTitleValue(ytitle0);
         ytitle.setTitleValue(xtitle0);
         x2title.setTitleValue(y2title0);
         y2title.setTitleValue(x2title0);

         // swap band color and size
         PlotDescriptor pdesc = desc.getPlotDescriptor();
         Color xcolor = pdesc.getXBandColor();
         int xsize = pdesc.getXBandSize();

         pdesc.setXBandColor(pdesc.getYBandColor());
         pdesc.setXBandSize(pdesc.getYBandSize());
         pdesc.setYBandColor(xcolor);
         pdesc.setYBandSize(xsize);

         cinfo.updateChartType(!cinfo.isMultiStyles());
         fixSizeFrame(cinfo);
      }
   }

   /**
    * Swap chart x/y fields.
    */
   private void swapChartXYFields() {
      List<ChartRef> xrefs = Arrays.asList(cinfo.getXFields());
      List<ChartRef> yrefs = Arrays.asList(cinfo.getYFields());

      copyScatterMatrixFrames(xrefs, yrefs);

      xrefs.sort(vcomparator);
      yrefs.sort(vcomparator);

      cinfo.removeXFields();
      cinfo.removeYFields();

      for(int i = 0; i < yrefs.size(); i++) {
         ChartRef ref = yrefs.get(i);

         if(ref.isMeasure()) {
            ChartAggregateRef agg = (ChartAggregateRef) ref;
            int type = !cinfo.isMultiStyles() ? cinfo.getChartType() : agg.getChartType();

            if(!GraphTypes.supportsInvertedChart(type)) {
               continue;
            }
         }

         cinfo.addXField(i, ref);
      }

      for(int i = 0; i < xrefs.size(); i++) {
         cinfo.addYField(i, xrefs.get(i));
      }
   }

   private void copyScatterMatrixFrames(List<ChartRef> xrefs, List<ChartRef> yrefs) {
      // scatter matrix uses Y fields for binding, so when swapped, keep the same frames. (59582)
      if(GraphTypeUtil.isScatterMatrix(cinfo)) {
         for(int i = 0; i < yrefs.size(); i++) {
            if(xrefs.get(i) instanceof ChartAggregateRef) {
               ChartAggregateRef xaggr = (ChartAggregateRef) xrefs.get(i);
               xaggr.setColorFrame(((ChartAggregateRef) yrefs.get(i)).getColorFrame());
               xaggr.setShapeFrame(((ChartAggregateRef) yrefs.get(i)).getShapeFrame());
               xaggr.setSizeFrame(((ChartAggregateRef) yrefs.get(i)).getSizeFrame());
            }
         }
      }
   }

   /**
    * Swap map x/y fields.
    */
   private void swapMapXYFields() {
      List xrefs = Arrays.asList(cinfo.getXFields());
      List yrefs = Arrays.asList(cinfo.getYFields());

      Collections.sort(xrefs, vcomparator);
      Collections.sort(yrefs, vcomparator);

      // remove x dims
      for(int i = cinfo.getXFields().length - 1; i >= 0; i--) {
         if(!cinfo.getXField(i).isMeasure()) {
            cinfo.removeXField(i);
         }
      }

      // remove y dims
      for(int i = cinfo.getYFields().length - 1; i >= 0; i--) {
         if(!cinfo.getYField(i).isMeasure()) {
            cinfo.removeYField(i);
         }
      }

      for(int i = 0, idx = 0; i < yrefs.size(); i++) {
         ChartRef ref = (ChartRef) yrefs.get(i);

         if(!ref.isMeasure()) {
            cinfo.addXField(idx, ref);
            idx++;
         }
      }

      for(int i = 0, idx = 0; i < xrefs.size(); i++) {
         ChartRef ref = (ChartRef) xrefs.get(i);

         if(!ref.isMeasure()) {
            cinfo.addYField(idx, ref);
            idx++;
         }
      }
   }

   // update reference to parameter value when x/y are swapped
   private String updateTip(String tip, int xcnt, int ycnt) {
      for(int i = 0; i < xcnt; i++) {
         tip = tip.replace("{" + i + "}", "_swapped_to<<" + (i + ycnt) + ">>swapped_to_");
         tip = tip.replace("{" + i + ",", "_swapped_to<<" + (i + ycnt) + "))swapped_to_");
      }

      for(int i = 0; i < ycnt; i++) {
         tip = tip.replace("{" + (xcnt + i) + "}", "_swapped_to<<" + i + ">>swapped_to_");
         tip = tip.replace("{" + (xcnt + i) + ",", "_swapped_to<<" + i + "))swapped_to_");
      }

      return tip.replace("_swapped_to<<", "{")
         .replace(">>swapped_to_", "}")
         .replace("))swapped_to_", ",");
   }

   private ChartInfo cinfo;
   private ChartDescriptor desc;
}
