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
package inetsoft.report.internal.binding;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.graph.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * This class ChartOption holds all chart data binding related options.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public class ChartOption extends BindingOption {
   /**
    * Create a default chart option attr.
    */
   public ChartOption() {
      cinfo = createEmptyChartInfo();
      desc = new ChartDescriptor();
      applyReportFont();
   }

   /**
    * Get the chart desc.
    * @return a ChartDescriptor object that contains the chart attributes.
    */
   public ChartDescriptor getChartDescriptor() {
      return desc;
   }

   /**
    * Set the chart desc.
    * @param desc a ChartDescriptor object that contains the chart desc.
    */
   public void setChartDescriptor(ChartDescriptor desc) {
      this.desc = desc;
   }

   /**
    * Get the wrapped chart info object.
    */
   public ChartInfo getChartInfo() {
      return cinfo;
   }

   /**
    * Set the wrapped chart info object.
    * @param cinfo the wrapped chart info object.
    */
   public void setChartInfo(ChartInfo cinfo) {
      this.cinfo = cinfo;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         ChartOption option = (ChartOption) super.clone();
         option.cinfo = cinfo != null ? (ChartInfo) cinfo.clone() : cinfo;
         option.desc = desc != null ? (ChartDescriptor) desc.clone() : desc;
         applyReportFont();
         return option;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone chart option", ex);
      }

      return null;
   }

   /**
    * Check if the binding option require runtime binding attr.
    */
   @Override
   protected boolean requiresRuntime() {
      return true;
   }

   /**
    * Check two options equals.
    */
   public boolean equals(Object obj) {
      if(obj == this) {
         return true;
      }

      if(!(obj instanceof ChartOption) || !super.equals(obj)) {
         return false;
      }

      ChartOption copt = (ChartOption) obj;

      return cinfo.equalsContent(copt.cinfo) &&
         (desc == null ? copt.desc == null : desc.equalsContent(copt.cinfo));
   }

   /**
    * Create a empty chart info.
    */
   private ChartInfo createEmptyChartInfo() {
      ChartInfo info = new DefaultVSChartInfo();
      // the defautl runtime chart type is bar
      info.updateChartType(!info.isMultiStyles());
      return info;
   }

   /**
    * Clear all settings.
    */
   @Override
   public void clear() {
      super.clear();
      cinfo.removeFields();
      desc.clearTargets();
   }

   /**
    * Apply the Default Report Font for the Chart Descriptors.
    */
   public void applyReportFont() {
      Font fn = SreeEnv.getFont("report.font");

      if(fn != null) {
         LegendsDescriptor legends = desc.getLegendsDescriptor();
         legends.getColorLegendDescriptor().getContentTextFormat()
            .getDefaultFormat().setFont(fn);
         legends.getShapeLegendDescriptor().getContentTextFormat()
            .getDefaultFormat().setFont(fn);
         legends.getSizeLegendDescriptor().getContentTextFormat()
            .getDefaultFormat().setFont(fn);
         legends.getTitleTextFormat().getDefaultFormat().setFont(fn);
         TitlesDescriptor titles = desc.getTitlesDescriptor();
         titles.getYTitleDescriptor().getTextFormat()
            .getDefaultFormat().setFont(fn);
         titles.getY2TitleDescriptor().getTextFormat()
            .getDefaultFormat().setFont(fn);
         titles.getX2TitleDescriptor().getTextFormat()
            .getDefaultFormat().setFont(fn);
         titles.getXTitleDescriptor().getTextFormat()
            .getDefaultFormat().setFont(fn);
         desc.getPlotDescriptor().getTextFormat()
            .getDefaultFormat().setFont(fn);
      }
   }

   private ChartInfo cinfo;
   private ChartDescriptor desc;

   private static final Logger LOG =
      LoggerFactory.getLogger(ChartOption.class);
}
