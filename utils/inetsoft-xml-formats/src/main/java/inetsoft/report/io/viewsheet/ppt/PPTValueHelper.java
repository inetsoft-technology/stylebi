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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.internal.*;
import inetsoft.report.io.rtf.RichText;
import inetsoft.report.io.rtf.RichTextFont;
import inetsoft.report.io.viewsheet.PoiExportUtil;
import inetsoft.report.io.viewsheet.VSFontHelper;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.report.io.viewsheet.excel.PoiExcelVSUtil;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

/**
 * ValueHelper wraps the value assembly for powerpoint.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class PPTValueHelper {
   /**
    * Constructor.
    */
   public PPTValueHelper(XSLFSlide slide) {
      this.slide = slide;
   }

   /**
    * Set the value.
    * @param value the specified value.
    */
   public void setValue(String value) {
      this.value = value;
   }

   /**
    * Get the value.
    * @return the specified value.
    */
   public String getValue() {
      return this.value;
   }

   /**
    * Set the format for the value helper.
    */
   public void setFormat(VSCompositeFormat format) {
      this.format = format;
   }

   /**
    * Get the format of the value helper.
    */
   public VSCompositeFormat getFormat() {
      return this.format;
   }

   /**
    * Set the bounds of the value helper.
    */
   public void setBounds(Rectangle2D bounds) {
      this.bounds = bounds;
   }

   /**
    * Get the bounds of the value helper.
    */
   public Rectangle2D getBounds() {
      return this.bounds;
   }

   /**
    * Set the cell type.
    */
   public void setCellType(int cellType) {
      this.cellType = cellType;
   }

   /**
    * Get the cell type.
    */
   public int getCellType() {
      return this.cellType;
   }

   /**
    * Whether the text have a shadow.
    */
   public boolean isShadowed() {
      return this.shadowed;
   }

   /**
    * Set whether the text has a shadow.
    */
   public void setShadowed(boolean shadowed) {
      this.shadowed = shadowed;
   }

   public Insets getPadding() {
      return padding;
   }

   public void setPadding(Insets padding) {
      this.padding = padding;
   }

   /**
    * Paint the value assembly. It contains two components:
    * one is rectangle for background and borders,
    * the other is textbox for value.
    */
   public void writeTextBox() {
      writeTextBox(false);
   }

   /**
    * Paint the value assembly. It contains two components:
    * one is rectangle for background and borders,
    * the other is textbox for value.
    */
   public void writeTextBox(boolean isTableCell) {
      writeContent(isTableCell);
      paintBorders();
   }

   /**
    * Paint the textbox contains the value.
    */
   private void writeContent(boolean isTableCell) {
      XSLFTextBox textbox = slide.createTextBox();
      textbox.clearText();
      XSLFTextParagraph paragraph = textbox.addNewTextParagraph();
      XSLFTextRun rtr = paragraph.addNewTextRun();

      if(bounds != null) {
         Rectangle anchorRect = new Rectangle((int) bounds.getX(),
                                              (int) bounds.getY(),
                                              (int) bounds.getWidth(),
                                              (int) Math.ceil(bounds.getHeight()));
         textbox.setAnchor(anchorRect);
      }

      if(format == null) {
         format = new VSCompositeFormat();
         Font tf = VSFontHelper.getDefaultFont();
         format.getDefaultFormat().setFont(tf);
         format.getDefaultFormat().setWrapping(false);
      }

      StringBuilder buf = new StringBuilder();
      Font txtFont = format.getFont() == null ?
         VSFontHelper.getDefaultFont() : format.getFont();

      // when write text out to ppt, the font size has been changed, so
      // all the caculation of the font should use the adjust font size,
      // here just change the font size, and when write text out just use
      // current adjustted font size, see bug1241777058333
      if(txtFont instanceof StyleFont) {
         txtFont = new StyleFont(txtFont.getName(), txtFont.getStyle(),
            VSFontHelper.getFontSize(txtFont),
               ((StyleFont) txtFont).getUnderlineStyle(),
               ((StyleFont) txtFont).getStrikelineStyle());
      }
      else {
         txtFont =
            txtFont.deriveFont((float) VSFontHelper.getFontSize(txtFont));
      }

      FontMetrics fm = Common.getFractionalFontMetrics(txtFont);
      float fontH = Common.getHeight(txtFont);
      Bounds inBounds = new Bounds((float) bounds.getX(), (float) bounds.getY(),
                                   (float) bounds.getWidth(),
                                   (float) bounds.getHeight());

      if(!format.isWrapping()) {
         inBounds.width -= 2; // must consider margin for ppt!
      }
      else {
         // bug1166609586046, although the text can showed whole on textbox
         // without wrap on ppt, the ppt still auto warp it.
         // Add WRAP_GAP to avoid it.
         // bug1296026784843, cannot add WRAP_GAP
         inBounds.width -= 2; //(2 + WRAP_GAP);

         if(isTableCell) {
            inBounds.width -= WRAP_GAP;
         }
      }

      Bounds outBounds = new Bounds();
      String val = Tool.convertHTMLSymbol(value);
      Vector lines = Common.processText(val, inBounds,
         format.getAlignment(), format.isWrapping(), txtFont,
         outBounds, new Vector(), 0, fm);
      float y = 0;

      for(int i = 0; i < lines.size(); i++) {
         int[] offset = (int[]) lines.elementAt(i);
         String txt = val.substring(offset[0], offset[1]);

         if(i > 0) {
            buf.append("\r");
         }

         // if wrap is set to false, the lines will not be
         // chop off at the right bound, so we need to it explicitly
         if(!format.isWrapping()) {
            int idx = Util.breakLine(txt, bounds.getWidth() - 1, txtFont, false);
            txt = (idx < 0) ? txt : txt.substring(0, idx);
         }

         y += fontH;
         buf.append(txt);

         // if the next line is outside of printable area,
         // ignore the line, otherwise it overlaps with line below
         if(y + fontH >= bounds.getHeight()) {
            break;
         }
      }

      if(isShadowed()) {
         PoiExportUtil.drowTextShadow(rtr.getXmlObject());
      }

      //rtr.setShadowed(isShadowed());
      String str = buf.toString();

      if(!"".equals(str) && str.endsWith("\r")) {
         int idx = str.lastIndexOf("\r");
         str = str.substring(0, idx - 1);
      }

      rtr.setText(str);
      textbox.setWordWrap(format.isWrapping());

      if(format.getBackground() != null) {
         textbox.setFillColor(PoiExcelVSUtil.getColorByAlpha(
            format.getBackground()));
      }

      if(format.getForeground() != null) {
         rtr.setFontColor(format.getForeground());
      }

      // if the Alignmentis 0, set the default Alignment.
      if(format.getAlignment() == 0) {
         format.getDefaultFormat().setAlignment(
            StyleConstants.H_LEFT | StyleConstants.V_TOP);
      }

      textbox.setVerticalAlignment(
         PPTVSUtil.getVerticalAlign(format.getAlignment()));
      paragraph.setTextAlign(
         PPTVSUtil.getHorizontalAlign(format.getAlignment()));

      if(txtFont != null) {
         if(txtFont.getFontName() != null) {
            rtr.setFontFamily(txtFont.getFontName());
         }

         rtr.setFontSize((double) txtFont.getSize());

         if(txtFont.isItalic()) {
            // poi bug when setting to false!
            rtr.setItalic(true);
         }

         if(txtFont.isBold()) {
            // poi bug when setting to false!
            rtr.setBold(true);
         }

         int extstyle = txtFont.getStyle();

         // set strike through
         boolean bStrikeThrough = (extstyle & StyleFont.STRIKETHROUGH) != 0;
         rtr.setStrikethrough(bStrikeThrough);

         if((extstyle & StyleFont.UNDERLINE) != 0) {
            rtr.setUnderlined(true);
         }
      }

      if(padding != null) {
         textbox.setLeftInset(padding.left);
         textbox.setRightInset(padding.right);
         textbox.setTopInset(padding.top);
         textbox.setBottomInset(padding.bottom);
      }
      else {
         textbox.setLeftInset(1);
         textbox.setRightInset(1);
         textbox.setTopInset(1);
         textbox.setBottomInset(1);
      }
   }

   /**
    * Write text contents applying the individual format of each seperate piece of
    * rich text
    * @param richTexts
    */
   public void writeRichTextContent(java.util.List richTexts, int textAlignment) {
      paintBorders();
      XSLFTextBox textbox = slide.createTextBox();
      textbox.clearText();
      XSLFTextParagraph paragraph = textbox.addNewTextParagraph();

      if(bounds != null) {
         textbox.setAnchor(new Rectangle((int) bounds.getX(),
                                         (int) bounds.getY(),
                                         (int) bounds.getWidth(),
                                         (int) Math.ceil(bounds.getHeight())));
      }

      if(format == null) {
         format = new VSCompositeFormat();
         Font tf = VSFontHelper.getDefaultFont();
         format.getDefaultFormat().setFont(tf);
         format.getDefaultFormat().setWrapping(false);
      }

      for(int i = 0; i < richTexts.size(); i++) {
         Object obj = richTexts.get(i);

         if(obj instanceof RichText) {
            RichText rt = (RichText) obj;
            XSLFTextRun rtr = paragraph.addNewTextRun();
            rtr.setBold(rt.getFont().isBold());
            rtr.setStrikethrough(((RichTextFont) rt.getFont()).isStrikethrough());
            rtr.setUnderlined(((RichTextFont) rt.getFont()).isUnderline());
            rtr.setFontColor(rt.getFgColor());
            rtr.setFontFamily(rt.getFont().getFamily());
            rtr.setFontSize((double) rt.getFont().getSize());
            rtr.setText(rt.getContent().replaceAll("%3Cbr%3E", ""));
            rtr.setCharacterSpacing(0);

            //if(rt.getContent().contains("%3Cbr%3E")) {
            paragraph.addLineBreak();
         }
      }

      if(format.getBackground() != null) {
         textbox.setFillColor(PoiExcelVSUtil.getColorByAlpha(
            format.getBackground()));
      }

      textbox.setWordWrap(format.isWrapping());


      // if the alignment is 0, set the default alignment.
      if(format.getAlignment() == 0) {
         format.getDefaultFormat().setAlignment(
            StyleConstants.H_LEFT | StyleConstants.V_TOP);
      }

      textbox.setVerticalAlignment(
         PPTVSUtil.getVerticalAlign(format.getAlignment()));
      paragraph.setTextAlign(
         PPTVSUtil.getHorizontalAlign(textAlignment));

      textbox.setLeftInset(1);
      textbox.setRightInset(1);
      textbox.setTopInset(1);
      textbox.setBottomInset(1);
   }

   /**
    * Paint the border for the rectangle.
    */
   private void paintBorders() {
      if(format == null) {
         return;
      }

      int x = (int) bounds.getX();
      int y = (int) bounds.getY();
      int width = (int) bounds.getWidth();
      int height = (int) bounds.getHeight();
      BorderColors colors = format.getBorderColors();
      Insets borders = format.getBorders();
      BorderColors defbcolors = new BorderColors(
         VSAssemblyInfo.DEFAULT_BORDER_COLOR,
         VSAssemblyInfo.DEFAULT_BORDER_COLOR,
         VSAssemblyInfo.DEFAULT_BORDER_COLOR,
         VSAssemblyInfo.DEFAULT_BORDER_COLOR);

      if(borders != null) {
         if(PPTVSUtil.getBorderWidth(borders.left) != 0) {
            XSLFAutoShape leftBorder = slide.createAutoShape();
            leftBorder.setShapeType(ShapeType.LINE);
            leftBorder.setAnchor(new Rectangle(x, y, 0, height));
            PPTVSUtil.applyLineStyle(leftBorder, borders.left);
            leftBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.left));

            if(colors != null) {
               leftBorder.setLineColor(colors.leftColor == null ?
                  defbcolors.leftColor : colors.leftColor);
            }
         }

         if(PPTVSUtil.getBorderWidth(borders.right) != 0) {
            XSLFAutoShape rightBorder = slide.createAutoShape();
            rightBorder.setShapeType(ShapeType.LINE);
            rightBorder.setAnchor(new Rectangle(
               (int) (bounds.getX() + bounds.getWidth()), y, 0, height));
            PPTVSUtil.applyLineStyle(rightBorder, borders.right);
            rightBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.right));

            if(colors != null) {
               rightBorder.setLineColor(colors.rightColor == null ?
                  defbcolors.rightColor : colors.rightColor);
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
               topBorder.setLineColor(colors.topColor == null ?
                  defbcolors.topColor : colors.topColor);
            }
         }

         if(PPTVSUtil.getBorderWidth(borders.bottom) != 0 &&
            cellType != ExcelVSUtil.CELL_CONTENT &&
            cellType != ExcelVSUtil.CELL_TAIL)
         {
            XSLFAutoShape bottomBorder = slide.createAutoShape();
            bottomBorder.setShapeType(ShapeType.LINE);
            bottomBorder.setAnchor(new Rectangle(
               x, (int) (bounds.getY() + bounds.getHeight()), width, 0));
            PPTVSUtil.applyLineStyle(bottomBorder, borders.bottom);
            bottomBorder.setLineWidth(PPTVSUtil.getBorderWidth(borders.bottom));

            if(colors != null) {
               bottomBorder.setLineColor(colors.bottomColor == null ?
                  defbcolors.bottomColor : colors.bottomColor);
            }
         }
      }
   }

   private Rectangle2D bounds = null;
   private VSCompositeFormat format = null;
   private XSLFSlide slide = null;
   private String value = null;
   private int cellType = 0;
   private int WRAP_GAP = 4;
   private boolean shadowed;
   private Insets padding;
}
