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
package inetsoft.report.io.viewsheet.svg;

import inetsoft.report.Painter;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.css.*;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Coordinate helper used when exporting as SVG.
 *
 * @since 12.1
 */
public class SVGCoordinateHelper extends CoordinateHelper {
   /**
    * Creates a new instance of <tt>SVGCoordinateHelper</tt>.
    */
   public SVGCoordinateHelper() {
      svgGraphics = SVGSupport.getInstance().createSVGGraphics();
      svgGraphics.setRenderingHint(Common.EXPORT_GRAPHICS, Common.VALUE_EXPORT_GRAPHICS_ON);
      svgBounds = new Rectangle(0, 0, 0, 0);
   }

   @Override
   public int getTitleHeightOffset(VSAssemblyInfo info) {
      return 0;
   }

   /**
    * Gets the graphics context.
    * @return the graphics.
    */
   Graphics2D getGraphics() {
      return svgGraphics;
   }

   /**
    * Sets the graphics context.
    * @param graphics the graphics.
    */
   void setGraphics(Graphics2D graphics) {
      svgGraphics = graphics;
   }

   /**
    * Final preparation for viewsheet export. Called once per viewsheet.
    */
   public void processViewsheet(Viewsheet sheet) {
      if(!sheet.isEmbedded()) {
         Dimension size = sheet.getPreferredSize(false, true);
         boolean adjustPaddingLeft = svgBounds.width < size.width;
         svgBounds.width = Math.max(svgBounds.width, size.width);
         svgBounds.height = Math.max(svgBounds.height, size.height);

         Assembly[] assemblies = sheet.getAssemblies(false, false, true, false, true);
         int top = svgBounds.height;
         int left = svgBounds.width;

         for(Assembly assembly : assemblies) {
            VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

            if(info != null) {
               Rectangle2D rect = getBounds(info);

               top = (int) Math.min(top, Math.max(0, rect.getY()));
               left = (int) Math.min(left, Math.max(0, rect.getX()));
            }
         }

         final int MARGIN = 8;
         left -= MARGIN;
         top -= MARGIN;
         svgBounds.width  = adjustPaddingLeft ? svgBounds.width += MARGIN : svgBounds.width;
         svgBounds.height += MARGIN;
         svgBounds.x = left;
         svgBounds.width = adjustPaddingLeft ?  svgBounds.width -= left : svgBounds.width;
         svgBounds.y = top;
         svgBounds.height -= top;

         init(svgBounds.width, svgBounds.height);

         // remove the top-left margin
         svgGraphics.translate(-left, -top);

         // Set background color
         TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
         VSCompositeFormat format = sheet.getFormatInfo().getFormat(path);
         Color bg = format.getBackground();

         if(bg == null) {
            bg = CSSDictionary.getDictionary().getBackground(
               new CSSParameter(CSSConstants.VIEWSHEET, null, null, null));
            int alpha = CSSDictionary.getDictionary().getAlpha(
               new CSSParameter(CSSConstants.VIEWSHEET, null, null, null));

            if(alpha != 100 && bg != null) {
               bg = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), alpha * 255 / 100);
            }
         }

