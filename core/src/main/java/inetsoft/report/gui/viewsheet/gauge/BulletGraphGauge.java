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
package inetsoft.report.gui.viewsheet.gauge;

import inetsoft.report.StyleConstants;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.painter.BulletGraphPresenter;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.graphics.SVGSupport;

import java.awt.*;
import java.awt.image.BufferedImage;

import static inetsoft.report.gui.viewsheet.VSFaceUtil.createDropShadow;

/**
 * VSGauge component for viewsheet.
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class BulletGraphGauge extends VSGauge {
   @Override
   public BufferedImage getContentImage() {
      Dimension size = getSize();
      BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D g = (Graphics2D) img.getGraphics();

      return paintContent(img, g, size, isShadow());
   }

   @Override
   public Graphics2D getContentSvg(boolean isShadow) {
      Graphics2D g = SVGSupport.getInstance().createSVGGraphics();
      paint0(g, getSize(), isShadow);
      g.dispose();
      return g;
   }

   private void paint0(Graphics2D g, Dimension size, boolean isShadow) {
      GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
      VSCompositeFormat fmt = info.getFormat();
      Insets padding = info.getPadding();
      int x = 0, y = 0, w = size.width, h = size.height;

      g.setFont(info.getFormat().getFont());
      g.setColor(info.getFormat().getForeground());

      x += padding.left;
      y += padding.top;
      w -= padding.left + padding.right;
      h -= padding.top + padding.bottom;
      int y0 = y;
      int h0 = h;

      final int mainPrefH = 23;
      int fontH = g.getFontMetrics().getHeight();

      BulletGraphPresenter presenter = new BulletGraphPresenter();
      presenter.setMinimum(info.getMin());
      presenter.setMaximum(info.getMax());
      presenter.setTarget(info.getTarget());
      presenter.setBarColor(info.getValueFillColor());
      presenter.setLabelVisible(info.isLabelVisible());
      presenter.setShadow(isShadow);

      if(labelFmt != null && labelFmt.getFormat() != null) {
         presenter.setFormat(TableFormat.getFormat(labelFmt.getFormat(),
                                                   labelFmt.getFormatExtent()));
      }

      if(presenter.getBarColor() == null) {
         presenter.setBarColor(Color.GRAY.darker());
      }

      if(presenter.isLabelVisible()) {
         if(h > mainPrefH + fontH) {
            y0 = y + (h - mainPrefH - fontH) / 2;
            h0 = mainPrefH + fontH;
         }
      }
      else if(h > mainPrefH) {
         y0 = y + (h - mainPrefH) / 2;
         h0 = mainPrefH;
      }

      int valign = fmt.getAlignment();

      if((valign & StyleConstants.V_TOP) != 0) {
         y0 = y;
      }
      else if((valign & StyleConstants.V_BOTTOM) != 0) {
         y0 = y + h - h0;
      }

      double[] ranges = info.getRanges();

      if(ranges != null) {
         Color[] colors = info.getRangeColors();

         if(ranges.length > 0) {
            presenter.setRange1(ranges[0]);
            presenter.setColor1(colors[0]);
         }

         if(ranges.length > 1) {
            presenter.setRange2(ranges[1]);
            presenter.setColor2(colors[1]);
         }

         if(ranges.length > 2) {
            presenter.setRange3(ranges[2]);
            presenter.setColor3(colors[2]);
         }
      }

      Object val = info.getValue();
      presenter.paint(g, val == null ? 0 : val, x, y0, w, h0);
   }

   private BufferedImage paintContent(BufferedImage img, Graphics g, Dimension size, boolean isShadow) {
      paint0((Graphics2D) g, size, isShadow);
      g.dispose();

      if(isShadow == true) {
         img = addShadow(img, 6);

         BufferedImage labelImg = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
         Graphics labelGraphics = labelImg.getGraphics();
         paint0((Graphics2D) labelGraphics, size, false);
         labelGraphics.dispose();

         BufferedImage finalImage = new BufferedImage(img.getWidth() + 6, img.getHeight() + 6,
                                               BufferedImage.TYPE_INT_ARGB);
         Graphics finalGraphics = finalImage.getGraphics();
         finalGraphics.drawImage(labelImg, 0, 0, null);
         finalGraphics.drawImage(img, 0, 0, null);
         finalGraphics.dispose();

         return finalImage;
      }

      return img;
   }

   private static BufferedImage addShadow(BufferedImage img, int edge) {
      int w = img.getWidth();
      int h = img.getHeight();

      BufferedImage out = new BufferedImage(w + edge, h + edge,
                                            BufferedImage.TYPE_INT_ARGB);
      BufferedImage shadow = createDropShadow(img, edge / 3);
      Graphics g = out.getGraphics();
      g.drawImage(shadow, 0, 0, null);
      g.drawImage(img, 0, 0, null);
      g.dispose();

      return out;
   }
}
