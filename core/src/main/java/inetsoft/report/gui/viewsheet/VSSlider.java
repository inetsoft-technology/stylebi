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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.StyleFont;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.NumberFormat;

/**
 * VSSlider component for view sheet.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public class VSSlider extends VSFloatable {
   /**
    * Constructor.
    */
   public VSSlider(Viewsheet vs) {
      super(vs);
   }

   /**
    * Paint the component.
    */
   @Override
   public void paintComponent(Graphics2D gImg) {
      SliderVSAssemblyInfo info = (SliderVSAssemblyInfo) getAssemblyInfo();

      if(info == null) {
         return;
      }

      try {
         Graphics cg = gImg.create();

         cg.translate(getContentX(), getContentY());

         VSCompositeFormat format =
            info.getFormat() == null ? new VSCompositeFormat() :
            info.getFormat();

         if(format.getFont() == null) {
            cg.setFont(new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10));
         }
         else {
            cg.setFont(format.getFont());
         }

         fm = cg.getFontMetrics();
         min = info.getMin();
         max = info.getMax();
         inc = info.getIncrement();
         labels = info.getLabels();

         // calculate the label's decimal digital
         decimalDigital = getLabelDecimalDigits(min, inc);
         Number data = (Number) info.getSelectedObject();
         double currentValue = data == null ? 0 : data.doubleValue();
         currentValue = Math.max(min, Math.min(currentValue, max));
         String sMax = "" + max;
         String sCV = info.getValueLabel() == null ? "" + currentValue :
            info.getValueLabel();
         int labelHeight = cg.getFontMetrics().getHeight();
         int sliderLine_W = getContentWidth() - LINE_TICK_GAP * 2;

         if(info.isLabelVisible() || info.isMaxVisible()) {
            try {
               sliderLine_W -=
                  Math.min(10, fm.stringWidth(labels[labels.length - 1]) / 2);
            }
            catch(Exception e) {
               sliderLine_W -= fm.stringWidth(sMax) / 2;
            }
         }

         int sliderLine_Y = getContentHeight() / 2;

         // step 1.3: paint the slider line
         Image track = getTheme().getImage("widget|SliderBase", "sliderTrack",
                                           sliderLine_W, 4);
         cg.drawImage(track, LINE_TICK_GAP, sliderLine_Y, null);

         // step 1.5: paint the minmax and current labels
         // must be placed AFTER where the sliderLine_W is re-calculated!
         int curX = (int) (LINE_TICK_GAP +
                           (currentValue - min) * sliderLine_W /
                           (max - min));

         // draw thumb
         Image thumb = getTheme().getImage("widget|SliderBase", "sliderThumbUp",
            -1, -1);
         int ex = curX - thumb.getWidth(null) / 2;
         int ey = sliderLine_Y - thumb.getHeight(null) / 2;
         // make sure image will draw in bounds
         ex = Math.max(0, ex);
         ex = Math.min(ex, getContentWidth() - thumb.getWidth(null) - 1);
         cg.drawImage(thumb, ex, ey, null);

         if(format.getForeground() != null) {
            cg.setColor(format.getForeground());
         }
         else {
            cg.setColor(Color.black);
         }

         // draw current label
         if(info.isCurrentVisible()) {
            int curLBw = fm.stringWidth(sCV);
            int curLBx = curX - curLBw / 2;

            if(curLBx < 0) {
               // too much left
               curLBx = 0;
            }
            else if(curLBx > getContentWidth() - curLBw) {
               // too much right
               curLBx = getContentWidth() - curLBw;
            }

            Common.drawString(cg, sCV, curLBx,
                              sliderLine_Y - fm.getDescent() - 6);
         }

         // step 2.0: paint ticks and labels
         boolean bShowIncrement = info.isTickVisible();
         boolean bShowLabel = info.isLabelVisible();
         boolean showMin = info.isMinVisible();
         boolean showMax = info.isMaxVisible();

         if(bShowIncrement) {
            int startX = LINE_TICK_GAP;
            int startY = sliderLine_Y + 4;
            int incCount = (int) ((max - min) / inc) ;
            double incWidth = inc * sliderLine_W / (max - min) ;
            Color tickColor = cg.getColor();
            int lb_y = Math.min(startY + TICK_GAP + TICK_HEIGHT,
                                getSize().height - labelHeight + 2) ;

            double maxLW = fm.stringWidth(fixNum(max));
            int jump = getJump();
            jump = jump <= 0 ? 1 : jump;
            int tickJump = (incCount / jump) < 4 ?
               (int) Math.floor(jump / 2) : jump;
            cg.setColor(Color.lightGray);
            // @by keven, fix bug1247461717084(slider displays uncorrectly
            // when exported to excel,ppt and pdf)
            int tickCount = (int) Math.ceil((max - min) / inc);

            for(int i = 0; i <= tickCount; i++) {
               if(showMin && i == 0 || tickJump == 0 ||
                  tickJump != 0 && i % tickJump == 0 &&
                  i != tickCount && i != 0)
               {
                  cg.drawLine((int)(startX + i * incWidth), startY,
                     (int)(startX + i * incWidth), startY + TICK_HEIGHT);
               }
               // process the last tick
               else if(showMax && i == tickCount) {
                  // use the slider's end x coordinate directly instead of
                  // caculating it like other ticks
                  int lastTickX = startX + sliderLine_W;
                  cg.drawLine(lastTickX, startY,
                        lastTickX, startY + TICK_HEIGHT);
               }
            }

            cg.setColor(tickColor);

            for(int i = 0; i <= tickCount;) {
               if(bShowLabel || showMin && i == 0 || showMax && i == tickCount) {
                  String lb_value = "";

                  if(labels != null) {
                     lb_value = showMax && i == tickCount ?
                        labels[labels.length - 1] : labels[i];
                  }

                  if(lb_value == null || lb_value.equals("")) {
                     double labelValue = min + i * inc;

                     // to control the decimal's number if no format
                     NumberFormat nf = NumberFormat.getInstance();
                     nf.setMaximumFractionDigits(decimalDigital);
                     lb_value = nf.format(labelValue);
                  }

                  int lb_w = fm.stringWidth(lb_value);
                  int lb_x = (int) (startX + i * incWidth - lb_w / 2);

                  if(showMin && i == 0 || !showMin && i <= jump) {
                     // if ovlap with next, jump next
                     if(lb_x < 0 &&
                        (lb_w > (i + jump) * incWidth + startX - maxLW / 2))
                     {
                        i = i + jump;
                     }

                     lb_x = (int) Math.max(0, lb_x);
                  }
                  else if(showMax && i == tickCount ||
                     !showMax && i + jump >= tickCount)
                  {
                     // not show max, and last label may be overlapped with
                     // previous label, ignore it
                     double prex = i - jump < 0 ? 0 :
                        (i - jump) * incWidth + startX + maxLW / 2;

                     if(!showMax && lb_x + lb_w > getContentWidth() &&
                        getContentWidth() - lb_w < prex)
                     {
                        i = i + jump;
                        continue;
                     }

                     lb_x = (int) Math.min(lb_x, getContentWidth() - lb_w);
                  }

                  // show max and last label may be overlapped with the previous
                  // label, ignore the previous
                  if(showMax && i != tickCount && i + jump >= tickCount) {
                     if(getContentWidth() - maxLW <
                        i * incWidth + startX + lb_w / 2)
                     {
                        i = Math.min(tickCount, i + jump);
                        continue;
                     }
                  }

                  if(lb_x >= 0) {
                     // paint the label
                     Common.drawString(cg, lb_value, lb_x, lb_y + fm.getAscent());
                  }

                  if(i != tickCount && i + jump > tickCount && showMax &&
                     jump != 0)
                  {
                     i = tickCount;
                  }
                  else {
                     i+= jump;
                  }
               }
               else {
                  if(showMax && i + jump > tickCount) {
                     i = tickCount;
                  }
                  else {
                     i+= jump;
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   /**
    * Get the jump of displaying values.
    */
   private int getJump() {
      String[] values = new String[(int) Math.floor((max - min) / inc)];

      for(int i = 0; i < values.length; i++) {
         values[i] = labels != null && labels[i] != null ?
            labels[i] : fixNum(min + i * inc);
      }

      for(int i = 1; i < values.length; i++) {
         if(!isValuesOverlapped(values, i)) {
            return i;
         }
      }

      return values.length;
   }

   private String fixNum(Object value) {
      NumberFormat nf = NumberFormat.getInstance();
      nf.setMaximumFractionDigits(decimalDigital);
      return nf.format(value);
   }

   /**
    * Check if the values overlap each other.
    * @param values the values array.
    * @param jump the jump of displaying values.
    */
   private boolean isValuesOverlapped(String[] values, int jump) {
      if(jump >= values.length) {
         return true;
      }

      double incWidthDelta =
         (getContentWidth() - LINE_TICK_GAP * 2.0) / values.length;

      for(int i = 0; i + jump < values.length; i += jump) {
         int length1 = fm.stringWidth(values[i]) + 4;
         int length2 = fm.stringWidth(values[i + jump]) + 4;

         if((length1 + length2) / 2 + VALUE_GAP > incWidthDelta * jump) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the width of the string.
    * @param str the specified string.
    */
   private int getLabelDecimalDigits(double minValue, double incValue) {
      String min = minValue + "";
      String inc = incValue + "";
      int minp = min.indexOf(".") == -1 ? min.length() : min.indexOf(".");
      int incp = inc.indexOf(".") == -1 ? inc.length() : inc.indexOf(".");

      return Math.max(min.length() - minp, inc.length() - incp);
   }

   private FontMetrics fm;
   private double min;
   private double max;
   private double inc;
   private String[] labels;
   private int decimalDigital;
   private static final int TICK_GAP = 3;
   private static final int TICK_HEIGHT = 3;
   private static final int VALUE_GAP = 5;
   private static final int LINE_TICK_GAP = 0;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSSlider.class);
}