         bg = bg == null ? Color.WHITE : bg;
         svgGraphics.setColor(bg);
         getGraphics().fillRect(-MARGIN, -MARGIN,
               svgBounds.width + left + 2 * MARGIN, svgBounds.height + top + 2 * MARGIN);
      }
   }

   /**
    * Perform any init code once the boundries are known. (Mostly for PNG)
    * @param width  of the png
    * @param height of the png
    */
   protected void init(int width, int height) {
   }

   /**
    * draw the image.
    * @param image the specified Image.
    * @param bounds the specified Bounds.
    */
   public void drawImage(Image image, Rectangle2D bounds) {
      drawImage(image, bounds, null);
   }

   /**
    * draw the image.
    * @param image the specified Image.
    * @param bounds the specified Bounds.
    */
   public void drawImage(Image image, Rectangle2D bounds, String alpha) {
      Shape shape = svgGraphics.getClip();
      svgGraphics.setClip(bounds);
      Common.drawImage(svgGraphics, image, (float) bounds.getX(),
                       (float) bounds.getY(), (float) bounds.getWidth(),
                       (float) bounds.getHeight(), null);

      if(alpha != null) {
         float alphaVal = 1.0f - Integer.parseInt(alpha) / 100.0f;
         svgGraphics.setColor(new Color(1.0f, 1.0f, 1.0f, alphaVal));
         svgGraphics.fill(bounds);
      }

      svgGraphics.setClip(shape);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    */
   public void drawTextBox(Rectangle2D bounds, VSCompositeFormat format, String dispText) {
      drawTextBox(bounds, format, dispText, true);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    */
   public void drawTextBox(Rectangle2D bounds, VSCompositeFormat format,
                           String dispText, Insets shapeBorders, Insets padding)
   {
      drawTextBox(bounds, bounds, format, dispText, shapeBorders, padding, false);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, Insets shapeBorders, Insets padding, boolean shadow)
   {
      ExportUtil.drawTextBox(svgGraphics, bounds, textBounds, format, dispText,
         true, false, shapeBorders, padding, shadow);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param paintBackground the background.
    */
   public void drawTextBox(Rectangle2D bounds, VSCompositeFormat format,
                           String dispText, boolean paintBackground)
   {
      drawTextBox(bounds, bounds, format, dispText, paintBackground);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param textBounds the displat text Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param paintBackground the background.
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, boolean paintBackground)
   {
      ExportUtil.drawTextBox(svgGraphics, bounds, textBounds, format, dispText,
         paintBackground, false, null, null);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param paintBackground the background.
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, boolean paintBackground, Insets padding)
   {
      ExportUtil.drawTextBox(svgGraphics, bounds, textBounds, format, dispText,
         paintBackground, false, null, padding);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param paintBackground the background.
    */
   public void drawTextBox(Rectangle2D bounds, VSCompositeFormat format,
                           String dispText, boolean paintBackground, Insets padding)
   {
      drawTextBox(bounds, bounds, format, dispText, paintBackground, padding);
   }

   /**
    * Writes the SVG image.
    *
    * @param output the output stream to which to write the image.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void write(OutputStream output) throws IOException {
      Dimension size = new Dimension(svgBounds.width, svgBounds.height);
      SVGSupport svgSupport = SVGSupport.getInstance();
      svgSupport.setCanvasSize(svgGraphics, size);
      Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      svgSupport.writeSVG(svgGraphics, writer);
      writer.flush();
   }

   /**
    * Write the table cell.
    */
   public void writeTableCell(int startX, int startY, Dimension bounds,
                              Rectangle2D pixelbounds, VSCompositeFormat format,
                              String dispText, Object dispObj, boolean needBG)
   {
      writeTableCell(startX, startY, bounds, pixelbounds, format, dispText,
                     dispObj, needBG, null, null);
   }

   /**
    * Write the table cell.
    */
   public void writeTableCell(int startX, int startY, Dimension bounds,
                              Rectangle2D pixelbounds, VSCompositeFormat format,
                              String dispText, Object dispObj,
                              boolean needBG, SVGVSExporter exporter,
                              java.util.List<VSAssemblyInfo> annos)
   {
      writeTableCell(startX, startY, bounds, pixelbounds, format, dispText, dispObj, needBG,
                     exporter, annos, null);
   }

   /**
    * Write the table cell.
    */
   public void writeTableCell(int startX, int startY, Dimension bounds,
                              Rectangle2D pixelbounds, VSCompositeFormat format,
                              String dispText, Object dispObj,
                              boolean needBG, SVGVSExporter exporter,
                              java.util.List<VSAssemblyInfo> annos,
                              Insets padding)
   {
      if(pixelbounds == null) {
         pixelbounds = getBounds(new Point(startX, startY), bounds);
      }

      if(dispObj instanceof Painter) {
         dispText = "";
      }

      if(exporter != null && annos != null) {
         int x = (int) pixelbounds.getX();
         int y = (int) pixelbounds.getY();
         int width = (int) pixelbounds.getWidth();
         int height = (int) pixelbounds.getHeight();
         int nx = x + width / 2;
         int ny = y + height / 2;

         for(VSAssemblyInfo anno : annos) {
            Point npos = AnnotationVSUtil.getNewPos(exporter.getViewsheet(),
                                                    anno, nx, ny);

            if(npos == null) {
               continue;
            }

            AnnotationVSUtil.refreshAnnoPosition(exporter.getViewsheet(), anno,
                                                 npos);
         }
      }

      drawTextBox(pixelbounds, pixelbounds, format, dispText, needBG, padding);

      if(dispObj instanceof Painter || dispObj instanceof Image) {
         try {
            // avoid covering border
            pixelbounds = new Rectangle2D.Double(
               pixelbounds.getX() + 1, pixelbounds.getY() + 1,
               pixelbounds.getWidth() - 1, pixelbounds.getHeight() - 1);
            BufferedImage img = ExportUtil.getPainterImage(
               dispObj, (int) pixelbounds.getWidth(),
               (int) pixelbounds.getHeight(), format);
            drawImage(img, pixelbounds);
         }
         catch(Exception ex) {
            LOG.error("Failed to draw painter image", ex);
         }
      }
   }

   private Graphics2D svgGraphics;
   private final Rectangle svgBounds;
   private static final Logger LOG = LoggerFactory.getLogger(SVGCoordinateHelper.class);
}
