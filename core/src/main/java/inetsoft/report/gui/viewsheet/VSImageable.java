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
import inetsoft.report.gui.viewsheet.gauge.BulletGraphGauge;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ExtendedDecimalFormat;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.text.*;
import java.util.StringTokenizer;

/**
 * VSImageable component for viewsheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class VSImageable extends VSFloatable {
   /**
    * Max major tick count.
    */
   public static final int MAX_MAJOR_TICK_COUNT = 200;

   /**
    * Constructor.
    */
   public VSImageable(Viewsheet vs) {
      super(vs);
   }

   /**
    * Get the default size.
    * @return the default size.
    */
   public Dimension getDefaultSize() {
      return defaultSize;
   }

   /**
    * Paint the content of the image.
    */
   @Override
   protected void paintComponent(Graphics2D g) {
      BufferedImage img = getContentImage();
      Point startPoint = getPosition();

      if(img != null) {
         if(isShadow() && !(this instanceof BulletGraphGauge)) {
            img = VSFaceUtil.addShadow(img, 6);
         }

         g.drawImage(img, startPoint.x, startPoint.y,
                     img.getWidth(), img.getHeight(), null);
      }
   }

   /**
    * Get the content image.
    * @return image.
    */
   public abstract BufferedImage getContentImage();

   /**
    * Get the background image with panel.
    */
   protected BufferedImage createPanelImage(int width, int height) {
      BufferedImage background =
         new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
      Color bg = getBackground();

      // force bg otherwise anti-aliasing doesn't work. Since jpg doesn't
      // support transparency anyway, this doesn't lose any functionality.
      /* @by ashur image as background
      if(bg == null) {
         bg = Color.WHITE;
      }*/

      if(bg != null) {
         Graphics g = background.getGraphics();

         g.setColor(bg);
         g.fillRect(0, 0, width, height);
         g.dispose();
      }

      return background;
   }

   /**
    * Get component foreground color.
    */
   protected Color getForeground() {
      if(fg == null) {
         fg = valueColor;
         VSCompositeFormat fmt = getAssemblyInfo().getFormat();

         if(fmt != null && fmt.getForeground() != null) {
            fg = fmt.getForeground();
         }
      }

      return fg;
   }

   /**
    * Get component background color.
    */
   protected Color getBackground() {
      VSCompositeFormat fmt = getAssemblyInfo().getFormat();

      return fmt == null ? null : fmt.getBackground();
   }

   /**
    * Get component font.
    */
   protected Font getFont() {
      VSCompositeFormat fmt = getAssemblyInfo().getFormat();

      if(fmt != null && fmt.getFont() != null) {
         return fmt.getFont();
      }

      return valueFont;
   }

   /**
    * Format a number to string.
    */
   protected String formatValue(double val, boolean total) {
      VSAssemblyInfo info = getAssemblyInfo();
      return formatValue(info, val, total);
   }

   public static String formatValue(VSAssemblyInfo info, double val, boolean total) {
      VSCompositeFormat fmt = info.getFormat();
      Format format = null;

      if(fmt != null && fmt.getFormat() != null) {
         try {
            String spec = fmt.getFormatExtent();

            // fix bug1236349623404 by shirlyg, reset the format pattern in
            // order to display clearly of ticks label
            if(!total) {
               int cidx = spec == null ||
                  "".equals(spec) ? -1 : spec.indexOf(";(");

               if(cidx >= 0) {
                  String first = spec.substring(0, cidx);
                  String second = spec.substring(cidx, spec.length());

                  if(first != null && !"".equals(first)) {
                     int idx = first.indexOf(".");
                     first = validateFormatPattern(first, idx);
                  }

                  if(second != null && !"".equals(second)) {
                     int idx = second.lastIndexOf(".");
                     second = validateFormatPattern(second, idx);
                  }

                  spec = first + second;
               }
               else {
                  int idx = spec == null ? -1 : spec.lastIndexOf(".");

                  if(idx >= 0) {
                     spec = validateFormatPattern(spec, idx);
                  }
               }
            }

            format = TableFormat.getFormat(fmt.getFormat(), spec);

            if(info instanceof RangeOutputVSAssemblyInfo &&
               format instanceof ExtendedDecimalFormat)
            {
               format = ((ExtendedDecimalFormat) format)
                  .setIncrement(((RangeOutputVSAssemblyInfo) info).getMajorInc());
            }

            if(fmt.getFormat().equals(TableFormat.CURRENCY_FORMAT) && !total) {
               String pattern = ((DecimalFormat) format).toPattern();
               int idx = pattern.lastIndexOf(".");

               if(idx >= 0) {
                  pattern = validateFormatPattern(pattern, idx);
                  ((DecimalFormat) format).applyPattern(pattern);
               }
            }
         }
         catch(Exception ex) {
            LOG.debug(ex.getMessage(), ex);
         }
      }

      if(format == null && info instanceof OutputVSAssemblyInfo) {
         Format defaultFmt = ((OutputVSAssemblyInfo) info).getDefaultFormat();

         if(defaultFmt instanceof NumberFormat) {
            format = defaultFmt;
         }
      }

      if(format == null) {
         if(val == (int) val) {
            return Integer.toString((int) val);
         }
         else {
            format = decimalFormat;
         }
      }

      return format.format(val);
   }

   /**
    * Get the new format pattern of ticks.
    */
   private static String validateFormatPattern(String pattern, int idx) {
      String bstr = pattern.substring(0, idx);
      String astr = pattern.substring(idx, pattern.length());
      pattern = bstr + astr.replace("0", "#");

      return pattern;
   }

   /**
    * Method to parse an xml segment.
    * @param node the XML representation of this object.
    */
   protected void parseXML(Element node) throws Exception {
      Element valueNode = Tool.getChildNodeByTagName(node, "value");

      if(valueNode != null) {
         valueFont = parseFont(Tool.getChildValueByTagName(valueNode, "font"),
                               DEFAULT_FONT);
         valueColor = parseColor(Tool.getAttribute(valueNode, "color"));

         if(valueColor == null) {
            valueColor = Color.BLACK;
         }

         // get the radius of the tick's value
         String valueRadiusStr = Tool.getAttribute(valueNode, "valueRadius");

         if(valueRadiusStr != null) {
            valueRadius = Double.parseDouble(valueRadiusStr);
         }

         valueVisible = !"false".equals(Tool.getAttribute(valueNode, "visible"));
      }
   }

   /**
    * Parse the font from the XML attribute.
    * @param f the font description, it should be the format of the
    *           StyleFont's toString() method's result.
    * @param defaultFont default font.
    * @return return the font, if not successful, it return the default font!
    */
   protected StyleFont parseFont(String f, Font defaultFont) {
      Font font = StyleFont.decode(f);

      if(font == null) {
         font = defaultFont;
      }

      return font != null ? new StyleFont(font) : null;
   }

   /**
    * Parse the color from the XML attribute.
    * @param c the color stored in the XML, it is should be formated as
    *          alpha, red, green, blue.
    * @return the color if successful.
    */
   protected Color parseColor(String c) {
      StringTokenizer token = new StringTokenizer(c, ",");
      int alpha = 0;
      int red = 0;
      int blue = 0;
      int green = 0;

      try {
         alpha = Integer.parseInt(token.nextToken());
         red = Integer.parseInt(token.nextToken());
         green = Integer.parseInt(token.nextToken());
         blue = Integer.parseInt(token.nextToken());
         return new Color(red, green, blue, alpha);
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Get the start point of the imageable.
    */
   protected Point getPosition() {
      return new Point(0, 0);
   }

   /**
    * Get value font.
    */
   protected StyleFont getValueFont() {
      return valueFont;
   }

   /**
    * Get value color.
    */
   protected Color getValueColor() {
      return valueColor;
   }

   /**
    * To avoid problems about weblogic and websphere error.
    * Weblogic confuses jar and zip url, websphere confuses jar and wsjar url.
    * Reference http://article.gmane.org/gmane.text.xml.cocoon.devel/75461.
    * If url starts with 'zip', replace the 'zip' to 'jar:file:'.
    * If url starts with 'wsjar', replace the 'wsjar' to 'jar'.
    */
   protected static String getConvertURLString(URL url) {
      String urlString = url + "";
      int index = urlString.indexOf("zip:");

      // fix weblogic url
      if(index == 0) {
         urlString = "jar:file" + urlString.substring(3);
      }

      // fix websphere url
      if(urlString.startsWith("wsjar:")) {
         urlString = urlString.substring(2);
      }

      return urlString;
   }

   /**
    * If the labels are showing K/M/B, only display the suffix on the
    * first label.
    */
   protected String[] abbreviate(String[] labels) {
      if(labels.length <= 1) {
         return labels;
      }

      String[] suffixs = {"K", "M", "B"};
      String suffix = null;

      for(String str : suffixs) {
         if(labels[0].endsWith(str)) {
            suffix = str;
            break;
         }
      }

      if(suffix == null) {
         return labels;
      }

      for(int i = 1; i < labels.length; i++) {
         if(labels[i].endsWith(suffix)) {
            labels[i] = labels[i].substring(0, labels[i].length() - 1);
         }
      }

      return labels;
   }

   // default values
   private final Font DEFAULT_FONT = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10);
   private static DecimalFormat decimalFormat = new DecimalFormat();

   static {
      decimalFormat.setMaximumFractionDigits(4);
      decimalFormat.setGroupingUsed(false);
   }

   protected Dimension defaultSize = new Dimension(0, 0);
   protected double valueRadius = 0; // the radius of the tick's value
   protected boolean valueVisible = true;

   private StyleFont valueFont;
   private Color valueColor;
   private Color fg;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSImageable.class);
}
