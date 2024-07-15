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
package inetsoft.uql.viewsheet.graph;

import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * RadarVSChartInfo maintains binding info of radar chart.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RadarVSChartInfo extends MergedVSChartInfo implements RadarChartInfo {
   /**
    * Constructor.
    */
   public RadarVSChartInfo() {
      super();
      labelDesc = new AxisDescriptor();
      /* default to show now
      labelDesc.setLineVisible(false);
      labelDesc.setTicksVisible(false);
       */
      labelDesc.getAxisLabelTextFormat().getCSSFormat().setCSSType(CSSConstants.Measure_Title);
   }

   /**
    * Get label axis descriptor for parallel coord.
    */
   @Override
   public AxisDescriptor getLabelAxisDescriptor() {
      return labelDesc;
   }

   /**
    * Set the label axis descriptor for parallel coord.
    */
   public void setLabelAxisDescriptor(AxisDescriptor desc) {
      this.labelDesc = desc;
   }

   @Override
   public boolean supportsColorFieldFrame() {
      return false;
   }

   /**
    * Check if the size frame is per measure.
    */
   @Override
   public boolean supportsSizeFieldFrame() {
      return false;
   }

   /**
    * Check if equals another object in content.
    * @param chartInfo the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object chartInfo) {
      if(!super.equalsContent(chartInfo)) {
         return false;
      }

      if(!(chartInfo instanceof RadarVSChartInfo)) {
         return false;
      }

      RadarVSChartInfo rinfo = (RadarVSChartInfo) chartInfo;

      if(!Tool.equalsContent(labelDesc, rinfo.getLabelAxisDescriptor())) {
         return false;
      }

      return true;
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public VSChartInfo clone() {
      try {
         RadarVSChartInfo obj = (RadarVSChartInfo) super.clone();

         if(labelDesc != null) {
            obj.labelDesc = labelDesc.clone();
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone RadarVSChartInfo", ex);
         return null;
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(labelDesc != null) {
         writer.println("<labelAxisDescriptor>");
         labelDesc.writeXML(writer);
         writer.println("</labelAxisDescriptor>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element lnode = Tool.getChildNodeByTagName(elem, "labelAxisDescriptor");

      // backward compatibility, ParallelAxisDescriptor.java is used before
      // version 10.2
      if(lnode != null) {
         Element axisnode = Tool.getChildNodeByTagName(lnode, "parallelAxisDescriptor");

         if(axisnode != null) {
            labelDesc = new AxisDescriptor();
            labelDesc.parseXML(axisnode);
            return;
         }
      }

      if(lnode != null) {
         Element axisnode = Tool.getChildNodeByTagName(lnode, "axisDescriptor");

         if(axisnode != null) {
            labelDesc = new AxisDescriptor();
            labelDesc.parseXML(axisnode);
         }
      }
   }

   @Override
   public boolean supportsShapeFieldFrame() {
      final PlotDescriptor plotDescriptor = Optional.ofNullable(getChartDescriptor())
         .map(ChartDescriptor::getPlotDescriptor)
         .orElse(null);
      final boolean isPointLine = plotDescriptor != null && plotDescriptor.isPointLine();
      final boolean isSimpleRadar = GraphTypes.CHART_RADAR == getChartType();
      final boolean isPointRadar = isPointLine && isSimpleRadar;

      return super.supportsShapeFieldFrame() || isPointRadar;
   }

   private AxisDescriptor labelDesc; // label axis for parallel

   private static final Logger LOG = LoggerFactory.getLogger(RadarVSChartInfo.class);
}
