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
package inetsoft.report.gui.viewsheet;

import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;

import java.awt.*;

/**
 * VSTimeSlider component for view sheet.
 *
 * @version 8.5, 07/26/2006
 * @author InetSoft Technology Corp
 */
public class VSTimeSlider extends VSFloatable {
   /**
    * Constructor.
    */
   public VSTimeSlider(Viewsheet vs) {
      super(vs);
   }

   @Override
   protected Dimension getImageSize() {
      Dimension size = super.getImageSize();
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) getAssemblyInfo();

      if(info != null) {
         boolean maxmin = info.isMinVisible() || info.isMaxVisible();

         if(maxmin) {
            int h = (int) (getContentHeight() - getBW(TOP) - getBW(BOTTOM));
            VSCompositeFormat format = info.getFormat();
            int lineY = maxmin ? (h / 2 - SLIDER_H / 2) : (h * 3 / 4 - SLIDER_H / 2);
            int fontH = 10;

            if(format.getFont() != null) {
               fontH = Common.getFontMetrics(format.getFont()).getHeight();
            }

            // on html, the labels can be drawn out of bounds (not clipped), so we enlarge
            // the image to accommodate the labels to mimic the same effect.
            return new Dimension(size.width, Math.max(size.height, lineY + SLIDER_H + fontH));
         }
      }

      return size;
   }

   /**
    * Paint the component.
    */
   @Override
   public void paintComponent(Graphics2D g) {
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) getAssemblyInfo();

      if(info == null) {
         return;
      }

      TimeSliderVSAssembly assembly = (TimeSliderVSAssembly)
         getViewsheet().getAssembly(info.getAbsoluteName());
      Viewsheet vs = this.getViewsheet();

      while(vs.getViewsheet() != null && assembly == null) {
         assembly = (TimeSliderVSAssembly)
            vs.getViewsheet().getAssembly(info.getAbsoluteName());
         vs = vs.getViewsheet();
      }

      VSCompositeFormat format = info.getFormat();
      TimeInfo tinfo = info.getTimeInfo();
      int w = (int) (getContentWidth() - getBW(LEFT) - getBW(RIGHT));
      int h = (int) (getContentHeight() - getBW(TOP) - getBW(BOTTOM));
      int buttonW = 9;
      int buttonH = 15;
      int lineX = (int) Math.ceil(buttonW / 2);
      int lineW = w - lineX * 2;

      int sliderW = info.getTotalLength() == 0 ? buttonW :
                    info.getTotalLength() == 1 ? lineW :
                    tinfo.getLength() * lineW / (info.getTotalLength() - 1);
      int sliderX = info.getTotalLength() == 0 ?
         lineX + info.getCurrentPos() * lineW :
         (info.getTotalLength() == 1 ? lineX :
         lineX + info.getCurrentPos() * lineW / (info.getTotalLength() - 1));
      boolean maxmin = info.isMinVisible() || info.isMaxVisible();
      int lineY = maxmin ? (h / 2 - SLIDER_H / 2) : (h * 3 / 4 - SLIDER_H / 2);

      g = (Graphics2D) g.create();
      g.translate(getContentX(), getContentY());

      // draw slider line
      Image track = getTheme().getImage("widget|SliderBase", "sliderTrack", lineW, SLIDER_H);
      g.drawImage(track, lineX, lineY, null);

      // draw ticks
      double tickW = info.getTotalLength() < 2 ? (double) lineW :
         (double) lineW / (info.getTotalLength() - 1);
      int minTickW = Math.max(8, lineW / 40);
      double lastX = -99999;
      g.setColor(new Color(0x888888));

      if(assembly.isTickVisible()) {
         for(double tickX = 0; tickX <= lineW; tickX += tickW) {
            if(tickX - lastX < minTickW) {
               continue;
            }

            g.drawLine((int) tickX + lineX, lineY + SLIDER_H,
                       (int) tickX + lineX, lineY + SLIDER_H + 3);
            lastX = tickX;
         }
      }

      // draw slider button
      Image sliderButton = getTheme().getImage("widget|SliderBase", "thumbUp",
                                               sliderW, SLIDER_H);
      g.drawImage(sliderButton, sliderX, lineY, null);

      // draw left/right button
      Image btn = getTheme().getImage("widget|SliderBase", "buttonUp",
                                      buttonW, buttonH);
      // for btn width is 9, so when paint, should make sure the button images
      // is in bounds, so in left, should -4, and in right, should -5
      g.drawImage(btn, sliderX - 4, lineY - (buttonH - SLIDER_H) / 2, null);
      g.drawImage(btn, sliderX + sliderW - 5, lineY - (buttonH - SLIDER_H) / 2, null);

      if(format == null) {
         format = new VSCompositeFormat();
      }

      if(format.getFont() != null) {
         g.setFont(format.getFont());
      }

      if(format.getForeground() != null) {
         g.setColor(format.getForeground());
      }
      else {
         g.setColor(Color.BLACK);
      }

      FontMetrics fm = g.getFontMetrics();

      if(info.isCurrentVisible()) {
         String txt = assembly.getDisplayValue(false);
         txt = txt == null ? "" : txt;
         int sw = fm.stringWidth(txt);
         int tx = sliderX + sliderW / 2 - sw / 2;

         tx = Math.min(tx, w - sw);
         tx = Math.max(tx, 0);

         Common.drawString(g, txt, tx, lineY - fm.getDescent() - 2);
      }

      SelectionList slist = info.getSelectionList();

      if(slist != null && info.isMinVisible()) {
         SelectionValue[] values = slist.getSelectionValues();

         if(values != null && values.length > 0) {
            String label = values[0].getLabel();
            label = getShrinkString(buttonW, g, label);
            Common.drawString(g, label, 0, lineY + fm.getAscent() + SLIDER_H + 2);
         }
      }

      if(slist != null && info.isMaxVisible()) {
         SelectionValue[] values = slist.getSelectionValues();

         if(values != null && values.length > 0) {
            String label = values[values.length - 1].getLabel();
            label = getShrinkString(buttonW, g, label);
            Common.drawString(g, label, w - fm.stringWidth(label),
                              lineY + fm.getAscent() + SLIDER_H + 2);
         }
      }

      g.dispose();
   }

   /**
    * Get the curtate label for overlapping strings.
    */
   private String getShrinkString(int buttonW, Graphics2D g, String label) {
      int w = getContentWidth();
      int h = getContentHeight();
      g = (Graphics2D) g.create(getContentX(), getContentY(), w, h);
      FontMetrics fm = g.getFontMetrics();
      int lineX = (int) Math.ceil(buttonW / 2);
      int lineW = w - lineX * 2;

      if(fm.stringWidth(label) > lineW / 2) {
         for(int i = 1; i <= label.length() - 1 ; i++) {
            if(fm.stringWidth(label.substring(0, i)) >= lineW / 2 - 5) {
               label = label.substring(0, i - 1) + "...";
               break;
            }
         }
      }

      return label;
   }

   private static final int SLIDER_H = 9;
}
