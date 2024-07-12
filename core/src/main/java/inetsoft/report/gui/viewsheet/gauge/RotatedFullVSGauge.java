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
package inetsoft.report.gui.viewsheet.gauge;

import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.css.*;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Draw the half guage.
 *
 * @version 8.5, 2006-6-22
 * @author InetSoft Technology Corp
 */
public class RotatedFullVSGauge extends DefaultFullVSGauge {
   /**
    * Calculate the beginning of the values, ticks and ranges drawing angle.
    */
   @Override
   protected double getValueDrawingBeginRadian() {
      double begin = (Math.PI / 2 - angle) + angle - getRotation();
      return begin;
   }

   /**
    * Draw a string on the panel.
    * Remove 0 tick, and move the max tick center.
    *
    * @param g The graphics object to draw.
    * @param center The panel's center point.
    * @param radian The string's center point's radian relative to
    *                   the panel's center point.
    * @param radius The string's center point's radius relative to
    *                   the panel's center point.
    * @param content The string itself.
    */
   @Override
   protected void drawString(Graphics2D g, Point2D center, double radian,
      double radius, String content)
   {
      if(!content.equals("0")) {
         GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) getAssemblyInfo();
         double minValue = info.getMin();
         String minLabel = formatValue(minValue, false);

         if(minLabel.equals(content)) {
            return;
         }

         super.drawString(g, center, radian - 0.065, radius, content);
      }
   }

   /**
    * Fill color for needle.
    **/
   private void drawNeedleColor(Graphics2D g2d) {
      if(valueFillColor != null) {
         double radianMaxRange = getRadianBetweenBeginAndEnd();
         double beginRadian = getValueDrawingBeginRadian();
         GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
         double delta = radianMaxRange *
            (getValue() - info.getMin()) / (info.getMax() - info.getMin());
         Color color = info.getValueFillColor();

         // CSSDictionary.getDictionary() is for viewsheet ONLY
         if(color == null) {
            color = CSSDictionary.getDictionary().getForeground(
               new CSSParameter(CSSConstants.GAUGE_VALUE_FILL, vsobjId.get(), vsClass.get(), null));
         }

         if(color == null) {
            color = valueFillColor;
         }

         fillRange(g2d, center, beginRadian + getAngleAdjustment(),
                   beginRadian - delta - getAngleAdjustment(),
                   color, color, valueFillRadius,
                   valueFillWidth, false, false, false);
      }
   }

   @Override
   protected void drawNeedle(Graphics2D g2d) {
      drawNeedleColor(g2d);

      super.drawNeedle(g2d);
   }

   @Override
   public void drawSVGNeedle(Graphics2D g2d) {
      drawNeedleColor(g2d);

      super.drawSVGNeedle(g2d);
   }

   @Override
   protected void parseXML(Element node) throws Exception {
      super.parseXML(node);

      if(node.getNodeName().equals("face")) {
         Element rangeNode = Tool.getChildNodeByTagName(node, "valueFill");

         if(rangeNode != null) {
            String fill = Tool.getAttribute(rangeNode, "fill");
            String radius = Tool.getAttribute(rangeNode, "radius");
            String width = Tool.getAttribute(rangeNode, "width");

            if(fill != null) {
               this.valueFillColor = parseColor(fill);
            }

            if(radius != null) {
               this.valueFillRadius = Double.parseDouble(radius);
            }

            if(width != null) {
               this.valueFillWidth = Double.parseDouble(width);
            }
         }
      }
   }

   @Override
   protected void setScale(double scale) {
      super.setScale(scale);
      valueFillRadius *= scale;
      valueFillWidth *= scale;
   }

   // fill the range for the value
   private Color valueFillColor = null;
   private double valueFillRadius;
   private double valueFillWidth;
}
