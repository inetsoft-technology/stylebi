/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.report.*;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Common;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.io.viewsheet.VSFontHelper;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.StrokeStyle.LineDash;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.openxmlformats.schemas.drawingml.x2006.main.*;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Common utility methods for powerpoint export.
 *
 * @version 8.5, 8/7/2006
 * @author InetSoft Technology Corp
 */
public class PPTVSUtil {
   /**
    * Pixel to point factor. 72 / 96 = 0.75
    * Refer to org.apache.poi.hslf.model.Shape.
    * PIXEL_DPI: Pixels DPI (96 pixels per inch).
    * POINT_DPI: Points DPI (72 pixels per inch).
    */
   public static final double PIXEL_TO_POINT = 0.75;

   /**
    * apply the viewsheet border dashstyle to powerpoint border style.
    * @param line the specified viewsheet border object.
    * @param type the specified border style.
    */
   public static void applyLineStyle(XSLFAutoShape line, int type) {
      line.setLineWidth(getBorderWidth(type));

      if(type == StyleConstants.DOUBLE_LINE) {
         setDoubleLineStyle(line);
      }

      switch(type) {
      case StyleConstants.DOT_LINE:
         line.setLineDash(LineDash.DOT);
         break;
      case StyleConstants.DASH_LINE:
         line.setLineDash(LineDash.DASH_DOT);
         break;
      case StyleConstants.MEDIUM_DASH:
         line.setLineDash(LineDash.DASH);
         break;
      case StyleConstants.LARGE_DASH:
         line.setLineDash(LineDash.LG_DASH);
         break;
      }
   }

   private static void setDoubleLineStyle(XSLFAutoShape line) {
      CTShape shape = (CTShape) line.getXmlObject();
      CTShapeProperties sppr = shape.getSpPr();
      CTLineProperties ln = sppr.isSetLn() ? sppr.getLn() : sppr.addNewLn();
      ln.setCmpd(STCompoundLine.DBL);
   }

   /**
    * Get the border line width.
    * @param type the specified viewsheet border type.
    * @return the border width in point unit.
    */
   public static double getBorderWidth(int type) {
      for(int i = 0; i < borderWidthMap.length; i++) {
         if(type == borderWidthMap[i][0]) {
            return borderWidthMap[i][1] * PIXEL_TO_POINT;
         }
      }

      return 1 * PIXEL_TO_POINT;
   }

   /**
    * Get vertical align style with the specified viewsheet align style.
    * @param alignValue viewsheet align style.
    * @return the specified HSSFCellStyle align style.
    */
   public static VerticalAlignment getVerticalAlign(int alignValue) {
      int align = alignValue & 0x78;

      VerticalAlignment valignment = VerticalAlignment.TOP;

      if(align == StyleConstants.V_CENTER) {
         valignment = VerticalAlignment.MIDDLE;
      }
      else if(align == StyleConstants.V_BOTTOM) {
         valignment = VerticalAlignment.BOTTOM;
      }

      return valignment; // default to top same as viewsheet
   }

