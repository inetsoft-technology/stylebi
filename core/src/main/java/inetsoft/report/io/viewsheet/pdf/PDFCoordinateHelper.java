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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.*;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.pdf.PDF4Generator;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.css.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * The class is used to calculate the bounds and drawing.
 *
 * @version 10.1, 13/01/2009
 * @author InetSoft Technology Corp
 */
public class PDFCoordinateHelper extends CoordinateHelper {
   /**
    * Constructor.
    * @param out the specified OutputStream.
    */
   public PDFCoordinateHelper(OutputStream out) {
      printer = PDF4Generator.getPDFGenerator(out).getPrinter();
      Color bg = CSSDictionary.getDictionary().getBackground(
         new CSSParameter(CSSConstants.VIEWSHEET, null, null, null));
      int alpha = CSSDictionary.getDictionary().getAlpha(
         new CSSParameter(CSSConstants.VIEWSHEET, null, null, null));

      if(alpha != 100 && bg != null) {
         bg = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), alpha * 255 / 100);
      }

      printer.setBackground(bg);
   }

   @Override
   public int getTitleHeightOffset(VSAssemblyInfo info) {
      return 0;
   }

   /**
    * create the pdf page.
    * @param size the specified Dimension.
    */
   public void createPage(Dimension size) {
      page++;
      printer.dispose();
      // @by stephenwebster, For bug1409232608443
      // Removed minimum limit on table rows generated for the PDF
      // in PDFVSExporter.expandTable, and set the maximum page size to 200 here.
      // This limitation is well documented in the PDF specification.
      // @by stephenwebster, For Bug #9428, set exact size of viewsheet to avoid loss of precision
      // fixed bug #20360 , bug #23060 that add 1px border
      printer.setPageSize(Math.min(size.width + 1, 200 * printer.RESOLUTION),
                          Math.min(size.height + 1, 200 * printer.RESOLUTION));
      printer.setColor(printer.getBackground());

      if(size.height > 200 * printer.RESOLUTION) {
         printer.setOutOfMaxPageSize(true);
      }

      printer.fillRect(0, 0, printer.getPageSize().width * printer.RESOLUTION,
                       printer.getPageSize().height * printer.RESOLUTION);
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
      Shape shape = printer.getClip();
      printer.setClip(bounds);
      printer.startArtifact();
      Common.drawImage(printer, image, (float) bounds.getX(),
                       (float) bounds.getY(), (float) bounds.getWidth(),
                       (float) bounds.getHeight(), null);

      if(alpha != null) {
         float alphaVal = 1.0f - Integer.parseInt(alpha) / 100.0f;
         printer.setColor(new Color(1.0f, 1.0f, 1.0f, alphaVal));
         printer.fill(bounds);
      }

      printer.endArtifact();
      printer.setClip(shape);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    */
   public void drawTextBox(Rectangle2D bounds, VSCompositeFormat format,
                           String dispText) {
      drawTextBox(bounds, format, dispText, true);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, Insets shapeBorders, Insets padding) {
      drawTextBox(bounds, textBounds, format, dispText, shapeBorders, padding, false);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param shadow the specified shadow.
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, Insets shapeBorders, Insets padding, boolean shadow) {
      ExportUtil.drawTextBox(printer, bounds, textBounds , format, dispText,
         true, isLinkExists(bounds), shapeBorders, padding, shadow);
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
    * @param textBounds the display text Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param paintBackground the background.
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, boolean paintBackground)
   {
      drawTextBox(bounds, textBounds, format, dispText, paintBackground, null);
   }

   /**
    * draw the textbox.
    * @param bounds the specified Bounds.
    * @param textBounds the display text Bounds.
    * @param format the specified VSFormat.
    * @param dispText the specified String.
    * @param paintBackground the background.
    * @param padding text padding
    */
   public void drawTextBox(Rectangle2D bounds, Rectangle2D textBounds, VSCompositeFormat format,
                           String dispText, boolean paintBackground, Insets padding)
   {
      ExportUtil.drawTextBox(printer, bounds, textBounds, format, dispText,
         paintBackground, isLinkExists(bounds), null, padding);
   }

   /**
    * Write the in-mem document (workbook or show) to pdfprinter.
    */
   public void write() {
      printer.dispose();
   }

   /**
    * Get printer.
    */
   public PDFPrinter getPrinter() {
      return printer;
   }

   /**
    * Get the page count.
    */
   int getPage() {
      return page;
   }

   /**
    * Is link exists in the bounds.
    */
   private boolean isLinkExists(Object bounds) {
      LinkedHashMap<Object, Hyperlink.Ref> links = getLinks(page);

      if(links == null) {
         return false;
      }

      return links.containsKey(bounds);
   }

   /**
    * Set hyperlink to links.
    */
   public void setLinks(Object bounds, Hyperlink.Ref hyperlink) {
      LinkedHashMap<Object, Hyperlink.Ref> links = getLinks(page);

      if(links == null) {
         linksMap.put(page, new LinkedHashMap<>());
         links = getLinks(page);
      }

      if(links.containsKey(bounds)) {
         links.remove(bounds) ;
      }

      if(hyperlink == null) {
         return;
      }

      links.put(bounds, hyperlink);
   }

   /**
    * Get links.
    */
   public LinkedHashMap<Object, Hyperlink.Ref> getLinks(int page) {
      return linksMap.get(page);
   }

   /**
    * Write the table cell.
    */
   public void writeTableCell(int startX, int startY, Dimension bounds,
                              Rectangle2D pixelbounds,
                              VSCompositeFormat format,
                              String dispText, Object dispObj,
                              Hyperlink.Ref hyperlink, boolean needBG)
   {
      writeTableCell(startX, startY, bounds, pixelbounds, format, dispText,
                     dispObj, hyperlink, needBG, null, null);
   }

   /**
    * Write the table cell.
    */
   public void writeTableCell(int startX, int startY, Dimension bounds,
                              Rectangle2D pixelbounds,
                              VSCompositeFormat format,
                              String dispText, Object dispObj,
                              Hyperlink.Ref hyperlink, boolean needBG,
                              PDFVSExporter exporter,
                              java.util.List<VSAssemblyInfo> annos)
   {
      writeTableCell(startX, startY, bounds, pixelbounds, format, dispText, dispObj, hyperlink,
                     needBG, exporter, annos, null);
   }

   /**
    * Write the table cell.
    */
   public void writeTableCell(int startX, int startY, Dimension bounds,
                              Rectangle2D pixelbounds,
                              VSCompositeFormat format,
                              String dispText, Object dispObj,
                              Hyperlink.Ref hyperlink, boolean needBG,
                              PDFVSExporter exporter,
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

      setLinks(pixelbounds, hyperlink);
      drawTextBox(pixelbounds, pixelbounds, format, dispText, needBG, padding);

      if(dispObj instanceof Painter || dispObj instanceof Image) {
         try {
            // avoid covering border
            pixelbounds = new Rectangle2D.Double(
               pixelbounds.getX() + 1, pixelbounds.getY() + 1,
               pixelbounds.getWidth() - 1, pixelbounds.getHeight() - 1);

            if(dispObj instanceof Painter) {
               ExportUtil.printPresenter(
                  dispObj, (int) pixelbounds.getWidth(),
                  (int) pixelbounds.getHeight(), format, getPrinter(), pixelbounds);
            }
            else {
               BufferedImage img = ExportUtil.getPainterImage(
                  dispObj, (int) pixelbounds.getWidth(),
                  (int) pixelbounds.getHeight(), format);
               drawImage(img, pixelbounds);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to draw painter image", ex);
         }
      }
   }

   /**
    * Sets whether the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @param printOnOpen <tt>true</tt> to print when opened.
    */
   public void setPrintOnOpen(boolean printOnOpen) {
      getPrinter().setPrintOnOpen(printOnOpen);
   }

   /**
    * Determines if the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @return <tt>true</tt> to print when opened.
    */
   public boolean isPrintOnOpen() {
      return getPrinter().isPrintOnOpen();
   }

   private HashMap<Integer, LinkedHashMap<Object, Hyperlink.Ref>> linksMap = new HashMap<>();
   private PDFPrinter printer;
   private int page = -1;
   private static final Logger LOG =
      LoggerFactory.getLogger(PDFCoordinateHelper.class);
}
