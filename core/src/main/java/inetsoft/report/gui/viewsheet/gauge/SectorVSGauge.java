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
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SectorVSGauge extends DefaultFullVSGauge {
   /**
    * Caculate the needle's display radian.
    */
   @Override
   protected double calculateNeedleRadian() {
      return super.calculateNeedleRadian() + degreeToRadian(90);
   }

   @Override
   protected double getValueDrawingBeginRadian() {
      return (Math.PI / 2 - angle) + angle - getRotation();
   }

   @Override
   public void fillHighlight(Graphics2D g) {
      GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();

      int colorSize = info.getRangeColors() == null ? 0 : Arrays.stream(info.getRangeColors())
         .filter((c) -> c != null).collect(Collectors.toList()).size();

      if(colorSize > 0) {
         return;
      }

      double content = getTotalValue(info);
      double[] ranges = {content, info.getMax()};
      Color[] rangeColors = {TRANSPARENT_COLOR, HIGHLIGHT_COLOR};
      fillRanges0(g, ranges, rangeColors, false);
   }

   @Override
   protected boolean[] calculateValuesDrawingFlags(Graphics2D g,
                                                   String[] contents, double[] values, Font font)
   {
      boolean[] drawValueFlags = new boolean[contents.length];

      drawValueFlags[0] = drawValueFlags[drawValueFlags.length - 1] = true;

      return drawValueFlags;
   }

   @Override
   protected void drawValue(Graphics2D g2d, Point2D center, boolean[] drawValueFlags,
                            double radian, String label, int majorTickCount, int idx)
   {
      drawValue(g2d, center, drawValueFlags, radian,
         label, idx, idx == 0 || idx == majorTickCount - 1);
   }

   /**
    * Draw a string on the panel.
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
                             double radius, String content, int idx)
   {
      Font font = labelFmt.getFont();
      Color color = labelFmt.getForeground();
      radian = idx == 0 ? getValueDrawingBeginRadian() : getValueDrawingEndRadian();
      g.setColor(color);
      g.setFont(font);

      Point2D tpos = getValuePoint(content, g, center, radian, radius, font, idx);
      Graphics2D g2 = (Graphics2D) g.create();
      CoreTool.drawGlyph(g2, content, (float) tpos.getX(), (float) tpos.getY());
      g2.dispose();
   }

   protected Point2D getValuePoint(String content, Graphics2D g, Point2D center, double radian,
                                   double radius, Font font, int idx)
   {
      g.setFont(font);
      Point2D stringCenter = calculatePoint(center, radius, radian);
      float x = idx == 0 ? (float) stringCenter.getX() - (float) 1.5 * GAP
         : (float) stringCenter.getX() + (float) radius - (float) (content.length() / 2.0);

      return new Point2D.Double(x, stringCenter.getY() + (float) 1.5 * GAP);
   }

   private static final int GAP = 10;
   private static final Color HIGHLIGHT_COLOR = new Color(1.0f, 1.0f, 1.0f, 0.5f);
   private static final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);
}
