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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.filter.Highlight;
import inetsoft.report.internal.info.TextBoxElementInfo;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.Format;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Paints a textbox element on a report.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextPainter implements ExpandablePainter, StyleConstants, Cloneable
{
   public TextPainter(ReportElement relem) {
      setElement(relem);
   }

   /**
    * Set the associated element.
    */
   public void setElement(ReportElement relem) {
      this.relem = relem;
      this.elem = (TextBoxElementInfo) ((BaseElement) relem).getElementInfo();

      checkSection(true);
   }

   /**
    * Check if this painter is expandable. A painter may implement the
    * ExpandablePainter interface but not expandable at rendering time.
    */
   @Override
   public boolean isExpandable() {
      return true;
   }

   /**
    * Check if the element is inside a section.
    */
   private void checkSection(boolean init) {
      if(init) {
         sectionDecimal = (short) -1;
      }

      try {
         if(relem != null) {
            BindingInfo info = (BindingInfo) relem.getUserObject();

            if(info != null) {
               sectionDecimal = (short) info.getDecimalPoints();
            }
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to get the number of decimal places", ex);
      }
   }

   /**
    * Reset highlight attributes.
    */
   void resetHLAttributes() {
      font = null;
      foreground = null;
   }

   /**
    * Set highlight attributes.
    */
   void setHLAttributes(Highlight hl) {
      this.font = hl == null ? null : hl.getFont();
      this.foreground = hl == null ? null : hl.getForeground();
   }

   /**
    * Get the font of this text box.
    */
   public Font getFont() {
      return font != null ? font : elem.getFont();
   }

   /**
    * Set the font of this text box.
    */
   public void setFont(Font font) {
      elem.setFont(font);
   }

   /**
    * Get the foreground color of this text box.
    */
   public Color getForeground() {
      return foreground != null ? foreground : elem.getForeground();
   }

   /**
    * Set the foreground color of this text box.
    */
   public void setForeground(Color color) {
      elem.setForeground(color);
   }

   /**
    * Return the text in the text lens.
    */
   public String getText() {
      if(text == null) {
         return "";
      }
      else if(text instanceof HeaderTextLens) {
         ReportSheet report = relem == null ? null :
            ((BaseElement) relem).getReport();
         Locale locale = (report == null) ? null : report.getLocale();
         return ((HeaderTextLens) text).getDisplayText(locale);
      }
      else {
         String value = text.getText();
         ReportSheet report = relem == null ? null : ((BaseElement) relem).getReport();
         ParameterTool ptool = new ParameterTool();

          if (report != null && ptool.containsParameter(value)) {
              value = ptool.parseParameters(report.getVariableTable(), value);
          }

          return value;
      }
   }

   /**
    * Get the text for final displaying.
    */
   public String getDisplayText() {
      if(relem == null) {
         return getText();
      }

      ReportSheet report = ((BaseElement) relem).getReport();

      if(text instanceof HeaderTextLens) {
         Locale locale = (report == null) ? null : report.getLocale();
         return ((HeaderTextLens) text).getDisplayText(locale);
      }

      String str = getText();
      String fmt = relem.getProperty("__format__");
      boolean bound = false;

      if(relem instanceof TextBoxElementDef) {
         bound = ((TextBoxElementDef) relem).getData() != null;
      }

      if(!bound && XConstants.MESSAGE_FORMAT.equals(fmt)) {
         String spec = relem.getProperty("__format_spec__");
         Locale locale = report == null ? null : report.getLocale();
         Format format = TableFormat.getFormat(fmt, spec, locale);

         if(format != null && spec != null && spec.length() > 0) {
            return format.format(str);
         }
      }

      return (str == null) ? "" : str;
   }

   /**
    * Set the text contained in this text element.
    */
   public void setText(String text) {
      setText(new DefaultTextLens(text));
   }

   /**
    * Set the text source of this text element.
    */
   public void setText(TextLens lens) {
      this.text = lens;
      elem.setText(getText());
   }

   /**
    * Get the text lens of this text box.
    */
   public TextLens getTextLens() {
      return text;
   }

   /**
    * Get the actual text displayed in the textbox. Handles wrapping and
    * line breaks. This is called from HTMLWriter to generate HTML.
    * @param offset the distance to the top of the painter where to start
    * painting. This is set if a painter spans across pages.
    */
   public String[] processText(int w, int h, int offset) {
      return processText(w, h, -1, offset);
   }

   /**
    * Get the actual text displayed in the textbox. Handles wrapping and
    * line breaks. This is called from HTMLWriter to generate HTML.
    * @param offset the distance to the top of the painter where to start
    * painting. This is set if a painter spans across pages.
    */
   public String[] processText(int w, int h, int ow, int offset) {
      // the fontratio default value is 0.9 in HTMLWriter
      return processText(w, h, ow, offset, getFont().getSize() * 0.9f);
   }

   /**
    * Get the actual text displayed in the textbox. Handles wrapping and
    * line breaks. This is called from HTMLWriter to generate HTML.
    * @param offset the distance to the top of the painter where to start
    * painting. This is set if a painter spans across pages.
    */
   public String[] processText(int w, int h, int ow, int offset, float fontSize)
   {
      Margin adj = calcBorderMargin();
      Insets pad = calcPadding();
      String txt = getDisplayText();

      txt = (txt == null) ? "" : txt.replace('\t', ' ');

      // @by hummingm, fix bug1164623467453.
      txt += "\n";

      // get the max right side sub string
      boolean wrap = true;

      Font newFont = getFont().deriveFont(fontSize);
      float fontH = Common.getHeight(newFont);

      // @by larryl, the checkSection in setElement() may not catch the
      // bindingInfo since the userobject may have not been set yet
      checkSection(false);

      if(sectionDecimal >= 0) {
         wrap = h > fontH / 0.9f;
      }

      // h is now the original height
      h += offset;

      int startline = 0; // starting line index, in the offset part if wrapped
      float w2 = (ow < 0) ?
         (float) (w - adj.left - adj.right - pad.left - pad.right) :
         (float) (ow - adj.left - adj.right - pad.left - pad.right);
      float h2 = (float) (h - adj.top - adj.bottom - pad.top - pad.bottom);
      ArrayList<String> lines = new ArrayList<>();

      float adv = fontH + elem.getSpacing();

      w2 = (w2 < 0) ? 0 : w2;

      while(h2 >= fontH && txt.length() > 0) {
         int idx = breakLine(txt, w2, newFont, wrap);

         if(idx < 0) {
            lines.add(txt);
            break;
         }

         lines.add(txt.substring(0, idx));
         txt = txt.substring(idx);

         // skip the newline that caused the wrapping
         if(txt.startsWith("\n")) {
            txt = txt.substring(1);
         }

         if(offset > fontH) {
            startline++;
         }

         h2 -= adv;
         offset -= adv;
      }

      String[] arr = new String[lines.size() - startline];

      for(int i = startline; i < lines.size(); i++) {
         arr[i - startline] = lines.get(i);
      }

      return arr;
   }

   /**
    * Break lines at newline or when width exceeds available width.
    */
   private int breakLine(String txt, float w2, Font font, boolean wrap) {
      int idx = -1;
      int max = Math.min(txt.length(), 150); // optimization

      for(int i = 0; i < max; i++) {
         if(txt.charAt(i) == '\n') {
            txt = txt.substring(0, i);
            idx = i;
            break;
         }
      }

      int bi = Util.breakLine(txt, w2, font, wrap);

      return (bi > 0) ? bi : idx;
   }

   /**
    * Paint the textbox.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      Margin adj = calcBorderMargin();
      Insets pad = calcPadding();
      int shadW = 0;
      String txt = getDisplayText();
      txt = (txt == null) ? "" : txt.replace('\t', ' ');

      if(g instanceof PDFDevice) {
         ((PDFDevice) g).startArtifact();
      }

      if(elem.isShadow()) {
         // in case background is changed, clear the shadow area
         g.setColor(Color.white);
         Common.fillRect(g, x, y + h - shadowW, shadowW, shadowW);
         Common.fillRect(g, x + w - shadowW, y, shadowW, shadowW);
         g.setColor(Color.gray);
         Common.fillRect(g, x + shadowW, y + h - shadowW, w - shadowW, shadowW);
         Common.fillRect(g, x + w - shadowW, y + shadowW, shadowW, h - shadowW);
         shadW = shadowW;
      }

      g.setColor((elem.getBorderColor() == null) ?  getForeground() :
                 elem.getBorderColor());
      g.setFont(getFont());
      int style = (elem.getBorder() == -1) ? 0 : elem.getBorder();

      if(style != StyleConstants.NO_BORDER &&
         elem.getShape() == StyleConstants.BOX_ROUNDED_RECTANGLE)
      {
         Dimension corner = elem.getCornerSize();
         Dimension arc = (corner != null) ? corner : new Dimension(5, 5);

         g.drawRoundRect(x, y, w - shadW - 1, h - shadW - 1, arc.width,
               arc.height);
      }
      else if(elem.getBorders() != null &&
              (elem.getBorders().top != 0 ||
               elem.getBorders().left != 0 ||
               elem.getBorders().bottom != 0 ||
               elem.getBorders().right != 0))
      {
         Insets borders = elem.getBorders();
         int top = (borders.top != -1) ? borders.top : 0;
         int left = (borders.left != -1) ? borders.left : 0;
         int bottom = (borders.bottom != -1) ? borders.bottom : 0;
         int right = (borders.right != -1) ? borders.right : 0;

         Common.drawRect(g, x, y, w - shadW, h - shadW, top, left, bottom,
                         right);
      }
      else if(style != NO_BORDER) {
         Common.drawRect(g, x, y, w - shadW, h - shadW, style);
      }

      if(g instanceof PDFDevice) {
         ((PDFDevice) g).endArtifact();
      }

      g.setColor(getForeground());

      // get the max right side sub string
      int dot = 0;
      boolean wrap = true;

      if(sectionDecimal >= 0) {
         dot = sectionDecimal;
         wrap = h > Common.getHeight(getFont()) * 1.3;
      }

      Bounds bounds = new Bounds(
         (float) (x + adj.left + pad.left),
         (float) (y + adj.top + pad.top),
         (float) (w - adj.left - adj.right - pad.left - pad.right),
         (float) (h - adj.top - adj.bottom - pad.top - pad.bottom));
      boolean clipline = (elem.getTextAlignment() & StyleConstants.V_TOP) != 0;

      if(g instanceof PDFDevice) {
         ((PDFDevice) g).startParagraph(null);
      }

      Common.paintText(g, txt, bounds, elem.getTextAlignment(), wrap,
                       elem.isJustify() &&
                       (elem.getTextAlignment() & StyleConstants.H_LEFT) != 0,
                       elem.getSpacing(), dot, clipline);

      if(g instanceof PDFDevice) {
         ((PDFDevice) g).endParagraph();
      }
   }

   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    * @param bufy if the painter is drawn across pages, bufy is the height
    * already consumed in previous pages.
    * @param bufh is the height available on the current page.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h,
                     float bufy, float bufh) {
      paint(g, x, y, w, h);
   }

   /**
    * Calculate the border margin.
    */
   Margin calcBorderMargin() {
      Margin adj = elem.isShadow() ? new Margin(0, 0, shadowW, shadowW) :
         new Margin();
      Insets borders = elem.getBorders();

      if(borders != null &&
         (borders.top != 0 || borders.left != 0 ||
            borders.bottom != 0 || borders.right != 0)) {
         int top = (borders.top != -1) ? borders.top : 0;
         int left = (borders.left != -1) ? borders.left : 0;
         int bottom = (borders.bottom != -1) ? borders.bottom : 0;
         int right = (borders.right != -1) ? borders.right : 0;
         float topw = (float) Math.ceil(Common.getLineWidth(top));
         float leftw = (float) Math.ceil(Common.getLineWidth(left));
         float bottomw = (float) Math.ceil(Common.getLineWidth(bottom));
         float rightw = (float) Math.ceil(Common.getLineWidth(right));

         adj.top += topw;
         adj.left += leftw;
         adj.bottom += bottomw;
         adj.right += rightw;
      }
      else {
         int style = (elem.getBorder() == -1) ? 0 : elem.getBorder();

         adj.top += (float) Math.ceil(Common.getLineWidth(style));
         adj.left += adj.top;
         adj.bottom += adj.top;
         adj.right += adj.top;
      }

      return adj;
   }

   /**
    * Get the size that would fit the text.
    */
   @Override
   public Dimension getPreferredSize() {
      Margin adj = calcBorderMargin();
      String txt = getDisplayText();

      if(elem.isShadow()) {
         adj.right += shadowW;
         adj.bottom += shadowW;
      }

      // set a minimum size so it does not 'disappear'
      Size d;

      // @by larryl, the checkSection in setElement() may not catch the
      // bindingInfo since the userobject may have not been set yet
      checkSection(false);

      Insets pad = calcPadding();

      if(txt == null || txt.length() == 0) {
         d = new Size(20, 15);
      }
      // @by larryl, if inside section, calcaulate the size by wrapping
      // the lines so the auto-size would work and correct expand the height
      else if(sectionDecimal >= 0) {
         int width =
            (int) (this.width - adj.left - adj.right - pad.left - pad.right);
         Rectangle box = ((BaseElement) relem).getReport().printBox;
         Font fn = getFont();

         String[] lines = processText(box.width, Integer.MAX_VALUE, -1, 0,
                                      fn.getSize());
         FontMetrics fm = Common.getFractionalFontMetrics(fn);
         float fontH = Common.getHeight(getFont());
         d = new Size(0, 0);

         for(String line : lines) {
            d.width = Math.max(d.width, Common.stringWidth(line, fn, fm));
            d.width = Math.max(d.width, width);
            d.height += fontH + elem.getSpacing();
         }
      }
      else {
         d = StyleCore.getTextSize(txt, getFont(), elem.getSpacing());
      }

      return new Dimension(
         (int) (d.width + adj.left + adj.right + pad.left + pad.right),
         (int) (d.height + adj.top + adj.bottom + pad.top + pad.bottom));
   }

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   @Override
   public float getHeightAdjustment(ReportElement elem, Dimension pd, float offsetY, float painterW, float painterH) {
      int fontH = (int) Common.getHeight(getFont());

      if(offsetY + painterH < pd.height && painterH > fontH) {
         float height = painterH;
         Margin adj = calcBorderMargin();
         Insets pad = calcPadding();

         // if at top, subtract the top adjustment
         if(offsetY == 0) {
            height -= adj.top + pad.top;
         }
         else if(painterH + offsetY >= pd.height) {
            height -= adj.bottom + pad.bottom;
         }

         int perLine = fontH + this.getSpacing();

         // @by larryl, if the total height is less than the per line height,
         // don't subtract anything
         if(height > perLine) {
            return height % perLine;
         }
      }

      return 0;
   }

   /**
    * Set the width in section.
    */
   public void setWidth(int width) {
      this.width = (short) width;
   }

   /**
    * Get the width in section.
    */
   public int getWidth() {
      return this.width;
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param width the maximum width of the painter.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(float width) {
      Margin adj = calcBorderMargin();
      Insets pad = calcPadding();

      // @by larryl, calculate the height of wrapped text. Before we
      // use the ratio of the old width and the new width, which is not
      // correct since the wrapping is not linear
      String[] lines = processText((int) width, Integer.MAX_VALUE, -1, 0,
                                   getFont().getSize());
      float fnh = Common.getHeight(getFont());

      return new Dimension((int) width,
                           (int) (lines.length * (fnh + elem.getSpacing()) +
                                  adj.top + adj.bottom + pad.top + pad.bottom));
   }

   /**
    * Check if is scalable.
    */
   @Override
   public boolean isScalable() {
      return true;
   }

   // calculate the padding based on the shape.
   Insets calcPadding() {
      Insets pad = elem.getPadding();

      if(pad == null) {
         pad = new Insets(0, 0, 0, 0);
      }
      else {
         pad = (Insets) pad.clone();
      }

      int adj = 0;

      if(elem.getShape() == StyleConstants.BOX_ROUNDED_RECTANGLE) {
         adj = 5;
      }

      pad.left += adj;
      pad.right += adj;

      return pad;
   }

   /**
    * Get the shadow width.
    */
   public int getShadowWidth() {
      return elem.isShadow() ? shadowW : 0;
   }

   /**
    * Clone a TextPainter with null content.
    */
   @Override
   public Object clone() {
      return clone(null, false);
   }

   /*
    * Clone a TextPainter with the same attributes as the current one,
    * and with the new text as the content.
    */
   public TextPainter clone(TextLens text, boolean share) {
      try {
         TextPainter t = (TextPainter) super.clone();

         if(text != null) {
            t.text = text;
         }

         // @by larryl, optimization, shared identical elem info. The assumption
         // here is a cloned TextPainter is not changed anymore. Therefore,
         // if the current element info is the same as a cloned info, we can
         // reuse the cloned element info in the new clone
         if(share && lastInfo != null && !elem.isDirty()) {
            t.elem = lastInfo;
         }
         else {
            t.elem = (TextBoxElementInfo) elem.clone();
            lastInfo = t.elem;
            t.elem.setDirty(false);
         }

         return t;
      }
      catch(Exception e) {
         LOG.error("Failed to clone object", e);
         return null;
      }
   }

   // proxy methods

   /**
    * set the line spacing
    */
   public void setSpacing(int spac) {
      elem.setSpacing(spac);
   }

   /**
    * get the line spacing
    */
   public int getSpacing() {
      return elem.getSpacing();
   }

   /**
    * Set the border around this text box.
    * @param border line style in StyleConstants, e.g. THIN_LINE.
    */
   public void setBorder(int border) {
      elem.setBorder(border);
   }

   /**
    * Get the border around this text box.
    * @return border line style.
    */
   public int getBorder() {
      return elem.getBorder();
   }

   /**
    * Set the border color around this text box.
    */
   public void setBorderColor(Color color) {
      elem.setBorderColor(color);
   }

   /**
    * Get the border color around this text box.
    */
   public Color getBorderColor() {
      return elem.getBorderColor();
   }

   /**
    * Set the individual border line styles. This overrides the default border
    * setting.
    * @param borders line styles defined in StyleConstants, e.g. THIN_LINE.
    */
   public void setBorders(Insets borders) {
      elem.setBorders(borders);
   }

   /**
    * Get the individual border line styles.
    * @return border line style..
    */
   public Insets getBorders() {
      return elem.getBorders();
   }

   /**
    * Set the textbox shape. One of StyleConstants.BOX_RECTANGLE or
    * StyleConstants.BOX_ROUNDED_RECTANGLE.
    * @param shape textbox shape option.
    */
   public void setShape(int shape) {
      elem.setShape(shape);
   }

   /**
    * Get the textbox shape.
    * @return the textbox shape.
    */
   public int getShape() {
      return elem.getShape();
   }

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   public boolean isJustify() {
      return elem.isJustify();
   }

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   public void setJustify(boolean justify) {
      elem.setJustify(justify);
   }

   /**
    * Get the text alignment.
    */
   public int getTextAlignment() {
      return elem.getTextAlignment();
   }

   /**
    * Set the text alignment within the text box.
    */
   public void setTextAlignment(int align) {
      elem.setTextAlignment(align);
   }

   /**
    * Set the shadow option of this text box.
    */
   public void setShadow(boolean shadow) {
      elem.setShadow(shadow);
   }

   /**
    * Check the shadow option of this text box.
    */
   public boolean isShadow() {
      return elem.isShadow();
   }

   /**
    * Set the corner width and height for rounded rectangle shape.
    */
   public void setCornerSize(Dimension corner) {
      elem.setCornerSize(corner);
   }

   /**
    * Get the corner width and height of rounded rectangle.
    */
   public Dimension getCornerSize() {
      return elem.getCornerSize();
   }

   /**
    * Get text box padding space.
    */
   public Insets getPadding() {
      return elem.getPadding();
   }

   /**
    * Set text box padding space.
    */
   public void setPadding(Insets padding) {
      elem.setPadding(padding);
   }

   // serialization methods

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();

      // @by billh, font, foreground and background might be not the same as
      // the ones contained in element info, for highlight might change them
      elem.setForeground((Color) s.readObject());
      elem.setFont((Font) s.readObject());
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      // @by amitm, 2004-11-02
      checkSection(false);

      stream.defaultWriteObject();

      // @by billh, font, foreground and background might be not the same as
      // the ones contained in element info, for highlight might change them
      stream.writeObject(getForeground());
      stream.writeObject(getFont());
   }

   private static byte shadowW = 4; // shadow width

   static {
      String prop = SreeEnv.getProperty("textbox.shadow.width");

      if(prop != null) {
         shadowW = (byte) Integer.parseInt(prop);
      }
   }

   private TextLens text; // content
   private short sectionDecimal = -1; // points in section, -1 if not in section
   private TextBoxElementInfo elem; // element info

   private transient ReportElement relem = null; // report element
   private transient Color foreground = null; // highlight foreground
   private transient Font font = null; // highlight font
   private transient TextBoxElementInfo lastInfo = null; // optimization
   private transient short width = -1; // width in section

   private static final Logger LOG =
      LoggerFactory.getLogger(TextPainter.class);
}
