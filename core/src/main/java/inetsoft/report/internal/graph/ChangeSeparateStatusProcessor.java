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

import inetsoft.report.StyleFont;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.viewsheet.graph.*;

import java.awt.*;
import java.util.List;

/**
 * Change chart separate status.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChangeSeparateStatusProcessor extends ChangeChartProcessor {
   /**
    * Create a ChangeSeparateStatusProcessor.
    */
   public ChangeSeparateStatusProcessor(ChartInfo info, ChartDescriptor cdesc) {
      this.info = info;
      this.cdesc = cdesc;
   }

   /**
    * Process.
    */
   public void process(boolean separated, boolean multi) {
      separatedChanged = separated != info.isSeparatedGraph();
      multiChanged = multi != info.isMultiStyles();

      info.setSeparatedGraph(separated);
      info.setMultiStyles(multi);

      // fix the runtime chart type of refs or the chart
      info.updateChartType(!info.isMultiStyles());

      if(separatedChanged) {
         fixAxisProperties(info, info.isSeparatedGraph());

         // keep the y axis settings/format
         if(info.getYFieldCount() > 0 && info.getYField(0) instanceof ChartAggregateRef) {
            ChartAggregateRef yfield = (ChartAggregateRef) info.getYField(0);

            if(separated) {
               yfield.setAxisDescriptor(info.getAxisDescriptor());
            }
            else {
               info.setAxisDescriptor(yfield.getAxisDescriptor());
            }
         }
      }

      if(multiChanged) {
         // copy value label format and legend descriptors
         PlotDescriptor plot = cdesc.getPlotDescriptor();
         LegendsDescriptor legends = cdesc.getLegendsDescriptor();
         List[] arr = {GraphUtil.getMeasures(info.getXFields()),
                       GraphUtil.getMeasures(info.getYFields())};

         // clear out runtime refs so we don't get the legend descriptors
         // from the old refs, which override the legend descriptors
         // set below
         info.clearRuntime();

         for(List refs : arr) {
            for(Object ref : refs) {
               ChartAggregateRef aggr = (ChartAggregateRef) ref;
               AestheticRef color = aggr.getColorField();
               AestheticRef shape = aggr.getShapeField();
               AestheticRef size = aggr.getSizeField();

               // copy from plot to aggr
               if(multi) {
                  aggr.setTextFormat((CompositeTextFormat) plot.getTextFormat().clone());

                  if(color != null) {
                     LegendDescriptor legendDesc = legends.getColorLegendDescriptor();
                     legendDesc = legendDesc == null ? null : (LegendDescriptor) legendDesc.clone();
                     color.setLegendDescriptor(legendDesc);
                  }

                  if(shape != null) {
                     LegendDescriptor legendDesc = legends.getColorLegendDescriptor();
                     legendDesc = legendDesc == null ? null : (LegendDescriptor) legendDesc.clone();
                     shape.setLegendDescriptor(legendDesc);
                  }

                  if(size != null) {
                     LegendDescriptor legendDesc = legends.getColorLegendDescriptor();
                     legendDesc = legendDesc == null ? null : (LegendDescriptor) legendDesc.clone();
                     size.setLegendDescriptor(legendDesc);
                  }
               }
               // copy from aggr to plot
               else {
                  plot.setTextFormat(aggr.getTextFormat());

                  if(color != null) {
                     legends.setColorLegendDescriptor(color.getLegendDescriptor());
                  }

                  if(shape != null) {
                     legends.setShapeLegendDescriptor(shape.getLegendDescriptor());
                  }

                  if(size != null) {
                     legends.setSizeLegendDescriptor(size.getLegendDescriptor());
                  }

                  break;
               }
            }
         }
      }

      if(info.isMultiAesthetic()) {
         for(ChartAggregateRef ref : info.getAestheticAggregateRefs(false)) {
            fixShapeField(ref, info, getChartType(info, ref));
         }
      }
      else {
         fixShapeField(info, info, getChartType(info, null));
      }
   }

   /**
    * Fix the format info in axis descriptor
    * @param cinfo The object representing the chart.
    * @param multi When true, indicates the mode of the chart
    * is switched to separate graph view, and all global axis attributes
    * should be copied to each individual aggregates axes.
    */
   private void fixAxisProperties(ChartInfo cinfo, boolean multi) {
      // @by stephenwebster, refactored code and fixed bug1383036667361

      boolean changed = false;

      if(multi) {
         changed = fixMultiAxisProperties(cinfo, cinfo.getYFields());

         if(changed) {
            return;
         }

         fixMultiAxisProperties(cinfo, cinfo.getXFields());
      }
      else {
         changed = fixSingleAxisProperties(cinfo, cinfo.getYFields());

         if(changed) {
            return;
         }

         fixSingleAxisProperties(cinfo, cinfo.getXFields());
      }
   }

   private boolean fixSingleAxisProperties(ChartInfo cinfo, ChartRef[] refs) {
      AxisDescriptor globalAxis = cinfo.getAxisDescriptor();
      boolean changed = false;
      ChartRef firstAgg = getFirstAggregateField(refs, false);
      ChartRef firstSecondaryField = getFirstAggregateField(refs, true);

      if(firstAgg != null) {
         ChartAggregateRef aggr = (ChartAggregateRef) firstAgg;
         AxisDescriptor fieldAdes = aggr.getAxisDescriptor();
         copyAxisAttributes(globalAxis, fieldAdes);
         changed = true;
         final CompositeTextFormat columnFormat = fieldAdes.getColumnLabelTextFormat(aggr.getFullName());

         if(columnFormat != null) {
            globalAxis.setAxisLabelTextFormat((CompositeTextFormat) columnFormat.clone());
         }
      }

      if(firstSecondaryField != null) {
         ChartAggregateRef aggr = (ChartAggregateRef) firstSecondaryField;
         AxisDescriptor fieldAdes = aggr.getAxisDescriptor();
         changed = true;

         if(firstAgg == null) {
            copyAxisAttributes(globalAxis, fieldAdes);
         }

         final CompositeTextFormat columnFormat = fieldAdes.getColumnLabelTextFormat(aggr.getFullName());
         AxisDescriptor axisDescriptor2 = cinfo.getAxisDescriptor2();

         if(columnFormat != null && axisDescriptor2 != null) {
            axisDescriptor2.setAxisLabelTextFormat((CompositeTextFormat) columnFormat.clone());
         }
      }

      return changed;
   }

   private boolean fixMultiAxisProperties(ChartInfo cinfo, ChartRef[] refs) {
      AxisDescriptor globalAxis = cinfo.getAxisDescriptor();
      boolean changed = false;

      for(int i = 0; i < refs.length; i++) {
         ChartRef ref = refs[i];

         if(ref instanceof ChartAggregateRef) {
            ChartAggregateRef aggr = (ChartAggregateRef) ref;
            AxisDescriptor fieldAdes = aggr.getAxisDescriptor();
            copyAxisAttributes(fieldAdes, globalAxis);
            changed = true;
            final AxisDescriptor axisDesc = aggr.isSecondaryY() ?
               cinfo.getAxisDescriptor2() : cinfo.getAxisDescriptor();
            final CompositeTextFormat columnFormat = axisDesc.getAxisLabelTextFormat();

            if(columnFormat != null) {
               fieldAdes.setColumnLabelTextFormat(
                  aggr.getFullName(), (CompositeTextFormat) columnFormat.clone());
            }
         }
      }

      return changed;
   }

   private ChartRef getFirstAggregateField(ChartRef[] fields, boolean secondary) {
      for(int i = 0; i < fields.length; i++) {
         ChartRef field = fields[i];

         if(field instanceof ChartAggregateRef &&
            (secondary && ((ChartAggregateRef) field).isSecondaryY() || !secondary))
         {
            return field;
         }
      }

      return null;
   }

   /**
    * Copy axis attributes from source descriptor to target descriptor.
    * Only color, font, and rotation are copied.  Format is intentionally
    * omitted.
    *
    * @param axisDesc The descriptor to set the new information on.
    * @param sourceDesc The descriptor which contains the source information.
    */
   private void copyAxisAttributes(AxisDescriptor axisDesc, AxisDescriptor sourceDesc) {
      CompositeTextFormat sourceFormat = sourceDesc.getAxisLabelTextFormat();
      TextFormat sformat = sourceFormat.getUserDefinedFormat();
      CompositeTextFormat targetFormat = axisDesc.getAxisLabelTextFormat();
      TextFormat tformat = targetFormat.getUserDefinedFormat();
      Color color = sformat.isColorDefined() ? sformat.getColor() : null;
      Font font = sformat.isFontDefined() ? sformat.getFont() : null;
      Number rotation = sformat.isRotationDefined() ? sformat.getRotation() : null;

      if(color != null) {
         tformat.setColor(new Color(sformat.getColor().getRGB()));
      }

      if(rotation != null) {
         tformat.setRotation(sformat.getRotation());
      }

      if(font != null) {
         tformat.setFont(convertFont(font));
      }

      axisDesc.setTicksVisible(sourceDesc.isTicksVisible());
      axisDesc.setLineColor(sourceDesc.getLineColor());
      axisDesc.setLineVisible(sourceDesc.isLineVisible());
      axisDesc.setMinimum(sourceDesc.getMinimum());
      axisDesc.setMaximum(sourceDesc.getMaximum());
      axisDesc.setIncrement(sourceDesc.getIncrement());
      axisDesc.setMinorIncrement(sourceDesc.getMinorIncrement());
   }

   /**
    * Convert font for line style.
    */
   private Font convertFont(Font font) {
      if(font instanceof StyleFont) {
         return (Font) ((StyleFont) font).clone();
      }
      else {
         return new Font(font.getName(), font.getStyle(), font.getSize());
      }
   }

   private ChartInfo info;
   private ChartDescriptor cdesc;
   private boolean separatedChanged;
   private boolean multiChanged;
}