   /**
    * Get horizontal align style with the specified viewsheet align style.
    * @param alignValue viewsheet align style.
    * @return the specified HSSFCellStyle align style.
    */
   public static TextAlign getHorizontalAlign(int alignValue) {
      int align = alignValue & 0x7;

      TextAlign alignment = TextAlign.LEFT;

      if(align == StyleConstants.H_CENTER) {
         alignment = TextAlign.CENTER;
      }
      else if(align == StyleConstants.H_RIGHT) {
         alignment = TextAlign.RIGHT;
      }

      return alignment;
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to ppt.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param size the Dimension returned by getSpan().
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    */
   public static void writeTableCell(int startX, int startY, Dimension size,
                                     Rectangle2D pixelbounds,
                                     VSCompositeFormat format,
                                     String dispText, Object dispObj,
                                     PPTCoordinateHelper coordinationHelper,
                                     PPTValueHelper vHelper,
                                     PPTVSExporter exporter,
                                     java.util.List<VSAssemblyInfo> annos)
   {
      writeTableCell(startX, startY, size, pixelbounds, format, dispText, dispObj,
                     coordinationHelper, vHelper, exporter, annos, null);
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to ppt.
    * @param startX the display area's left-most X coordinate.
    * @param startY the display Area's top-most Y coordinate.
    * @param size the Dimension returned by getSpan().
    * @param format the VSCompositeFormat for the cell.
    * @param dispText the cell's text to be displayed.
    */
   public static void writeTableCell(int startX, int startY, Dimension size,
                                     Rectangle2D pixelbounds,
                                     VSCompositeFormat format,
                                     String dispText, Object dispObj,
                                     PPTCoordinateHelper coordinationHelper,
                                     PPTValueHelper vHelper,
                                     PPTVSExporter exporter,
                                     java.util.List<VSAssemblyInfo> annos,
                                     Insets padding)
   {
      if(pixelbounds == null) {
         pixelbounds = coordinationHelper.getBounds(new Point(startX, startY),
                                                    size);
      }

      List richText = null;

      if(dispText != null && VSTableLens.isHTML(dispText)) {
         dispObj = new PresenterPainter(dispText, new HTMLPresenter());
      }

      if(dispObj instanceof Painter) {
         if(dispObj instanceof PresenterPainter) {
            Presenter pr = ((PresenterPainter) dispObj).getPresenter();

            if(pr instanceof HTMLPresenter) {
               String str = ((PresenterPainter) dispObj).getObject() + "";
               richText = AnnotationVSUtil.getHTMLContent(str);
               dispObj = null;
            }
         }

         dispText = "";
      }

      vHelper.setBounds(pixelbounds);
      vHelper.setFormat(format);
      vHelper.setPadding(padding);


      Font txtFont = format.getFont() == null ?
         VSFontHelper.getDefaultFont() : format.getFont();
      float fontH = Common.getHeight(txtFont);

      if(fontH < (pixelbounds.getHeight() * 2)) {
         if(richText != null) {
            vHelper.writeRichTextContent(richText, 0);
         }
         else {
            vHelper.setValue(dispText);
            vHelper.writeTextBox(true);
         }
      }

      if(annos != null) {
         int x = (int) (pixelbounds.getX() / PIXEL_TO_POINT);
         int y = (int) (pixelbounds.getY() / PIXEL_TO_POINT);
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

      if(dispObj instanceof Painter || dispObj instanceof Image) {
         try {
            // avoid covering border
            pixelbounds = new Rectangle2D.Double(
               pixelbounds.getX() + 1, pixelbounds.getY() + 1,
               pixelbounds.getWidth() - 1, pixelbounds.getHeight() - 1);
            BufferedImage img = ExportUtil.getPainterImage(
               dispObj, (int) pixelbounds.getWidth(),
               (int) pixelbounds.getHeight(), format);
            exporter.writePicture(img, pixelbounds);
         }
         catch(Exception ex) {
            LOG.error("Failed to write painter image", ex);
         }
      }
   }

   /**
    * Set the borders type for region.
    * @param bounds the region.
    * @param slide the specified slide.
    */
   public static void paintDefaultBorders(Rectangle2D bounds, XSLFSlide slide) {
      VSCompositeFormat format = new VSCompositeFormat();
      int line = StyleConstants.THIN_LINE;
      format.getDefaultFormat().setBorders(new Insets(line, line, line, line));
      int cellType = ExcelVSUtil.CELL_HEADER;
      paintBorders(bounds, format, slide, cellType);
   }

   /**
    * Set the borders type for region.
    * @param bounds the region.
    * @param format the specified format.
    * @param slide the specified slide.
    * @param cellType the specified cellType.
    */
   public static void paintBorders(Rectangle2D bounds, VSCompositeFormat format,
                                   XSLFSlide slide, int cellType) {
      int x = (int) bounds.getX();
      int y = (int) bounds.getY();
      int width = (int) bounds.getWidth();
      int height = (int) bounds.getHeight();

      if(format == null) {
         return;
      }

      BorderColors colors = format.getBorderColors();
      Insets borders = format.getBorders();

      if(borders != null) {
         if(PPTVSUtil.getBorderWidth(borders.left) != 0) {
            XSLFAutoShape leftBorder = slide.createAutoShape();
            leftBorder.setShapeType(ShapeType.LINE);
            leftBorder.setAnchor(new Rectangle(x, y, 0, height));
            PPTVSUtil.applyLineStyle(leftBorder, borders.left);
            leftBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.left));

            if(colors != null) {
               leftBorder.setLineColor(colors.leftColor);
            }
         }

         if(PPTVSUtil.getBorderWidth(borders.right) != 0) {
            XSLFAutoShape rightBorder = slide.createAutoShape();
            rightBorder.setShapeType(ShapeType.LINE);
            rightBorder.setAnchor(new Rectangle(x + width, y, 0, height));
            PPTVSUtil.applyLineStyle(rightBorder, borders.right);
            rightBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.right));

            if(colors != null) {
               rightBorder.setLineColor(colors.rightColor);
            }
         }

         if(PPTVSUtil.getBorderWidth(borders.top) != 0 &&
            cellType != ExcelVSUtil.CELL_TAIL &&
            cellType != ExcelVSUtil.CELL_CONTENT)
         {
            XSLFAutoShape topBorder = slide.createAutoShape();
            topBorder.setShapeType(ShapeType.LINE);
            topBorder.setAnchor(new Rectangle(x, y, width, 0));
            PPTVSUtil.applyLineStyle(topBorder, borders.top);
            topBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.top));

