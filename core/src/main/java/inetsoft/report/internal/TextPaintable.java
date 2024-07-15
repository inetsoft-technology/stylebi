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
import inetsoft.report.internal.table.TableFormat;
import inetsoft.util.Tool;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.text.Format;
import java.util.*;

/**
 * The TextPaintable encapsulate printing of a text segment.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextPaintable extends BasePaintable {
   /**
    * Create a default text paintable
    */
   public TextPaintable() {
      super();
      text = "";
      attr.font = Util.DEFAULT_FONT;
      attr.fg = Color.black;
      attr.bg = null;
   }

   /**
    * Paint a text string at the specified location.
    * @param str string to print.
    * @param x x coordinate.
    * @param y y coordinate.
    * @param width fixed width in a section.
    * @param height fixed height in a section.
    */
   public TextPaintable(String str, float x, float y, float width, float height,
                        ReportElement elem, boolean inSection) {
      super(elem);
      this.text = str;
      this.x = (short) x;
      this.y = y;
      this.width = (short) width;
      this.height = height;
      setMultiLine(true);
      init(inSection);
   }

   /**
    * Paint a text string at the specified location.
    * @param str string to print.
    * @param x x coordinate.
    * @param y y coordinate.
    */
   public TextPaintable(String str, float x, float y, ReportElement elem,
                        boolean inSection)
   {
      super(elem);
      this.text = str;
      this.x = (short) x;
      this.y = y;
      init(inSection);
   }

   /**
    * Paint a text string at the specified location.
    * @param str string to print.
    * @param x x coordinate.
    * @param y y coordinate.
    * @param w width the string should fill
    */
   public TextPaintable(String str, float x, float y, float w,
                        ReportElement elem, boolean inSection) {
      this(str, x, y, elem, inSection);
      this.fillWidth = (short) w;
   }

   /**
    * Paint a text string at the specified location.
    * @param lens string to print.
    * @param x x coordinate.
    * @param y y coordinate.
    */
   public TextPaintable(TextLens lens, float x, float y, ReportElement elem,
                        boolean inSection) {
      super(elem);
      this.text = lens;
      this.x = (short) x;
      this.y = y;
      init(inSection);

      if(lens instanceof HeaderTextLens) {
         width = (short) getBounds().getWidth();
         ReportSheet report = ((BaseElement) elem).getReport();

         // @by larryl, make sure the width is less than the max page width
         if(report != null) {
            width = (short) Math.min(report.printBox.width, width);
         }
      }
   }

   /**
    * Initialize paintabel from element.
    */
   private void init(boolean inSection) {
      attr.font = elem.getFont();
      attr.fg = elem.getForeground();
      attr.bg = elem.getBackground();
      attr.align = elem.getAlignment();
      String fmt = elem.getProperty("__format__");

      if(fmt != null) {
         String spec = elem.getProperty("__format_spec__");
         ReportSheet report = ((BaseElement) elem).getReport();
         Locale locale = (report == null) ? null : report.getLocale();
         attr.format = TableFormat.getFormat(fmt, spec, locale);
      }

      // due to the optimization of the text inside section, a textpaintable
      // may have a height larger than the text height. If drawn as baseline,
      // the text appears to be vertically centered. Force it to be top oriented
      // as baseline does not mean much in text inside section anyway
      if(isMultiLine() && attr.align == StyleConstants.V_BASELINE) {
         attr.align = StyleConstants.V_TOP |
            (attr.align & StyleConstants.H_ALIGN_MASK);
      }

      if(elem instanceof TextElement) {
         attr.justify = ((TextElement) elem).isJustify();
      }

      if(elem != null) {
         Hyperlink link = ((HyperlinkSupport) elem).getHyperlink();

         if(link != null) {
            setHyperlink(new Hyperlink.Ref(link));
         }

         setDrillHyperlinks(((TextBased) elem).getDrillHyperlinks());
      }
   }

   /**
    * Get a shared object.
    */
   @Override
   public void complete() {
      attr = (Attr) ObjectCache.get(attr);
      linkAttr = (LinkAttr) ObjectCache.get(linkAttr);
   }

   /**
    * Get the format if the text is formatted from another object.
    */
   public Format getFormat() {
      return attr.format;
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   @Override
   public final Rectangle getBounds() {
      // height is cached
      if(height <= 0) {
         height = Common.getHeight(attr.font);
      }

      // if displaying page numbers, still cache the width
      if(width == 0) {
         String str = getText();
         width = (short) Common.round(Common.stringWidth(str, attr.font));
      }

      return new Rectangle(x, (int) y,
         fillWidth > 0 ? fillWidth : width, (int) height);
   }

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   @Override
   public void setLocation(Point loc) {
      x = (short) loc.x;
      y = loc.y;
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return new Point(x, (int) y);
   }

   /**
    * Set the width of this paintable.
    */
   public void setWidth(int w) {
      width = (short) w;
   }

   /**
    * Set the height of this paintable.
    */
   public void setHeight(float h) {
      height = h;
   }

   /**
    * Set whether this is a fixed section element.
    */
   public void setMultiLine(boolean ml) {
      attr.multiLine = ml;
   }

   /**
    * Get whether this is a fixed section element.
    */
   public boolean isMultiLine() {
      return attr.multiLine;
   }

   public boolean isJustify() {
      return attr.justify;
   }

   /**
    * Set if this paintable is the first paintable generated for
    * a text element. Normally it is true for the first line and
    * false for the following.
    */
   public void setFirstPaintable(boolean first) {
      attr.firstPaintable = first;
   }

   /**
    * Check if a text paintable is the first paintable for an element.
    */
   public boolean isFirstPaintable() {
      return attr.firstPaintable;
   }

   /**
    * Set if this paintable is the first paintable generated for
    * a text element. Normally it is true for the first line and
    * false for the following.
    */
   public void setNewLine(boolean newLine) {
      attr.newLine = newLine;
   }

   /**
    * Paint the text.
    */
   @Override
   public void paint(Graphics g) {
      g.setFont(attr.font);

      Shape clip = g.getClip();
      double clipWidth = clip != null ? clip.getBounds().getWidth() : getBounds().getWidth();

      if(g instanceof Graphics2D) {
         ((Graphics2D) g).clip(new Rectangle2D.Double(x, y, clipWidth, height));
      }
      else {
         g.clipRect(x, (int) y, (int) clipWidth, (int) (height));
      }

      String str = getText();
      FontMetrics fm = Common.getFractionalFontMetrics(attr.font);
      float w = Common.stringWidth(str, attr.font, fm);

      if(width == 0) {
         width = (short) Common.round(w);
      }

      float left = x;

      if(isMultiLine() || fillWidth > 0) {

      }
      else if((attr.align & StyleConstants.H_CENTER) != 0) {
         left = x + (width - w) / 2;
      }
      else if((attr.align & StyleConstants.H_RIGHT) != 0) {
         if(text instanceof HeaderTextLens && elem instanceof TextElementDef) {
            float shift = 0;
            Size psize;
            TextElementDef te = (TextElementDef) elem;

            psize = StyleCore.getTextSize(str, te.getFont(), te.getSpacing());
            shift = width - psize.width;
            left = x + shift;
         }
         else {
            left = x + width - w;
         }
      }
      else if((attr.align & StyleConstants.H_CURRENCY) != 0 &&
         (getElement().getUserObject() instanceof BindingInfo))
      {
         // Adjust the X position if alignment is currency.
         int dot =
            ((BindingInfo) getElement().getUserObject()).getDecimalPoints();

         // Fix bug1324584704413, to re-caculate the decimal points after script
         // execute.
         if(str != null) {
            int ndot = str.lastIndexOf('.');
            ndot = (ndot < 0) ? 0 : (str.length() - ndot - 1);
            dot = dot < ndot ? ndot : dot;
         }

         float rmax = (dot == 0) ? 0f :
            Common.stringWidth("." + Tool.getChars('8', dot), g.getFont());
         int idx = str.lastIndexOf('.');
         float rw = 0;

         if(idx != -1) {
            rw = Common.stringWidth(str.substring(0, idx), g.getFont());
         }
         else {
            rw = Common.stringWidth(str, g.getFont());
         }

         left = x + width - rmax - rw;
      }

      if(attr.bg != null) {
         g.setColor(attr.bg);
         // need to use width to fill because in a section a textpaintable
         // may be wider than the text, and width is set using setWidth
         // in printFixedContainer to stretch the textpaintable to the
         // desired width
         float h = (isMultiLine() && height > 0) ? height : Common.getHeight(attr.font);

         // @by larryl, if multiline, the string width should not be used as
         // the fill width, since the string may be truncated or wrapped
         float fillW = isMultiLine() ? width :
            // @by billh, if is justify, use fill width
            (fillWidth > 0 ? fillWidth : Math.max(w, width));

         // @by mikec, to be consistent with painter element, the background
         // filling rect alway be a little smaller than the out bound.
         Common.fillRect(g, x, y, fillW - 0.5f, (!isMultiLine() ? h : h - 1));
      }

      g.setColor(attr.fg);

      if(isMultiLine()) {
         int dot = 0;

         if(elem.getUserObject() instanceof BindingInfo) {
            dot = ((BindingInfo) elem.getUserObject()).getDecimalPoints();
         }

         // only wrap at word boundary if more than one line
         boolean wrap = height > Common.getHeight(attr.font) + 5;

         Common.paintText(g, str, new Bounds(x, y, width, height), attr.align,
            wrap, false, elem.getSpacing(), dot, true);
      }
      else if(fillWidth > 0) {
         Common.drawString(g, str, left, y + Common.getAscent(attr.font), fillWidth);
      }
      else {
         Common.drawString(g, str, left, y + Common.getAscent(attr.font));
      }

      g.setColor(attr.fg);
      g.setClip(clip);
   }

   /**
    * Get the contents of this paintable.
    */
   public String getText() {
      String rtext = getText(true);
      ReportSheet report = ((BaseElement) elem).getReport();
      ParameterTool ptool = new ParameterTool();

       if (report != null && !false && ptool.containsParameter(rtext)) {
           rtext = ptool.parseParameters(report.getVariableTable(), rtext);
       }

       return rtext;
   }

   /**
    * Get the contents of this paintable.
    */
   public String getText(boolean formatExpre) {
      if(text == null) {
         return "";
      }

      if(text instanceof String) {
         return (String) text;
      }

      if(text instanceof HeaderTextLens) {
         ReportSheet report = ((BaseElement) elem).getReport();
         Locale locale = (report == null) ? null : report.getLocale();
         return ((HeaderTextLens) text).getDisplayText(locale, formatExpre);
      }

      return ((TextLens) text).getText();
   }

   /**
    * Set the text content.
    */
   public void setText(String str) {
      text = str;
   }

   /**
    * Get the clipped string.
    */
   public String getClippedText() {
      String str = getText();

      // if text comes from a textlens, it may be larger than the area for the
      // text paintable.
      if(text instanceof TextLens) {
         int idx = Util.breakLine(str, width, getFont(), false);

         if(idx >= 0) {
            return str.substring(0, idx);
         }
      }

      return str;
   }

   /**
    * Set the alignment of the text. This is normally not used because the
    * position of this paintable already reflects the aligned text. It is
    * used when printing a header text with embedded page numbers. Because
    * the total page number is defaulted to 1000 when laying out the text,
    * the actual size of the paintable is not same as the text size. This
    * alignment allows it the adjust the text within the paintable so it
    * at least is properly aligned.
    */
   public void setAlignment(int align) {
      if(attr.align != align) {
         attr.align = align;
         // make sure bounds is calculated, it is used later to
         // adjust the alignment
         getBounds();
      }
   }

   /**
    * Get text alignment.
    */
   public int getAlignment() {
      return attr.align;
   }

   /**
    * Get text font.
    */
   public Font getFont() {
      return attr.font;
   }

   /**
    * Get text foreground.
    */
   public Color getForeground() {
      return attr.fg;
   }

   /**
    * Get text background.
    */
   public Color getBackground() {
      return attr.bg;
   }

   /**
    * Get the hyperlink defined on this element.
    */
   @Override
   public Hyperlink.Ref getHyperlink() {
      return linkAttr.hyperlink;
   }

   /**
    * Set the hyperlink of this element.
    */
   public void setHyperlink(Hyperlink.Ref link) {
      linkAttr.hyperlink = link;
   }

   /**
    * Check if this paintable must wait for the entire report to be processed.
    * This is true for elements that need information from report, such
    * as page total, table of contents page index.
    */
   @Override
   public boolean isBatchWaiting() {
      // PageNumLens or HeaderTextLens
      return text instanceof TextLens;
   }

   /**
    * Get the drill hyperlinks on this element.
    */
   protected Hyperlink.Ref[] getDrillHyperlinks() {
      return linkAttr.dlinks;
   }

   /**
    * Set the drill hyperlinks of this element.
    */
   protected void setDrillHyperlinks(Hyperlink.Ref[] links) {
      if(links == null) {
         links = new Hyperlink.Ref[0];
      }

      linkAttr.dlinks = links;
   }

   /**
    * Get all hyperlinks of this element, including hyperlink and drill
    * hyperlinks.
    */
   public Hyperlink.Ref[] getHyperlinks() {
      if(getHyperlink() == null) {
         return linkAttr.dlinks;
      }

      Hyperlink.Ref[] links = new Hyperlink.Ref[linkAttr.dlinks.length + 1];
      links[0] = getHyperlink();

      for(int i = 0; i < linkAttr.dlinks.length; i++) {
         links[i + 1] = linkAttr.dlinks[i];
      }

      return links;
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      elem = new DefaultTextContext();

      ((DefaultTextContext) elem).read(s);
      attr.format = (Format) s.readObject();

      attr = (Attr) ObjectCache.get(attr);
      elem = (ReportElement) ObjectCache.get(elem);
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      DefaultTextContext.write(stream, elem);
      stream.writeObject(attr.format);
   }

   /**
    * Used to hold text attributes.
    */
   public static class Attr implements Serializable, Cacheable {
      public int hashCode() {
         return Objects.hash(font, fg, bg, format) + align +
            (justify ? 1 : 3) + (multiLine ? 7 : 11) + (firstPaintable ? 13 : 17) +
            (newLine ? 19 : 23) + (displayOnTree ? 29 : 31);
      }

      public boolean equals(Object obj) {
         try {
            Attr attr = (Attr) obj;

            // @by larryl, if a Font to be compared is StyleFont, the styles
            // will be ignored so we should force them to be the same type
            if(font != attr.font && font != null && attr.font != null &&
               (!font.equals(attr.font) ||
                !font.getClass().equals(attr.font.getClass())))
            {
               return false;
            }

            if(fg != attr.fg && fg != null && attr.fg != null &&
               fg.getRGB() != attr.fg.getRGB())
            {
               return false;
            }

            if(bg != attr.bg && bg != null && attr.bg != null &&
               bg.getRGB() != attr.bg.getRGB())
            {
               return false;
            }

            // @by larryl, since format is obtained through TableFormat's
            // cache, or through deserialization, both would ensure a
            // unique copy per format, we can use reference comparison for
            // optimization
            return align == attr.align &&
               justify == attr.justify && format == attr.format &&
               multiLine == attr.multiLine && firstPaintable == attr.firstPaintable &&
               newLine == attr.newLine && displayOnTree == attr.displayOnTree;
         }
         catch(ClassCastException ex) {
         }

         return false;
      }

      /**
       * Clone a copy of this object.
       */
      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            return this;
         }
      }

      Font font;
      Color fg;
      Color bg;
      int align = StyleConstants.H_LEFT; // horizontal alignment
      short target = -1; // >=0 if this paintable is a target for hyperlink
      boolean justify;
      Format format;
      boolean multiLine; // a fixed text element in a section
      boolean firstPaintable = true;
      boolean newLine;
      boolean displayOnTree = true;
   }

   /**
    * Used to hold hyperlink attributes.
    */
   public static class LinkAttr implements Serializable, Cacheable {
      public int hashCode() {
         return Objects.hash(hyperlink) + Arrays.hashCode(dlinks);
      }

      public boolean equals(Object obj) {
         try {
            LinkAttr attr = (LinkAttr) obj;

            return Objects.equals(hyperlink, attr.hyperlink) &&
               Arrays.equals(dlinks, attr.dlinks);
         }
         catch(ClassCastException ex) {
         }

         return false;
      }

      /**
       * Clone a copy of this object.
       */
      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            return this;
         }
      }

      Hyperlink.Ref hyperlink = null;
      Hyperlink.Ref[] dlinks = new Hyperlink.Ref[0];
   }

   /**
    * Return attribute of text.
    */
   public Attr getAttr() {
      return attr;
   }

   /**
    * Set if TextPaintable display on report tree. .
    */
   public void setDisplayOnTree(boolean displayOnTree) {
      attr.displayOnTree = displayOnTree;
   }

   private Object text; // String or TextLens
   private Attr attr = new Attr();
   private LinkAttr linkAttr = new LinkAttr();
   private short x, width;
   private float y; // y could be large if report is generated as a single page
   private float height;
   private short fillWidth; // justify
}
