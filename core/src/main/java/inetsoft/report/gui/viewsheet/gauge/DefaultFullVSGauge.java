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
package inetsoft.report.gui.viewsheet.gauge;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * The default implementation of the full circle gauge.
 *
 * @version 8.5, 2006-6-20
 * @author InetSoft Technology Corp
 */
public class DefaultFullVSGauge extends DefaultVSGauge {
   /**
    * The default label font.
    * */
   private static final Font DEFAULT_LABEL_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.BOLD, 18);

   /**
    * Set the gauge's scale.
    */
   @Override
   protected void setScale(double s) {
      super.setScale(s);

      labelY = labelY * scale;
      labelSize = new Dimension((int) (labelSize.width * scale),
         (int) (labelSize.height * scale));
   }

   /**
    * Get the gauge's image.
    */
   @Override
   public BufferedImage getContentImage() {
      BufferedImage image = super.getContentImage();

      if(getGaugeAssemblyInfo().isLabelVisible()) {
         Graphics2D g = (Graphics2D) image.getGraphics();
         RenderingHints hints = new RenderingHints(null);
         hints.put(RenderingHints.KEY_TEXT_ANTIALIASING,
         RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         g.setRenderingHints(hints);
         drawValueLabel(g);
         g.dispose();
      }

      return image;
   }

   /**
    * Get the gauge's image.
    */
   @Override
   public Graphics2D getContentSvg(boolean isShadow) {
      Graphics2D svgGraphics = super.getContentSvg(isShadow);

      if(getGaugeAssemblyInfo().isLabelVisible()) {
         Graphics2D g = (Graphics2D) svgGraphics.create();
         RenderingHints hints = new RenderingHints(null);
         hints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         g.setRenderingHints(hints);
         drawValueLabel(g);
         g.dispose();
      }

      return svgGraphics;
   }

   /**
    * Draw the value label.
    */
   protected void drawValueLabel(Graphics2D g) {
      Font originFont = g.getFont();
      Color originColor = g.getColor();
      GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
      String content = formatTotalValue(info);
      g.setFont(adjustValueLabelFont());

      try {
         // merger label background.
         if(SVGSupport.getInstance().isSVGGraphics(g)) {
            AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
            SVGSupport.getInstance().mergeSVGDocument(g, super.getPathUrl(labelPath), transform);
         }
         else {
            g.drawImage(getLabelBackground(), 0, 0, null);
         }

         drawString(g, content);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      g.setFont(originFont);
      g.setColor(originColor);
   }

   static String formatTotalValue(GaugeVSAssemblyInfo info) {
      return VSImageable.formatValue(info, getTotalValue(info), true);
   }

   static double getTotalValue(GaugeVSAssemblyInfo info) {
      double value = info.getDoubleValue();

      if(info.getBindingInfo() == null || info.getBindingInfo() != null &&
         info.getBindingInfo().getTableName() == null)
      {
         // the value may be set by script
         value = Math.max(info.getMin(), value);
         value = Math.min(info.getMax(), value);
      }

      return value;
   }

   /**
    * Get the label's background.
    * @return BufferedImage.
    */
   protected BufferedImage getLabelBackground() throws Exception {
      AffineTransform transform =
         AffineTransform.getScaleInstance(scale, scale);
      return getImageByURI(labelPath, transform);
   }

   /**
    * Draw the value label.
    */
   private void drawString(Graphics2D g, String content) throws Exception {
      Color originColor = g.getColor();
      FontMetrics fm = Common.getFontMetrics(g.getFont());
      Rectangle2D bounds = fm.getStringBounds(content, g);
      double stringWidth = Math.abs(bounds.getWidth());
      double stringHeight = Math.abs(bounds.getHeight());
      double dy = (labelSize.getHeight() - stringHeight) / 2 +
         stringHeight - fm.getDescent();
      float x = (float) (contentSize.getWidth() / 2 - stringWidth / 2);
      float y = (float) (labelY + dy);
      VSCompositeFormat fmt = valueFmt;
      Color color = null;

      if(valueLabelColor != null) {
         color = valueLabelColor;
      }
      else {
         color = fmt.getForeground();
      }

      if(fmt != null) {
         int align = fmt.getAlignment();
         boolean left = (align & StyleConstants.H_LEFT) != 0;
         boolean right = (align & StyleConstants.H_RIGHT) != 0;
         boolean top = (align & StyleConstants.V_TOP) != 0;
         boolean bottom = (align & StyleConstants.V_BOTTOM) != 0;
         float labelx = (float) (contentSize.getWidth() - labelSize.width) / 2;

         if(left) {
            x = labelx;
         }
         else if(right) {
            x = (float) (labelx + labelSize.width - stringWidth);
         }

         if(top) {
            y = (float) (labelY + stringHeight - fm.getDescent());
         }
         else if(bottom) {
            y = (float) (labelY + labelSize.height - fm.getDescent());
         }
      }

      g.setColor(color);
      Common.drawString(g, content, x, y);
      g.setColor(originColor);
   }

   /**
    * Adjust the label font due to the label size and the label value.
    */
   private Font adjustValueLabelFont() {
      final GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
      final Viewsheet vs = info.getViewsheet();
      String[] labels;

      if(vs != null) {
         // if there are multiple gauge with same size and same gauage face,
         // try to use same font size for the total label by adjusting font for
         // all labels. otherwise the font will be different and look out of sync.
         labels = Arrays.stream(vs.getAssemblies(false))
            .filter(a -> a instanceof GaugeVSAssembly)
            .map(a -> (GaugeVSAssemblyInfo) a.getInfo())
            .filter(info2 -> info.getFace() == info2.getFace() &&
               Math.min(info.getPixelSize().width, info.getPixelSize().height) ==
                  Math.min(info2.getPixelSize().width, info2.getPixelSize().height))
            .map(a -> VSImageable.formatValue(a, a.getDoubleValue(), true))
            .toArray(String[]::new);
      }
      else {
         labels = new String[0];
      }

      Font font = valueFmt.getFont();

      if(font == null) {
         font = valueLabelFont;
      }
      else {
         font = new StyleFont(font);
         // make the value label font bold by default if inheriting
         // the tick font
         font = font.deriveFont(font.getStyle() | Font.BOLD);
      }

      if(font == null) {
         font = DEFAULT_LABEL_FONT;
      }

      final float maxSize = Math.max(24f, labelSize.height);
      font = font.deriveFont(maxSize); // display as large as possible
      FontMetrics fm = Common.getFontMetrics(font);

      while(maxWidth(fm, labels) > labelSize.width - 2 || fm.getHeight() > labelSize.height) {
         font = reduceSize(font);
         fm = Common.getFontMetrics(font);
      }

      font = font.deriveFont(font.getSize() * valueFmt.getRScaleFont());

      return font;
   }

   private static double maxWidth(FontMetrics fm, String ...labels) {
      return Arrays.stream(labels).mapToDouble(fm::stringWidth).max().orElse(0);
   }

   /**
    * Parse the XML node.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      super.parseXML(node);

      // parse label tag
      Element lNode = Tool.getChildNodeByTagName(node, "label");
      labelY = Double.parseDouble(Tool.getAttribute(lNode, "y"));

      double width = Double.parseDouble(Tool.getAttribute(lNode, "width"));
      double height = Double.parseDouble(Tool.getAttribute(lNode, "height"));
      labelSize = new Dimension((int) width, (int) height);
      String colorstr = Tool.getAttribute(lNode, "color");

      if(colorstr != null) {
         valueLabelColor = parseColor(colorstr);
      }

      labelPath = Tool.getChildValueByTagName(lNode, "labelImage");
      String fontString = Tool.getChildValueByTagName(lNode, "font");

      valueLabelFont = parseFont(fontString, null);
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      DefaultFullVSGauge gauge = (DefaultFullVSGauge) super.clone();
      gauge.labelSize = labelSize == null ?
         null : (Dimension) labelSize.clone();
      return gauge;
   }

   private double labelY;
   private Dimension labelSize;
   private Font valueLabelFont;
   private Color valueLabelColor;
   private String labelPath;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultFullVSGauge.class);
}
