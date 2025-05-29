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
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.BindingAttr;
import inetsoft.report.internal.info.*;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTableNode;
import inetsoft.uql.viewsheet.BorderColors;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.Format;
import java.util.Locale;
import java.util.Vector;

/**
 * A TextBoxElementDef encapsulate text contents.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextBoxElementDef extends PainterElementDef
   implements TextBased, inetsoft.report.TextBoxElement, BindableElement
{
   /**
    * Create a default text boc element.
    */
   public TextBoxElementDef() {
      super();
      margin = new Insets(0, 0, 0, 0);
   }

   /**
    * Create a text element from a text lens.
    * @param lens text lens pointing to the text content.
    */
   public TextBoxElementDef(ReportSheet report, TextLens lens,
                            double winch, double hinch) {
      super(report, null, winch, hinch);

      setPainter(painter = new TextPainter(this));
      painter.setText(text = lens);
      setPadding(report.padding);
      setJustify(report.justify);
      setBorderColor(getForeground());
   }

   public TextBoxElementDef(ReportSheet report, TextLens lens) {
      this(report, lens, false);
   }

   /**
    * Create a text element from a text lens.
    * @param lens text lens pointing to the text content.
    * @param forceNoWrap force to not apply wrap, this is for vs printlayout with text embed as url.
    */
   public TextBoxElementDef(ReportSheet report, TextLens lens, boolean forceNoWrap) {
      super(report, null);

      painter = new TextPainter(this);
      painter.setForceNoWrap(forceNoWrap);
      setPainter(painter);
      painter.setText(text = lens);
      setPadding(report.padding);
      setJustify(report.justify);
      setBorderColor(getForeground());
   }

   /**
    * CSS Type to be used when styling this element.
    */
   @Override
   public String getCSSType() {
      return CSSConstants.TEXT_BOX;
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   protected ElementInfo createElementInfo() {
      return new TextBoxElementInfo();
   }

   /**
    * Get element info.
    */
   @Override
   public ElementInfo getElementInfo() {
      TextBoxElementInfo info = (TextBoxElementInfo) super.getElementInfo();
      info.setText(getText());

      return info;
   }

   /**
    * Get text box element info.
    */
   protected TextBoxElementInfo getTextBoxInfo() {
      return (TextBoxElementInfo) getElementInfo();
   }

   /*
    * Clone a TextBoxElementDef with the same attributes as the current one,
    * and with the new text as the content.
    */
   public TextBoxElementDef clone(String text) {
      try {
         TextBoxElementDef t = (TextBoxElementDef) this.clone();
         t.setText(text);

         if(filters != null) {
            t.filters = (BindingAttr) filters.clone();
         }

         return t;
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Get the size of the element.
    * @return size in inches.
    */
   @Override
   public Size getSize() {
      ReportSheet rpt = getReport();

       if ("true".equals(getProperty(GROW)) && isInSection() &&
               rpt != null && !false) {
           return null;
       }

       return ((PainterElementInfo) einfo).getSize();
   }

   /**
    * Set the border around this text box.
    * @param border line style in StyleConstants.
    */
   @Override
   public void setBorder(int border) {
      getTextBoxInfo().setBorder(border);
   }

   /**
    * Set the border, and the flag indicating it was set by user (call from Bean).
    * @param border line style in StyleConstants.
    */
    public void setBorderUser(int border) {
      setBorder(border);
      getTextBoxInfo().setBordersByUser(true);
   }

   /**
    * Get the border around this text box.
    * @return border line style.
    */
   @Override
   public int getBorder() {
      return getTextBoxInfo().getBorder();
   }

   /**
    * Set the border color. If the border color is not set, the foreground
    * color is used to draw the border.
    */
   @Override
   public void setBorderColor(Color color) {
      getTextBoxInfo().setBorderColor(color);
   }

   /**
    * Set the border color, and the flag indicating it was set by user (call from Bean).
    */
   public void setBorderColorUser(Color color) {
      setBorderColor(color);
      getTextBoxInfo().setBorderColorByUser(true);
   }

   @Override
   public void setBorderColors(BorderColors bcolors) {
       getTextBoxInfo().setBorderColor(bcolors.topColor);
   }

   /**
    * Get the border color.
    */
   @Override
   public Color getBorderColor() {
      return getTextBoxInfo().getBorderColor();
   }

   @Override
   public BorderColors getBorderColors() {
      BorderColors borderColors = new BorderColors();
      borderColors.topColor = getTextBoxInfo().getBorderColor();
      borderColors.leftColor = getTextBoxInfo().getBorderColor();
      borderColors.bottomColor = getTextBoxInfo().getBorderColor();
      borderColors.rightColor = getTextBoxInfo().getBorderColor();
      return borderColors;
   }

   /**
    * Set the individual border line styles. This overrides the default border
    * setting.
    * @param border line styles.
    */
   @Override
   public void setBorders(Insets borders) {
      getTextBoxInfo().setBorders(borders);
   }

   /**
    * Get the individual border line styles.
    * @return border line style..
    */
   @Override
   public Insets getBorders() {
      return getTextBoxInfo().getBorders();
   }

   /**
    * Set the textbox shape. One of StyleConstants.BOX_RECTANGLE or
    * StyleConstants.BOX_ROUNDED_RETANGLE.
    * @param shape textbox shape option.
    */
   @Override
   public void setShape(int shape) {
      getTextBoxInfo().setShape(shape);
   }

   /**
    * Get the textbox shape.
    * @return the textbox shape.
    */
   @Override
   public int getShape() {
      return getTextBoxInfo().getShape();
   }

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   @Override
   public boolean isJustify() {
      return getTextBoxInfo().isJustify();
   }

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   @Override
   public void setJustify(boolean justify) {
      getTextBoxInfo().setJustify(justify);
   }

   /**
    * Set the shadow option of this text box.
    */
   @Override
   public void setShadow(boolean shadow) {
      ((TextBoxElementInfo) einfo).setShadow(shadow);
   }

   /**
    * Check the shadow option of this text box.
    */
   @Override
   public boolean isShadow() {
      return ((TextBoxElementInfo) einfo).isShadow();
   }

   /**
    * Get text box padding space.
    */
   @Override
   public Insets getPadding() {
      return ((TextBoxElementInfo) einfo).getPadding();
   }

   /**
    * Set text box padding space.
    */
   @Override
   public void setPadding(Insets padding) {
      ((TextBoxElementInfo) einfo).setPadding(padding);
   }

   /**
    * Set the corner width and height for rounded rectangle shape.
    */
   @Override
   public void setCornerSize(Dimension corner) {
      ((TextBoxElementInfo) einfo).setCornerSize(corner);
   }

   /**
    * Get the corner width and height of rounded rectangle.
    */
   @Override
   public Dimension getCornerSize() {
      return ((TextBoxElementInfo) einfo).getCornerSize();
   }

   /**
    * Set the painter object.
    */
   @Override
   public void setPainter(Painter painter) {
      super.setPainter(this.painter = (TextPainter) painter);

      if(this.painter != null) {
         this.painter.setElement(this);
      }
   }

   /**
    * Return the text in the text lens.
    */
   @Override
   public String getText() {
      return text == null ? "" : text.getText();
   }

   /**
    * Return the text in the text lens.
    */
   @Override
   public String getDisplayText() {
      String str = getText();
      String fmt = getProperty("__format__");
      ReportSheet report = getReport();

      if(value != null && XConstants.MESSAGE_FORMAT.equals(fmt)) {
         String spec = getProperty("__format_spec__");
         Locale locale = report.getLocale();
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
   @Override
   public void setText(String text) {
      if(text == null) {
         text = "";
      }

      setTextLens(new DefaultTextLens(text));
   }

   /**
    * Set the text source of this text element.
    */
   @Override
   public void setTextLens(TextLens lens) {
      painter.setText(text = lens);
      resetHighlight();
      getHighlight();
      ((TextBoxElementInfo) einfo).setText(getText());
   }

   /**
    * Get the text lens of this text box.
    */
   @Override
   public TextLens getTextLens() {
      return text;
   }

   /**
    * Get edit alignment.
    */
   public int getEditAlignment() {
      if(isInSection()) {
         return getTextAlignment();
      }

      return getAlignment();
   }

   /**
    * Set edit alignment.
    */
   public void setEditAlignment(int align) {
      if(isInSection()) {
         setTextAlignment(align);
      }
      else {
         setAlignment(align);
      }
   }

   /**
    * Get the text alignment.
    */
   @Override
   public int getTextAlignment() {
      return getTextBoxInfo().getTextAlignment();
   }

   /**
    * Set the text alignment.
    */
   @Override
   public void setTextAlignment(int align) {
      getTextBoxInfo().setTextAlignment(align);
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "TextBox";
   }

   @Override
   public Object clone() {
      TextBoxElementDef elem = (TextBoxElementDef) super.clone();
      TextPainter pt = painter.clone(painter.getTextLens(), false);

      elem.setPainter(pt);
      elem.setTextLens(text);

      // @by larryl, this clone() would trigger setUserObject, which in-turn
      // calls setElement on the painter. At this point the new element is
      // pointing to the painter of this element, and causes the painter in
      // this element to point to the new element as the parent.
      // Restore element here.
      painter.setElement(this);

      if(filters != null) {
         elem.filters = (BindingAttr) filters.clone();
      }

      return elem;
   }

   /**
    * Set the highlight group setting.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return ((TextBoxElementInfo) einfo).getHighlightGroup();
   }

   /**
    * Get the highlight group setting.
    */
   @Override
   public void setHighlightGroup(HighlightGroup group) {
      ((TextBoxElementInfo) einfo).setHighlightGroup(group);
      resetHighlight();
      getHighlight();
   }

   /**
    * Current font.
    */
   @Override
   public void setFont(Font font) {
      super.setFont(font);
      resetInfo();
   }

   /**
    * Current font.
    */
   @Override
   public void setForeground(Color fore) {
      super.setForeground(fore);
      resetInfo();
   }

   /**
    * Current font.
    */
   @Override
   public void setBackground(Color back) {
      super.setBackground(back);
      resetInfo();
   }

   /**
    * Get the font. Returned value includes highlight effect.
    */
   @Override
   public Font getFont() {
      Highlight attr = getHighlight();

      return (attr != null && attr.getFont() != null) ? attr.getFont() :
         super.getFont();
   }

   /**
    * Get the background. Returned value includes highlight effect.
    */
   @Override
   public Color getBackground() {
      Highlight attr = getHighlight();

      return (attr != null && attr.getBackground() != null) ?
         attr.getBackground() : super.getBackground();
   }

   /**
    * Get the foreground. Returned value includes highlight effect.
    */
   @Override
   public Color getForeground() {
      Highlight attr = getHighlight();

      return (attr != null && attr.getForeground() != null) ?
         attr.getForeground() : super.getForeground();
   }

   /**
    * Get the data in object, it's used for binding.
    */
   @Override
   public Object getData() {
      return value;
   }

   /**
    * Set the data in object, it's used for binding.
    */
   @Override
   public void setData(Object val) {
      setData(val, null);
   }

   /**
    * Set the data in object, it's used for binding.
    */
   @Override
   public void setData(Object val, Format format) {
      value = val;

      try {
         Locale locale = getReport().getLocale();
         getReport().setValue(this, TextElementDef.format(this, value, locale, format));
      }
      catch(Exception ex) {
         LOG.warn("Failed to set value to \"" + val +
            "\" using format " + format, ex);
      }

      resetHighlight();
      getHighlight();
   }

   /**
    * Set highlight.
    */
   @Override
   public void setHighlight(TextHighlight highlight) {
      if(highlight == null) {
         highlight = TextElementDef.EMPTY_HIGHLIGHT;
      }

      this.highlight = highlight;
      painter.setHLAttributes(highlight);
   }

   /**
    * Get highlight.
    */
   @Override
   public TextHighlight getHighlight() {
      ReportSheet report = getReport();

      if(report != null && !true) {
         painter.setHLAttributes(null);
         return null;
      }

      HighlightGroup highlightGroup =
         ((TextBoxElementInfo) einfo).getHighlightGroup();
      if(highlightGroup != null && highlight == null) {
         highlight = (TextHighlight) highlightGroup.findGroup(value == null ?
            text.getText() : value);
         // cache to avoid multiple evaluation
         if(highlight == null) {
            highlight = new TextHighlight();
         }

         painter.setHLAttributes(highlight);
      }

      return highlight;
   }

   /**
    * Reset highlight.
    */
   @Override
   public void resetHighlight() {
      highlight = null;

      if(painter != null) {
         painter.resetHLAttributes();
      }
   }

   /**
    * If element information changed, make sure the internal states are in sync.
    */
   private void resetInfo() {
      if(painter != null) {
         painter.setElement(this);
      }

      resetHighlight();
   }

   /**
    * Get the presenter to be used in this element.
    */
   @Override
   public PresenterRef getPresenter() {
      return ((TextBoxElementInfo) einfo).getPresenter();
   }

   /**
    * Set the presenter to be used in this element.
    */
   @Override
   public void setPresenter(PresenterRef ref) {
      ((TextBoxElementInfo) einfo).setPresenter(ref);
   }

   /**
    * Get the textID, which is used for i18n support.
    */
   @Override
   public String getTextID() {
      return textID;
   }

   /**
    * Set the textID, which is used for i18n support.
    */
   @Override
   public void setTextID(String textID) {
      this.textID = textID;
   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return filters;
   }

   /**
    * Get the minimum height for a paintable.
    */
   @Override
   public float getMinimumHeight() {
      // this forces each textbox paintable hold at least one line
      FontMetrics fm = Common.getFractionalFontMetrics(this.painter.getFont());
      return fm.getHeight();
   }

   /**
    * Get the painter prepared for return prefer size.
    */
   @Override
   protected Painter getPainterForPreferredSize() {
      ReportSheet report = getReport();
      PresenterRef presenter_ = getPresenter();

      if((report == null || true) && presenter_ != null) {
         try {
            Presenter presenter = presenter_.createPresenter();
            presenter.setBackground(getBackground());

            if(ppainter == null) {
               ppainter = new PresenterPainter(presenter);
            }
            else {
               ppainter =
                  (PresenterPainter) ((PresenterPainter)ppainter).clone();
            }

            Object val = null;

            if(presenter.isRawDataRequired()) {
               val = getData();
            }
            else {
               val = getText();
            }

            if(val != null && presenter.isPresenterOf(val.getClass())) {
               ((PresenterPainter) ppainter).setObject(val);

               if(getSize() != null) {
                  final int textAlignment = painter.getTextAlignment();
                  final Dimension dim = presenter.getPreferredSize(val);
                  final Size preferredSize = new Size(dim.getWidth(), dim.getHeight());
                  final Size textBoxSize = getPainterPreferredSize(painter);
                  final Bounds textBoxBounds =
                     new Bounds(0, 0, textBoxSize.width, textBoxSize.height);
                  final Bounds bounds =
                     Common.alignCell(textBoxBounds, preferredSize, textAlignment);
                  presenter.setAlignmentOffset(new Dimension((int) bounds.x, (int) bounds.y));
               }
            }

            ((PresenterPainter) ppainter).getPresenter().setFont(getFont());
         }
         catch(Exception ex) {
            LOG.error("Failed to get presenter painter", ex);
         }
      }

      if(ppainter != null) {
         return ppainter;
      }

      return super.getPainterForPreferredSize();
   }

   /**
    * Create a PainterPaintable.
    */
   @Override
   protected PainterPaintable createPaintable(float x, float y, float painterW,
                                              float painterH, Dimension pd,
                                              int prefW, int prefH,
                                              ReportElement elem,
                                              Painter painter, int offsetX,
                                              int offsetY, int rotation) {
      ReportSheet report = getReport();
      PresenterRef presenter_ = getPresenter();

      if((report == null || true) && presenter_ != null) {
         try {
            Presenter presenter = presenter_.createPresenter();
            presenter.setBackground(getBackground());

            if(ppainter == null) {
               ppainter = getPainterForPreferredSize();
            }

            if(ppainter != null) {
               Object val = null;

               if(presenter.isRawDataRequired()) {
                  val = getData();
               }
               else {
                  val = getText();
               }

               if(val != null && presenter.isPresenterOf(val.getClass())) {
                  ((PresenterPainter) ppainter).setObject(val);
                  return new PresenterPainterPaintable(x, y, painterW, painterH,
                                                       pd, prefW, prefH, elem,
                                                       ppainter, offsetX,
                                                       offsetY, rotation);
               }
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get presenter painter paintable", ex);
         }
      }

      // adjust textbox height to multiple of lines
      painterH -= this.painter.getHeightAdjustment(this, pd, offsetY,
                                                   painterW, painterH);

      if(isTextBoxFlow) {
         TextPainter painter2 = (TextPainter) painter;
         int dot = 0;
         int sectionDecimal = 0;
         boolean wrap = true;
         Font fn = getFont();
         FontMetrics fm = Common.getFontMetrics(fn);
         Bounds nbound = new Bounds();
         Vector lineoff = new Vector(2);
         Margin adj = painter2.calcBorderMargin();
         Insets pad = painter2.calcPadding();
         BindingInfo info = (BindingInfo) elem.getUserObject();
         Insets borders = painter2.getBorders();
         pd = new Dimension((int) painterW, (int) painterH);

         if(info != null) {
            sectionDecimal = (short) info.getDecimalPoints();
         }

         if(sectionDecimal >= 0) {
            dot = sectionDecimal;
            wrap = painterH > Common.getHeight(getFont()) * 1.3;
         }

         float rmax = (dot == 0) ? 0f :
            Common.stringWidth("." + Tool.getChars('9', dot), fn, fm);
         String txt = painter2.getDisplayText().substring(txtOffset);
         Bounds bounds = new Bounds(
                 (float) (x + adj.left + pad.left),
                 (float) (y + adj.top + pad.top),
                 (float) (painterW - adj.left - adj.right - pad.left - pad.right),
                 (float) (painterH - adj.top - adj.bottom - pad.top - pad.bottom));
         Vector lines = Common.processText(txt, bounds,
            getTextAlignment(), wrap, fn, nbound, lineoff, getSpacing(), fm, rmax);

         // trim the lines that the paintable can display
         float rowHeight = Common.getHeight(fn);
         int rows = (int) ((painterH - rowHeight * 0.3) / rowHeight);
         int end = rows == 0 || lines.size() == 0 ? 0 :
            ((int[]) lines.get(Math.min(rows, lines.size()) -1))[1];
         painter2.setText(txt.substring(0, end));

         // create default borders if borders is null
         if(borders == null) {
            int border = painter2.getBorder();
            borders = new Insets(border, border, border, border);
         }

         // remove top border for non-header paintable
         if(txtOffset != 0) {
            borders.top = StyleConstants.NO_BORDER;
         }

         // remove bottom border for non-footer paintable
         if((txtOffset + end) != getDisplayText().length()) {
            borders.bottom = StyleConstants.NO_BORDER;
         }

         painter2.setBorders(borders);
         txtOffset += end;
         offsetX = 0;
         offsetY = 0;
      }

      return new TextPainterPaintable(x, y, painterW, painterH, pd, prefW,
                                      prefH, elem, painter, offsetX, offsetY,
                                      rotation);
   }

   /**
    * Set an user object. The object must be serializable.
    */
   @Override
   public void setUserObject(Object obj) {
      super.setUserObject(obj);

      if(painter != null) {
         painter.setElement(this); // initialize element properties in painter
      }
   }

   /**
    * Get the drill hyperlinks on this element.
    */
   @Override
   public Hyperlink.Ref[] getDrillHyperlinks() {
      return dlinks;
   }

   /**
    * Set the drill hyperlinks of this element.
    */
   @Override
   public void setDrillHyperlinks(Hyperlink.Ref[] links) {
      if(links == null) {
         links = new Hyperlink.Ref[0];
      }

      this.dlinks = links;
   }

   /**
    * Print the element at the printHead location.
    * @return true if the element is not completely printed and
    * need to be called again.
    */
   @Override
   public boolean print(StylePage pg, ReportSheet report) {
      isTextBoxFlow = isTextBoxFlow();

      return super.print(pg, report);
   }

   /**
    * Reset the printing so the any remaining portion of the painter
    * is ignored, and the next call to print start fresh.
    */
   @Override
   public void resetPrint() {
      super.resetPrint();
      txtOffset = 0;
   }

    /**
    * Get the getPainterH.
    */
   @Override
   protected float getPainterH(float painterH, float areaH) {
      // if the text can't fill the first area, don't paint it by flow control
      isTextBoxFlow &= !(txtOffset == 0 && painterH <= areaH);

      if(isTextBoxFlow) {
         return areaH;
      }
      else {
         return super.getPainterH(painterH, areaH);
      }
   }

   /**
    * Check if has reach the end of the element.
    */
   @Override
   protected boolean isEnd() {
      return isTextBoxFlow ?
         txtOffset >= Math.max(1, getDisplayText().length() - 1) :
         super.isEnd();
   }

   /**
    * Check if this element is paint by flow control.
    */
   private boolean isTextBoxFlow() {
      ReportSheet report = getReport();

      if(report == null || !isBreakable()) {
         return false;
      }

      return false;
   }

   @Override
   public XTableNode getTableNode() {
      return tableNode;
   }

   @Override
   public void setTableNode(XTableNode tableNode) {
      this.tableNode = tableNode;
   }

   private BindingAttr filters;
   private Object value = null; // associated with lens when binding
   private TextPainter painter;
   private TextLens text; // this may be different from TextPainter in header
   private String textID; // i18n
   private transient Hyperlink.Ref[] dlinks = new Hyperlink.Ref[0];
   private transient Painter ppainter;
   private transient TextHighlight highlight;
   private transient boolean isTextBoxFlow;
   private int txtOffset = 0;
   private transient XTableNode tableNode;

   private static final Logger LOG = LoggerFactory.getLogger(TextBoxElementDef.class);
}
