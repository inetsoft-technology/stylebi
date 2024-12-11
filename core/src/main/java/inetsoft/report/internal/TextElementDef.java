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
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.internal.binding.BindingAttr;
import inetsoft.report.internal.info.ElementInfo;
import inetsoft.report.internal.info.TextElementInfo;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTableNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.Format;
import java.util.Locale;

/**
 * A TextElementDef encapsulate text contents.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextElementDef extends BaseElement
   implements TextBased, TextElement, BindableElement
{
   /**
    * Create a text element from a text lens.
    */
   public TextElementDef() {
      super();
   }

   /**
    * Create a text element from a text lens.
    * @param lens text lens pointing to the text content.
    */
   public TextElementDef(ReportSheet report, TextLens lens) {
      super(report, false);

      setJustify(report.justify);
      setTextAdvance(report.textadv);
      setOrphanControl(report.orphan);

      setTextLens(lens);
      cache = true;
      breakable = true;
   }

   /**
    * Format the value in a section
    */
   public static Object format(ReportElement elem, Object val, Locale locale, Format xformat) {
      if(val == null) {
         return "";
      }

      Format format = null;
      String spec = elem.getProperty("__format_spec__");
      String fmt = elem.getProperty("__format__");

      if(fmt != null) {
         format = TableFormat.getFormat(fmt, spec, locale);
      }

      if(format != null) {
         try {
            val = XUtil.format(format, val);
         }
         catch(Throwable ex) {
         }
      }
      else if(xformat != null) {
         val = XUtil.format(xformat, val);
      }

      return val;
   }

   /**
    * CSS Type to be used when styling this element.
    */
   @Override
   public String getCSSType() {
      return CSSConstants.TEXT;
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   protected ElementInfo createElementInfo() {
      return new TextElementInfo();
   }

   /**
    * Return the size that is needed for this element.
    */
   @Override
   public Size getPreferredSize() {
      if(!cache || psize == null) {
         psize = StyleCore.getTextSize(getDisplayText(), getFont(),
                                       getSpacing());
         psize.width += getTextAdvance();
      }

      return psize;
   }

   /**
    * Return true if the element can be broken into segments.
    */
   @Override
   public boolean isBreakable() {
      return breakable;
   }

   /**
    * Return true if this element is the last one on a line.
    */
   @Override
   public boolean isLastOnLine() {
      String str = (ptext == null) ? getDisplayText() : ptext;
      return (str.length() > MAX_LINE) || str.indexOf('\n') >= 0;
   }

   /**
    * Return the text in the text lens.
    */
   @Override
   public String getText() {
      if(lens != null && (!cache || lensText == null)) {
         lensText = getTextLens().getText();
      }

      return (lensText == null) ? "" : lensText;
   }

   /**
    * Set the text contained in this text element.
    */
   @Override
   public void setText(String text) {
      if(text == null) {
         text = "";
      }

      // optimization, since almost all lens are DefaultTextLens
      // only for DefaultTextLens and not their subclasses since they may
      // have special logic for replacing the text
      if(lens.getClass() == DefaultTextLens.class) {
         ((DefaultTextLens) lens).setText(text);
         setTextLens(lens);
      }
      // fix bug1292958091249, should not reset the lens if it is HeaderTextLens
      else if(lens instanceof HeaderTextLens) {
         ((HeaderTextLens) lens).setText(text);

         // fix bug1321914559988, if the lens is HTextLens, HFTextFormatter will
         // wipe off this HTextLens and use it's internal tablelens to display
         // the text, so here we should set the text to the internal lens.
         if(lens instanceof HFTextFormatter.HTextLens) {
            TextLens tlens = ((HFTextFormatter.HTextLens) lens).getTextLens();

            if(tlens instanceof DefaultTextLens) {
               ((DefaultTextLens) tlens).setText(text);
            }
         }

         setTextLens(lens);
      }
      else {
         setTextLens(new DefaultTextLens(text));
      }
   }

   /**
    * Get the text for final displaying.
    */
   @Override
   public String getDisplayText() {
      if(lens instanceof HeaderTextLens) {
         return ((HeaderTextLens) lens).getDisplayText(getReport().getLocale());
      }

      String str = getText();
      String fmt = getProperty("__format__");

      if(value == null && XConstants.MESSAGE_FORMAT.equals(fmt)) {
         String spec = getProperty("__format_spec__");
         Locale locale = getReport().getLocale();
         Format format = TableFormat.getFormat(fmt, spec, locale);

         if(format != null && spec != null && spec.length() > 0) {
            return format.format(str);
         }
      }

      str = (str == null) ? "" : str;

       getReport();
       if(false && str.isEmpty()) {
         str = Catalog.getCatalog().getString("Empty Text");
      }

      return str;
   }

   /**
    * Set the text source of this text element.
    */
   @Override
   public void setTextLens(TextLens text) {
      lens = text;
      lensText = null;
      psize = null;
      ptext = null;
      resetHighlight();
      ((TextElementInfo) einfo).setText(getText());
   }

   /**
    * Return the text lens of this text element.
    */
   @Override
   public TextLens getTextLens() {
      return lens;
   }

   /**
    * Current font.
    */
   @Override
   public void setFont(Font font) {
      super.setFont(font);
      resetHighlight();
      psize = null; // clear cached size
   }

   /**
    * Current font.
    */
   @Override
   public void setForeground(Color fore) {
      super.setForeground(fore);
      resetHighlight();
   }

   /**
    * Current font.
    */
   @Override
   public void setBackground(Color back) {
      super.setBackground(back);
      resetHighlight();
   }

   /**
    * Get the presenter to be used in this element.
    */
   @Override
   public PresenterRef getPresenter() {
      return ((TextElementInfo) einfo).getPresenter();
   }

   /**
    * Set the presenter to be used in this element.
    */
   @Override
   public void setPresenter(PresenterRef ref) {
      ((TextElementInfo) einfo).setPresenter(ref);
   }

   /**
    * Rewind a paintable. This is call if part of a element is undone
    * (in section). The paintable should be inserted in the next print.
    */
   @Override
   public void rewind(Paintable pt) {
      if(pt instanceof TextPaintable) {
         String str = ((TextPaintable) pt).getText();
         String disp = getDisplayText();

         if(ptext == null || ptext.length() == 0) {
            // if the text is fully rewound, reset text to null so logic check
            // for text == null to determine if this is the first time the
            // element is printed would work correctly
            ptext = str.equals(disp) ? null : str;
         }
         else {
            // try to restore the init state if full rewound
            if(disp.length() >= str.length() + ptext.length() &&
               disp.startsWith(str) && disp.endsWith(ptext) &&
               disp.substring(str.length(), disp.length() - ptext.length()).
               trim().length() == 0)
            {
               ptext = null;
            }

            if(ptext != null) {
               ptext = str + "\n" + ptext;
            }
         }
      }
   }

   /*
    * Clone a TextElementDef with the same attributes as the current one,
    * and with the new text as the content.
    */
   public TextElementDef clone(String text) {
      try {
         TextElementDef t = (TextElementDef) super.clone();

         if(lens != null) {
            t.lens = (TextLens) lens.clone();
         }

         if(filters != null) {
            t.filters = (BindingAttr) filters.clone();
         }

         t.ptext = ptext;

         return t;
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Print the element at the printHead location. A text segment may
    * need to be broken up to span across a page. So this function may
    * need to be called multiple times, until it returns false.
    * @return true if the element is not completed printed and
    * need to be called again.
    */
   @Override
   public boolean print(StylePage pg, ReportSheet report) {
      super.print(pg, report);

      // @by larryl, optimization, since reset() is called when
      // the entire report is reset(), this only reset once per report
      if(!inited_) {
         inited_ = true;
         String attr = getProperty(GROW);
         canGrow = attr != null && attr.equals("true");
         presenter_ = getPresenter();

         if(getParent() instanceof SectionBand) {
            SectionBand band = (SectionBand) getParent();
            elementIdx = band.getElementIndex(getID());
         }
      }

      // check if this text as {P} tag and the page number is < 1, skip
      if(true && (lens instanceof HeaderTextLens) &&
         (lens.getText().toUpperCase().indexOf("{P}") >= 0 ||
         lens.getText().toUpperCase().indexOf("{P,") >= 0))
      {
         HFTextFormatter fmt = report.getHFTextFormatter();

         if(fmt != null && fmt.getPageNumber() < 1) {
            return false;
         }
      }

      if(!checkVisible()) {
         return false;
      }

      // check orphan/widow
      boolean checkOrphan = isOrphanControl();
      // true if first line in a section text
      boolean inSection = isInSection();
      Object parent = getParent();
      boolean inFixed = parent instanceof FixedContainer;

      boolean firstsec = inSection && ptext == null;
      boolean firstfixed = inFixed && ptext == null;

      if(inSection && presenter_ != null) {
         try {
            Presenter presenter = presenter_.createPresenter();
            presenter.setFont(getFont());
            presenter.setBackground(getBackground());
            PresenterPainter painter = new PresenterPainter(presenter);
            Object obj = presenter.isRawDataRequired() ? getData() : getText();

            if(obj != null && presenter.isPresenterOf(obj.getClass())) {
               painter.setObject(obj);
               SectionBand band = (SectionBand) parent;
               Rectangle bounds = band.getBounds(elementIdx);

               Bounds b = new Bounds(bounds);
               Dimension psize = presenter.getPreferredSize(obj);
               Bounds b2 = Common.alignCell(b, new Size(psize), getAlignment());
               int offsetX = (int) (b.x - b2.x);
               int offsetY = (int) (b.y - b2.y);

               pg.addPaintable(new PresenterPainterPaintable(
                  report.printBox.x + report.printHead.x,
                  report.printBox.y + report.printHead.y,
                  bounds.width, bounds.height,
                  psize, bounds.width, bounds.height,
                  this, painter, offsetX, offsetY, 0));

               return false;
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to create presenter painter paintable", ex);
         }
      }

      // start from beginning, otherwise we are printing the remaining
      // text segment
      if(ptext == null) {
         firstprint = true;
         ptext = getDisplayText();
         newLine = ptext != null && ptext.indexOf('\n') >= 0;
         lastX = report.lastHead.x;

         // if in section, we don't support tab otherwise they overlap because
         // we adjust the painter position and width to fill the field
         if(inFixed && ptext != null) {
            ptext = ptext.replace('\t', ' ');
         }

         // if empty and in design time, print an empty string
          if((firstsec || false) &&
            (ptext != null && ptext.length() == 0))
         {
            ptext = " ";
         }
      }

      // if in a section and is fixed size, don't calculate. Leave the
      // calculation in the paintable - optimization
      if(firstsec) {
         SectionBand band = (SectionBand) parent;
         Rectangle bounds = band.getBounds(elementIdx);

         if(!canGrow && bounds.height <= report.printBox.height - report.printHead.y) {
            boolean pagecnt = (lens instanceof HeaderTextLens) &&
               (lens.getText().toUpperCase().indexOf("{N}") >= 0 ||
               lens.getText().toUpperCase().indexOf("{P}") >= 0 ||
               lens.getText().toUpperCase().indexOf("{N,") >= 0 ||
               lens.getText().toUpperCase().indexOf("{P,") >= 0);

            if(pagecnt) {
               TextPaintable pt = new TextPaintable(lens,
                  report.printBox.x + report.printHead.x,
                  report.printBox.y + report.printHead.y, this, inSection);
               pt.setAlignment(getAlignment());
               pt.setNewLine(newLine);
               pg.addPaintable(pt);
            }
            else {
               TextPaintable pt = new TextPaintable(ptext,
                  report.printBox.x + report.printHead.x,
                  report.printBox.y + report.printHead.y, bounds.width,
                 (float) bounds.getHeight(), this, inSection);
               pt.setNewLine(newLine);
               pg.addPaintable(pt);
            }

            ptext = null; // @by larryl, finished printing, clear pending text

            return false;
         }
      }

      // if hanging indent, advance to the indentation point
      if(getHindent() > 0) {
         report.printHead.x = Math.max(report.printHead.x, lastX);

         // @by billh, when lastX is very large, to avoid infinite
         // loop, we always make a little room to go on printing
         if(lastX >= report.printBox.width) {
            report.printHead.x = report.printBox.width - 10;
         }
      }

      Font font = getFont();
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      // line advancement
      float fontH = Common.getHeight(font);
      float adv = fontH + getSpacing();
      float minAdv = report.lineH; // minimum adv

      // check if the remaining area can fit in one line, if in a section
      // element, never skip the line. We use fontH instead of adv since
      // the spacing is only used on the second line and should not be used
      // to determine if a text string can be fitted on a page
      if(!firstfixed && report.printHead.y + fontH > report.printBox.height) {
         if(report.isHeaderFooterElement(this)) {
            fontH = report.printBox.height - report.printHead.y;
            adv = fontH + getSpacing();
         }
         else {
            report.printHead.y += adv;
            return true;
         }
      }
      // text out of bound
      // @by mikec, to determine if the text is out of bound.
      // if the text is null or no content, it should not be printed
      // so should not be treated as out of bound.
      // but in design time, the text will alwasy contains at least a
      // one space string.
      else {
          if (report.printHead.x >= report.printBox.width &&
                  ptext != null && ptext.length() > 0 && !(false && " ".equals(ptext))) {
              return true;
          }
      }

      float leftX = (float) getIndent() * 72 + getHindent();

      // indentation can not be outside of parea
      if(leftX >= report.printBox.width - 10) {
         leftX = Math.max(0, report.printBox.width - 10);
      }

      boolean newline = false; // line break at newline

      // print text
      while(ptext != null && ptext.length() > 0) {
         char sep = '\n';
         String line = ptext;
         int idx = line.indexOf('\n');

         if(idx >= 0) {
            // line is a string without newline
            line = line.substring(0, idx);
            if(line.endsWith("\r")) {
               line = line.substring(0, line.length() - 1);
            }

            newline = true;
         }
         else {
            newline = false;
         }

         int tab = line.indexOf('\t');

         // tab separates text into segments
         if(tab >= 0) {
            line = line.substring(0, tab);
            idx = tab;
            sep = '\t';
            newline = false;
         }

         line = (idx >= 0) ? line : ptext;
         ptext = (idx >= 0) ? ptext.substring(idx + 1) : null;

         // line contains a string without a newline
         // text contains the remaining text
         String ln = line;

         // @by henryh, 2004-12-30
         // to avoid infinite loop when the width of last char is bigger than
         // print space.
         float w;

         if(line.length() == 1) {
            w = Math.max(
               Common.stringWidth(line, font, Common.getFractionalFontMetrics(font)) + 1,
               report.printBox.width - report.printHead.x);
         }
         else {
            w = report.printBox.width - report.printHead.x;
         }

         TextPaintable paintable = null;
         int end = -1;

         // bug1288102132320 for one-line text in section, not warp it
         if(inSection && report.printHead.y + 2 * adv > report.printBox.height)
         {
            end = Util.breakLine(line, w, font, false);
         }
         else {
            // wrap line if longer than the width
            end = Util.breakLine(line, w, font, true);
         }

         if(w - getTextAdvance() < 0) {
            ln = "";
         }
         else if(end >= 0) {
            // if the break is not at a whitespace char, and
            // this text element is printing from a middle of
            // a line, then don't print anything this line
            // so we don't break a word in half
            // @by larryl 2003-9-23, change >= to > lastX to avoid infinite loop
            if(report.printHead.x > leftX && report.printHead.x > lastX &&
               end < line.length() &&
               !Character.isWhitespace(line.charAt(end)) &&
               line.charAt(end) <= 255)
            {
               end = 0;
            }

            ln = line.substring(0, end);

            // skip white space
            while(end < line.length() &&
                  Character.isWhitespace(line.charAt(end)))
            {
               end++;
            }

            line = line.substring(end);

            newline = false;

            // @by larryl, if the line wrapped before the tab, we undo any
            // info recorded against the tab so the processing follows the
            // normal path without any special handling for tab until it
            // goes to the next iteration (next line)
            if(end < tab) {
               ptext = line + '\t' + ptext;
               line = "";
               sep = '\n';
            }
         }
         else {
            line = "";
            w = tab >= 0 ? 0 : w;
         }

         // if header text contains {N}, the total page number is
         // not known know, so we pass the TextLens to the paintable,
         // in this case the text is restricted to one line
         final boolean oneline = (lens instanceof HeaderTextLens) &&
            (lens.getText().toUpperCase().indexOf("{N}") >= 0 ||
            lens.getText().toUpperCase().indexOf("{P}") >= 0 ||
            lens.getText().toUpperCase().indexOf("{N,") >= 0 ||
            lens.getText().toUpperCase().indexOf("{P,") >= 0);
         float strW = Common.stringWidth(ln, font, fm);

         // special handing for heading page number in toc
         // the real contents is not known until later
         if(oneline) {
            // the paintable get the string from the lens at print time
            paintable = new TextPaintable(lens,
               report.printBox.x + report.printHead.x,
               report.printBox.y + report.printHead.y, this, inSection);
            // set the alignment so when real page number is displayed,
            // the text is properly aligned
            paintable.setAlignment(getAlignment());
            paintable.setNewLine(newLine);
            // we can break out of here since if the lens paints
            // one more than one line, it does not work correctly
            pg.addPaintable(paintable);

            ptext = null;
            report.printHead.x += Common.stringWidth(ln, font, fm) +
               getTextAdvance();
            report.printHead.y += adv;
            return false;
         }
         // @by larryl, ignore justify if alignment is center or right
         else if(isJustify() && (getAlignment() & StyleConstants.H_LEFT) != 0 &&
                 // don't justify the last line of the paragraph
                 (line.length() > 0 || ptext != null && ptext.length() > 0))
         {
            // add this line to the style page, justify
            paintable = new TextPaintable(ln,
                                          report.printBox.x + report.printHead.x,
                                          report.printBox.y + report.printHead.y,
                                          w, this, inSection);
            paintable.setNewLine(newLine);

            if(firstprint) {
               firstprint = false;
            }
            else {
               paintable.setFirstPaintable(firstprint);
            }
         }
         else {
            // add this line to the style page
            paintable = new TextPaintable(ln,
                                          report.printBox.x + report.printHead.x,
                                          report.printBox.y + report.printHead.y,
                                          this, inSection);
            paintable.setNewLine(newLine);

            if(firstprint) {
               firstprint = false;
            }
            else {
               paintable.setFirstPaintable(firstprint);
            }
         }

         paintable.setWidth((int) Math.ceil(strW));
         paintable.setHeight(fontH);
         pg.addPaintable(paintable);

         // advance head, if the line is full, move it to far right
         if(line.length() > 0) {
            report.printHead.x = report.printBox.width;
         }
         else {
            report.printHead.x = (report.printHead.x + strW + getTextAdvance());
         }

         // don't advance line if tab separated text
         if((sep == '\n' || report.printHead.x >= report.printBox.width) &&
            (ptext != null && ptext.length() > 0 || line.length() > 0))
         {
            if(!report.horFlow) {
               // if more lines,adjust the alignment of the previous line
               // the last partial line is adjusted as part of the main
               // flow
               if((getAlignment() & StyleConstants.H_CENTER) != 0) {
                  Rectangle box = paintable.getBounds();
                  float x = report.printBox.x +
                     (report.printBox.width - box.width) / 2;

                  x = Math.max(x, box.x);
                  paintable.setLocation(new Point((int) x, box.y));
               }
               else if((getAlignment() & StyleConstants.H_RIGHT) != 0) {
                  Rectangle box = paintable.getBounds();
                  float x = report.printBox.x +
                     (report.printBox.width - box.width);

                  x = Math.max(x, box.x);
                  paintable.setLocation(new Point((int) x, box.y));
               }
            }

            // check for end of page
            if(report.printHead.y + 2 * adv > report.printBox.height) {
               // widow/orphan
               if(checkOrphan && !nonflow) {
                  // index of the first paintable for this element
                  int opcnt = pg.getPaintableCount() - 1;
                  boolean lasttab = true;
                  boolean stop = false;
                  int lastcnt = 0;
                  //the first paintable count for each line
                  int[] plines = new int[1];

                  for(; opcnt >= 0 &&
                         pg.getPaintable(opcnt).getElement() == this;
                      opcnt--)
                  {
                     boolean istab =
                        pg.getPaintable(opcnt) instanceof TabPaintable;

                     if(!istab && !lasttab) {
                        plines[plines.length -1] = lastcnt;

                        int[] nplines = new int[plines.length + 1];
                        System.arraycopy(plines, 0, nplines, 0, plines.length);
                        plines = nplines;
                     }

                     lastcnt = opcnt;
                     lasttab = istab;
                  }

                  plines[plines.length - 1] = lastcnt;
                  opcnt++;

                  String remaining = (ptext == null) ? line : ptext + line;

                  // widow
                  if(plines.length == 1) {
                     for(int i = pg.getPaintableCount() - 1; i >= opcnt; i--) {
                        // removed the widow line
                        pg.removePaintable(i);
                     }

                     ptext = getDisplayText();
                     line = "";
                  }
                  // orphan
                  // @by mikec, if the text contains newline, it MUST have
                  // more than two lines, should not be orphan.
                  else if(Common.stringWidth(remaining, font, fm) <=
                          report.printBox.width &&
                          remaining.indexOf('\n') < 0)
                  {
                     // if more than two lines on this page, move one
                     // line to the next page
                     if(plines.length > 2) {
                        for(int i = pg.getPaintableCount() - 1;
                            i >= plines[0]; i--)
                        {
                           Paintable pt = pg.getPaintable(i);

                           if(pt instanceof TextPaintable) {
                              line = ((TextPaintable) pt).getText() + line;
                           }
                           else if(pt instanceof TabPaintable) {
                              line = "\t" + line;
                           }

                           // removed the widow line
                           pg.removePaintable(i);
                        }
                     }
                     // else move the entire paragraph to next page
                     else {
                        for(int i = pg.getPaintableCount() - 1; i >= opcnt;
                            i--) {
                           pg.removePaintable(i);
                        }

                        ptext = getDisplayText();
                        line = "";
                     }
                  }
               }

               ptext = (ptext == null) ? line :
                  ((line == null || line.length() == 0) ? ptext :
                   line + sep + ptext);

               // if in a repeat non-flow area or band, don't wrap last line
               // this is only done for the case if line wrapped
               if(nonflow && !newline && ptext.length() > 0) {
                  String str = paintable.getText() + " " + ptext;

                  // @by joec 2004-4-6, last parameter in call to breakLine(...)
                  // must be true to calculate the index for line to break at a
                  // white space and not simply at the last character that fits
                  end = Util.breakLine(str, w, font, true);

                  // find new line
                  if(end < 0) {
                     end = str.indexOf('\n');
                  }

                  paintable.setText((end >= 0) ? str.substring(0, end) : str);
               }

               report.printHead.y += 2 * adv;
               return true;
            }
            else if(!report.horFlow) {
               // indent to the last X position if the text is within
               // the line height (wrap around image)
               if(getHindent() > 0) {
                  report.printHead.x = lastX;
               }
               else {
                  report.printHead.x = leftX;
               }
            }
         }

         // if we are printing tab separated text segments, advance the
         // cursor (this is done only if text is left aligned, otherwise
         // the width taken by this tab is larger than the reserved space)
         // this is really a hack to fix the problem that getTextSize()
         // does not return the correct size for text with tab. however,
         // since the size of the tab depends on the position of the
         // text, getTextSize() can not calculate the accurate size
         // without the position information
         if(sep == '\t') {
            if((getAlignment() & StyleConstants.H_LEFT) != 0) {
               float y = report.printHead.y;

               // check if x reached right side of page
               if(report.printHead.x >= report.printBox.width &&
                  ptext.length() > 0)
               {
                  float adv0 = Math.max(adv, minAdv);

                  minAdv = 0;

                  report.printHead.y = y + adv0;

                  if(getHindent() > 0) {
                     report.printHead.x = lastX;
                  }
                  else {
                     report.printHead.x = leftX;
                  }

                  report.advanceLine = Math.max(report.advanceLine, adv);
                  return true;
               }
               // undo y advance
               else {
                  report.printHead.y = y;
               }
            }
            else {
               report.printHead.x += fm.charWidth('\t') - 1;
            }
         }
         else if(sep == '\n') {
            report.printHead.y += adv;

            ptext = (ptext == null) ? line :
               ((line == null || line.length() == 0) ? ptext :
                line + '\n' + ptext);

            // @by stephenwebster, Fix bug1397571511773
            // Do not reset the x position unless the elements direct
            // parent is a container.
            return ptext != null && ptext.length() > 0;
         }
      }

      report.printHead.y += adv;

      return false;
   }

   /**
    * Restart from beginning of line.
    */
   @Override
   public void resetPrint() {
      super.resetPrint();
      ptext = null;
      lensText = null;
      newLine = false;
   }

   /**
    * Ignore the remaining print task if any.
    */
   @Override
   public void reset() {
      super.reset();
      inited_ = false;
   }

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   @Override
   public boolean isJustify() {
      return ((TextElementInfo) einfo).isJustify();
   }

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   @Override
   public void setJustify(boolean justify) {
      ((TextElementInfo) einfo).setJustify(justify);
   }

   /**
    * Get the advance amount after a text element.
    */
   @Override
   public int getTextAdvance() {
      return ((TextElementInfo) einfo).getTextAdvance();
   }

   /**
    * Set the advance amount after a text element.
    */
   @Override
   public void setTextAdvance(int textadv) {
      ((TextElementInfo) einfo).setTextAdvance(textadv);
   }

   /**
    * Get widow/orphan control.
    */
   @Override
   public boolean isOrphanControl() {
      return ((TextElementInfo) einfo).isOrphanControl();
   }

   /**
    * Set the widow/orphan control.
    */
   @Override
   public void setOrphanControl(boolean orphan) {
      ((TextElementInfo) einfo).setOrphanControl(orphan);
   }

   /**
    * Set the highlight group setting.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return ((TextElementInfo) einfo).getHighlightGroup();
   }

   /**
    * Get the highlight group setting.
    */
   @Override
   public void setHighlightGroup(HighlightGroup group) {
      ((TextElementInfo) einfo).setHighlightGroup(group);
      resetHighlight();
   }

   /**
    * Get the font. Returned value includes highlight effect.
    */
   @Override
   public Font getFont() {
      // @by larryl, optimization
      if(getHighlightGroup() != null) {
         TextHighlight attr = getHighlight();

         if(attr != null && attr.getFont() != null) {
            return attr.getFont();
         }
      }

      return super.getFont();
   }

   /**
    * Get the background. Returned value includes highlight effect.
    */
   @Override
   public Color getBackground() {
      // @by larryl, optimization
      if(getHighlightGroup() != null) {
         TextHighlight attr = getHighlight();

         if(attr != null && attr.getBackground() != null) {
            return attr.getBackground();
         }
      }

      return super.getBackground();
   }

   /**
    * Get the foreground. Returned value includes highlight effect.
    */
   @Override
   public Color getForeground() {
      // @by larryl, optimization
      if(getHighlightGroup() != null) {
         TextHighlight attr = getHighlight();

         if(attr != null && attr.getForeground() != null) {
            return attr.getForeground();
         }
      }

      return super.getForeground();
   }

   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "Text";
   }

   @Override
   public Object clone() {
      return clone(getText());
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
         getReport().setValue(this, format(this, value, locale, format));
      }
      catch(Exception ex) {
         LOG.warn("Failed to set data to \"" + val +
            "\" using format " + format, ex);
      }

      resetHighlight();
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
    * Get the hyper link on this element.
    */
   @Override
   public Hyperlink getHyperlink() {
      return link;
   }

   /**
    * Set the hyper link of this element.
    */
   @Override
   public void setHyperlink(Hyperlink link) {
      this.link = link;
   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return filters;
   }

   /**
    * Set highlight.
    */
   @Override
   public void setHighlight(TextHighlight highlight) {
      if(highlight == null) {
         highlight = EMPTY_HIGHLIGHT;
      }

      this.highlight = highlight;
   }

   /**
    * Get highlight.
    */
   @Override
   public TextHighlight getHighlight() {
      ReportSheet report = getReport();

      if(report != null && !true) {
         return null;
      }

      if(highlight == null) {
         HighlightGroup highlightGroup = getHighlightGroup();

         if(highlightGroup != null) {
            highlight = (TextHighlight) highlightGroup.findGroup(
               value == null ? lens.getText() : value);

            // cache to avoid multiple evaluation
            if(highlight == null) {
               highlight = EMPTY_HIGHLIGHT;
            }
         }
      }

      return highlight;
   }

   /**
    * Reset highlight.
    */
   @Override
   public void resetHighlight() {
      highlight = null;
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

    @Override
   public XTableNode getTableNode() {
      return tableNode;
   }

   @Override
   public void setTableNode(XTableNode tableNode) {
      this.tableNode = tableNode;
   }

   static final int MAX_LINE = 400; // max char on one line, optimization
   static final TextHighlight EMPTY_HIGHLIGHT = new TextHighlight();

   private BindingAttr filters;
   private transient TextHighlight highlight;
   private Object value = null; // associated with lens when binding
   private boolean cache = true; // true to cache
   private TextLens lens;
   private String textID; // i18n
   private Hyperlink link; // hyperlink
   private boolean breakable = false;
   private transient String ptext; // used during processing
   private transient String lensText; // cache
   private transient Size psize = null; // cached psize
   private transient float lastX = 0; // starting location of first line
   private transient Hyperlink.Ref[] dlinks = new Hyperlink.Ref[0];
   // optimization
   private transient boolean inited_ = false; // inited flag
   private transient boolean canGrow = false; // auto-size
   private transient PresenterRef presenter_; // presenter for text in section
   private transient int elementIdx; // element index inside a section
   private transient boolean firstprint = true;
   private transient boolean newLine = false;
   private transient XTableNode tableNode;

   private static final Logger LOG =
      LoggerFactory.getLogger(TextElementDef.class);
}