            if(colors != null) {
               topBorder.setLineColor(colors.topColor);
            }
         }

         if(PPTVSUtil.getBorderWidth(borders.bottom) != 0 &&
            cellType != ExcelVSUtil.CELL_CONTENT)
         {
            XSLFAutoShape bottomBorder = slide.createAutoShape();
            bottomBorder.setShapeType(ShapeType.LINE);
            bottomBorder.setAnchor(new Rectangle(x, y + height, width, 0));
            PPTVSUtil.applyLineStyle(bottomBorder, borders.bottom);
            bottomBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.bottom));

            if(colors != null) {
               bottomBorder.setLineColor(colors.bottomColor);
            }
         }
      }
   }

   /**
    * Change the Color to other Color smoothly. To make the image on different
    * slide have the different uid for ppt2007 problem
    * @param c the original color.
    * @param index indicate the width of color need be change.
    */
   public static Color changeColor(Color c, int index) {
      int red = c.getRed() < 40 ? c.getRed() + index : c.getRed() - index;
      int green =
         c.getGreen() < 40 ? c.getGreen() + index : c.getGreen() - index;
      int blue = c.getBlue() < 40 ? c.getBlue() + index : c.getBlue() - index;

      return new Color(Math.max(red, 0), Math.max(green, 0), Math.max(blue, 0));
   }

   /**
    * Implementation of the abstarct method for writing crosstab cells to ppt.
    * @param format the VSCompositeFormat for the cell.
    * @param title the title.
    * @param value the value.
    * @param coordinationHelper the coordination helper.
    * @param vHelper the PPT value helper
    */
   public static void writeTitleInContainer(
      Rectangle2D bounds, VSCompositeFormat format, String title, String value,
      PPTCoordinateHelper coordinationHelper, PPTValueHelper vHelper, double titleRatio,
      Insets padding)
   {
      if(bounds == null) {
         return;
      }

      Font font = format.getFont();
      int fontSize = VSFontHelper.getFontSize(font);
      font = new Font(font.getName(), font.getStyle(),
         (int) Math.round(fontSize / PIXEL_TO_POINT));
      String text = ExportUtil.getTextInTitleCell(title, value,
         (int) bounds.getWidth(), font, titleRatio);
      PPTVSUtil.writeTableCell(-1, -1, null, bounds, format, text,
                               null, coordinationHelper, vHelper, null, null,
                               padding);
   }

   // and four type of border width: 0, 1, 2, 3 pixels
   private static int[][] borderWidthMap = new int[][] {
      {StyleConstants.NO_BORDER, 0},
      {StyleConstants.THIN_LINE, 1},
      {StyleConstants.MEDIUM_LINE, 2},
      {StyleConstants.DASH_LINE, 1},
      {StyleConstants.THICK_LINE, 3},
      {StyleConstants.DOUBLE_LINE, 3},
      {StyleConstants.DOT_LINE, 1},
      {StyleConstants.MEDIUM_DASH, 2},
      {StyleConstants.LARGE_DASH, 2}
   };

   private static final Logger LOG = LoggerFactory.getLogger(PPTVSUtil.class);
}
