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
package inetsoft.report.internal;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.data.DataSet;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.internal.table.CachedTableLens;
import inetsoft.report.lens.*;
import inetsoft.report.painter.*;
import inetsoft.report.script.viewsheet.PViewsheetScriptable;
import inetsoft.report.style.TableStyle;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.security.Principal;
import java.text.Format;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;

import static inetsoft.report.ReportSheet.*;

/**
 * StyleCore is the base class of ReportSheet. It holds all inner class
 * and data members definitions.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class StyleCore extends AbstractAssetEngine
   implements StyleConstants, Cloneable
{
   /**
    * Constructor.
    */
   public StyleCore() {
      this.scopes = new int[] {AssetRepository.REPORT_SCOPE};
      this.istore = new XMLIndexedStorage("reportAssetRepository");

      refreshFormats();
   }

   /**
    * Refresh formats.
    */
   public void refreshFormats() {
      String prop = SreeEnv.getProperty("format.date", "");

      if(!prop.equals("")) {
         Format fmt = Tool.createDateFormat(prop, locale);
         this.formatmap.put(java.sql.Date.class, fmt);
      }

      prop = SreeEnv.getProperty("format.date.time", "");

      if(!prop.equals("")) {
         Format fmt = Tool.createDateFormat(prop, locale);
         this.formatmap.put(Date.class, fmt);
         this.formatmap.put(java.sql.Timestamp.class, fmt);
      }

      prop = SreeEnv.getProperty("format.time", "");

      if(!prop.equals("")) {
         Format fmt = Tool.createDateFormat(prop, locale);
         this.formatmap.put(java.sql.Time.class, fmt);
      }
   }

   /**
    * Set the total page in previous style sheet. Allows continuous page
    * number across multiple style sheet.
    * @param total the total page accumulated in previous style sheets.
    */
   public void setPageTotalStart(int total) {
      pgTotal = total;
   }

   /**
    * Get the page total starting number.
    */
   public int getPageTotalStart() {
      return pgTotal;
   }

   /**
    * Set the background image location. For templates only.
    */
   public void setBackgroundImageLocation(ImageLocation iloc) {
      bgimage = iloc;

      if(iloc != null) {
         try {
            bg = iloc.getImage();
         }
         catch(Exception e) {
            LOG.error("Failed to load image: " + iloc.getPath(), e);
         }
      }
   }

   /**
    * Get the background image location.
    */
   public ImageLocation getBackgroundImageLocation() {
      return bgimage;
   }

   /**
    * Get the next ID for a type of element.
    * @param type element type, can be any string.
    */
   public String getNextID(String type) {
      Integer cnt = idmap.get(type);

      if(cnt == null) {
         cnt = 1;
      }

      idmap.put(type, cnt + 1);
      return type + cnt;
   }

   /**
    * Set the hanging indent.
    */
   public void setHindent(int ind) {
      hindent = ind;
   }

   /**
    * Print the next page area. The area is pointed by printBox.
    * The printHead pointer is NOT reset in this method. It should point to
    * the point where the printing should start.
    * @param pg page to print.
    * @param flow true to flow the next page if full.
    * @return true if more to print.
    */
   protected abstract boolean printNextArea(StylePage pg, Vector elements,
                                            boolean flow);

   /**
    * Get the top most report if this is a bean or subreport. Return self
    * if this is not a bean or subreport.
    */
   public ReportSheet getTopReport() {
      return (ReportSheet) this;
   }

   /**
    * Get a ReportElement component's width.
    * @param elem the element of the report.
    */
   protected float getElementWidth(ReportElement elem) {
      if(elem instanceof TableElementDef) {
         TableElementDef tableElem = (TableElementDef) elem;
         return getTableElementWidth(tableElem);
      }

      return 0;
   }

   /**
    * Get a TableElementDef component's width.
    * @param elem the table element of the report.
    */
   private float getTableElementWidth(TableElementDef elem) {
      float colWidthSum = 0;

      // if the TableElementDef component's layout is TABLE_FIT_PAGE,
      // calculate the sum of the columns's width as
      // the TableElementDef component's width.
      if(elem.getLayout() == ReportSheet.TABLE_FIT_PAGE) {
         // get the TableElementDef's print table, and calculate the width
         TableLens lens = elem.getPrintTable();
         float[][] colWidths = elem.calcColWidth(0, lens);

         // get the sum of the columns widths
         for(float[] colWidth : colWidths) {
            colWidthSum += colWidth[0];
         }
      }

      return colWidthSum;
   }

   // internal functions
   // functions used in this class

   /**
    * Advance the cursor. If y is advanced, then the x is set to the left
    * edge of the page.
    * @param x horizontal advance.
    * @param y vertical advance.
    */
   protected void advance(float x, float y) {
      printHead.y += y;

      // when y advances, move the print head to the left
      if(x == 0 && y != 0) {
         printHead.x = 0;
      }
      else {
         printHead.x += x;
      }
   }

   /**
    * Get the text size. This handles the newline in the string.
    * @param str string content.
    * @param font font for printing.
    * @return the size of the text.
    */
   public static Size getTextSize(String str, Font font, int spacing) {
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      int idx = 0, odx = 0;
      Size d = new Size(0, 0);
      float fontH = Common.getHeight(font);

      while(idx >= 0) {
         idx = str.indexOf("\n", odx);

         String line = (idx >= 0) ?
            str.substring(odx, idx) :
            str.substring(odx);
         // line width
         float sw = Common.stringWidth(line, font, fm);
         d.width = Math.max(d.width, sw);
         d.height += fontH + ((idx >= 0) ? spacing : 0);

         odx = idx + 1;
      }

      d.width = (float) Math.ceil(d.width);

      return d;
   }

   /**
    * Calculate the preferred and minimum width of a text. The minimum
    * width is the width of the largest word in the text.
    * @param str string content.
    * @param font font for printing.
    * @param wrap true if allows wrapping. If false, min == pref.
    * @param eqw width of a column if the table is divided equally
    * @return {preferredwidth, minimumwidth}
    */
   static float[] getPrefMinWidth(String str, Font font, boolean wrap, float eqw) {
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      float xW = fm.charWidth('X');
      int idx = 0, odx = 0;
      float[] prefmin = new float[2];

      while(idx >= 0) {
         idx = str.indexOf("\n", odx);

         String line = (idx >= 0) ?
            str.substring(odx, idx) :
            str.substring(odx);
         // line width
         float sw = Common.stringWidth(line, font, fm);

         prefmin[0] = Math.max(prefmin[0], sw);

         // if wrap is true, break line into words
         if(wrap && line.length() > 0) {
            // optimized to reduce call to stringWidth
            // longest is an approximation because the word with most char
            // may not be the widest with variable width font
            double longest = 0;

            for(int owi = 0, wi = line.indexOf(' '); owi >= 0;
                owi = wi, wi = (wi > 0) ? line.indexOf(' ', wi + 1) : -1) {
               int len = (wi > 0) ? (wi - owi) : (line.length() - owi);

               if(len > longest) {
                  longest = len;
               }
            }

            // add 20% to longest to account for the optimization lose
            longest = Math.min(line.length(), longest * 1.2);

            prefmin[1] = Math.max(prefmin[1],
                                  (int) (sw * longest / line.length()));
            // @by larryl, make sure the min is >= at least one char
            prefmin[1] = Math.max(prefmin[1], xW);
            // the minimum size is not greater than eqw if the column
            // can wrap
            prefmin[1] = Math.min(prefmin[1], eqw);
            // make sure pref is >= min
            prefmin[0] = Math.max(prefmin[0], prefmin[1]);
         }
         else if(prefmin[1] > prefmin[0] || prefmin[1] <= 0) {
            prefmin[1] = prefmin[0];
         }

         odx = idx + 1;
      }

      return prefmin;
   }

   /**
    * Format a text string. The format string is same as supported by
    * the header/footer properties.
    * @param format format string.
    * @param pgnum page number.
    * @param pgs number of pages.
    * @param now current time.
    * @param locale the locale for the message format.
    */
   public static String format(String format, int pgnum, int pgs, Date now,
                               Locale locale) {
      return StyleCore.format(format, pgnum, pgs, now, locale, true);
   }

   /**
    * Format a text string. The format string is same as supported by
    * the header/footer properties.
    * @param format format string.
    * @param pgnum page number.
    * @param pgs number of pages.
    * @param now current time.
    * @param locale the locale for the message format.
    * @param formatExpre format the expression or not, such as {P} to {1}.
    */
   public static String format(String format, int pgnum, int pgs, Date now,
                               Locale locale, boolean formatExpre) {
      format = Tool.replaceAll(format, "'", "''");

      Object num = pgnum <= 0 ? "{P}" : ("" + pgnum);
      Object count = pgs <= 0 ? "{N}" : ("" + pgs);

      // escape regular '{' and '}'
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < format.length(); i++) {
         if(format.charAt(i) == '{') {
            int idx = format.indexOf('}', i + 1);

            if(idx > i + 1) {
               String str = format.substring(i + 1, idx);
               String lower = str.toLowerCase();

               if(lower.startsWith("p,") || lower.startsWith("n,") ||
                  lower.startsWith("d,") || lower.startsWith("t,") ||
                  lower.equals("p") || lower.equals("n") || lower.equals("d") ||
                  lower.equals("t"))
               {
                  buf.append("{").append(str).append("}");
                  i = idx;
                  continue;
               }
               else {
                  buf.append('\\');
               }
            }
            else {
               buf.append('\\');
            }
         }
         else if(format.charAt(i) == '}') {
            buf.append('\\');
         }
         else if(format.charAt(i) == '\\') {
            buf.append('\\');
            i++;
         }

         buf.append(format.charAt(i));
      }

      boolean lastesc = false; // last char escaped

      format = buf.toString();

      for(int i = 0; i < format.length(); i++) {
         if(format.charAt(i) == '\\') {
            format = format.substring(0, i) + format.substring(i + 1);

            // escape {
            char ch = format.charAt(i);

            if(ch == '{' || ch == '}') {
               if(lastesc) {
                  // '{''{' to '{{'
                  format = format.substring(0, i - 1) + ch + "'" +
                           format.substring(i + 1);
               }
               else {
                  format = format.substring(0, i) + "'" + ch + "'" +
                           format.substring(i + 1);
                  i += 2;
               }

               lastesc = true;
               continue;
            }
         }
         else if(format.charAt(i) == '{' && formatExpre) {
            i++;
            String s = null;

            switch(format.charAt(i)) {
            case 'P':
            case 'p':
               s = "0";
               break;
            case 'N':
            case 'n':
               s = "1";
               break;
            case 'D':
            case 'd':
               s = "2,date";
               break;
            case 'T':
            case 't':
               s = "2,time";
               break;
            }

            if(s != null) {
               format = format.substring(0, i) + s + format.substring(i + 1);
               i += s.length() - 1;
            }
         }

         lastesc = false;
      }

      // fix bug1256874051765, here if formatExpre is false,
      // then we shouldn't format the string here because the
      // string may contains expression such as {p}, such expression
      // can not be format here,we will use it when export to excel
      if(formatExpre) {
         try {
            MessageFormat mf = locale == null ?
             new MessageFormat(format) :
               new MessageFormat(format, locale);
            return mf.format(new Object[] {num, count, now});
         }
         catch(Exception ex) {
            LOG.error("Failed to format header/footer text: " + format, ex);
         }
      }

      return format;
   }

   /**
    * Round up the value to the multiple of mul.
    */
   static float roundup(float v, float mul) {
      return (float) Math.ceil(v / mul) * mul;
   }

   /**
    * Get the presenter for a type.
    */
   public static Presenter getPresenter(Hashtable presenters, Class type) {
      Presenter presenter = null;

      for(; type != null &&
             (presenter = (Presenter) presenters.get(type)) == null;
          type = type.getSuperclass()) {
      }

      return presenter;
   }

   /**
    * Get the format for a type.
    */
   public static Format getFormat(Map<Class<?>, Format> formats, Class<?> type) {
      if(formats.size() <= 0) {
         return null;
      }

      Format format = null;

      for(; type != null && (format = formats.get(type)) == null &&
         !type.equals(java.sql.Time.class); type = type.getSuperclass())
      {
      }

      return format;
   }

   /**
    * Get the format for a type in report and parent reports.
    */
   public Format getFormat(Class<?> type) {
       return getFormat(formatmap, type);
   }

   /**
    * Create a presenter map by merging the current setting, the enclosing
    * report's setting in case of subreport, and passed in mapping.
    */
   public Hashtable createPresenterMap(Hashtable map) {
      return Tool.mergeMap(presentermap, map);
   }

   /**
    * Create a format map by merging the current setting, the enclosing
    * report's setting in case of subreport, and passed in mapping.
    */
   public Hashtable createFormatMap(Hashtable map) {
      return Tool.mergeMap(formatmap, map);
   }

   /**
    * Get the formats for this template.
    */
   public Map<Class<?>, Format> getFormats() {
      return formatmap;
   }

   /**
    * Get the properties of this template.
    */
   public Properties getProperties() {
      return prop;
   }

   /**
    * Get all elements in the report, including body,
    * and non-flow area elements.
    * @return vector of all elements.
    */
   public abstract Vector getAllElements();

   /**
    * Get all header elements in the report, including headers, element
    * associated headers.
    * @return vector of vectors or FixedContainers
    */
   public Vector getAllHeaderElements() {
      Vector vec = new Vector();

      append(vec, headerElements);
      append(vec, firstHeader);
      append(vec, evenHeader);
      append(vec, oddHeader);

      return vec;
   }

   /**
    * Get all footer elements in the report, including footers, element
    * associated footers.
    * @return vector of vectors or FixedContainers
    */
   public Vector getAllFooterElements() {
      Vector vec = new Vector();

      append(vec, footerElements);
      append(vec, firstFooter);
      append(vec, evenFooter);
      append(vec, oddFooter);

      return vec;
   }

   public boolean isHeaderFooterElement(BaseElement elem) {
      HashSet<Object> headerElems = new HashSet<Object>(getAllHeaderElements())
      {
         @Override
         public boolean contains(Object obj) {
            if(obj instanceof BaseElement) {
               for(Object o : this) {
                  if(((BaseElement) obj).getFullName().equals(((BaseElement) o).getFullName())) {
                     return true;
                  }
               }
            }

            return false;
         }
      };

      HashSet<Object> footerElems = new HashSet<Object>(getAllFooterElements())
      {
         @Override
         public boolean contains(Object obj) {
            if(obj instanceof BaseElement) {
               for(Object o : this) {
                  if(((BaseElement) obj).getFullName().equals(((BaseElement) o).getFullName())) {
                     return true;
                  }
               }
            }

            return false;
         }
      };

      return headerElems.contains(elem) || footerElems.contains(elem);
   }

   /**
    * Calculate the grid that is not occupied by the sections.
    */
   protected Rectangle[][] calcGrid(float x, float y, float w, float h,
                                    Vector secs) {
      List<Integer> xv = new ArrayList<>();
      List<Integer> yv = new ArrayList<>();

      xv.add((int) x);
      yv.add((int) y);

      // calculate the location of boundaries
      for(int i = 0; i < secs.size(); i++) {
         Rectangle bounds = (Rectangle) secs.elementAt(i);
         int[] xs = { bounds.x, bounds.x + bounds.width };
         int[] ys = { bounds.y, bounds.y + bounds.height };

         for(int xVal : xs) {
            // keep sorted order
            Tool.insertSorted(xVal, xv);
         }

         for(int yVal : ys) {
            // keep sorted order
            Tool.insertSorted(yVal, yv);
         }
      }

      Tool.insertSorted((int) (x + w), xv);
      Tool.insertSorted((int) (y + h), yv);

      // hit matrix
      boolean[][] matrix = new boolean[yv.size() - 1][xv.size() - 1];

      // calculate hit matrix
      for(int i = 0; i < secs.size(); i++) {
         Rectangle bounds = (Rectangle) secs.elementAt(i);
         int row = yv.indexOf(bounds.y);
         int col = xv.indexOf(bounds.x);
         int row2 = yv.subList(row, yv.size()).indexOf(bounds.y + bounds.height) + row;
         int col2 = xv.subList(col, xv.size()).indexOf(bounds.x + bounds.width) + col;

         for(int r = row; r < row2; r++) {
            for(int c = col; c < col2; c++) {
               matrix[r][c] = true;
            }
         }
      }

      // calculate the grid, cells are merged horizontally only
      Rectangle[][] grid = new Rectangle[yv.size() - 1][];

      for(int i = 0; i < yv.size() - 1; i++) {
         int y0 = yv.get(i);
         int y1 = yv.get(i + 1);
         Rectangle area = null;
         List<Rectangle> row = new ArrayList<>();

         for(int j = 0; j < xv.size() - 1; j++) {
            // hit a block
            if(matrix[i][j]) {
               if(area != null) {
                  row.add(area);
                  area = null;
               }

               continue;
            }

            int x0 = xv.get(j);
            int x1 = xv.get(j + 1);

            // new area
            if(area == null) {
               area = new Rectangle(x0, y0, x1 - x0, y1 - y0);
            }
            // extend width
            else {
               area.width = x1 - area.x;
            }
         }

         if(area != null) {
            row.add(area);
         }

         grid[i] = row.toArray(new Rectangle[0]);
      }

      return grid;
   }

   // handles vertical alignment of line elements
   // handles centering and right align of line elements
   // (for wrapped text paintables)
   // centering is only done if all elements on the line are centered
   protected void alignLine(int scnt, int ecnt, StylePage pg, float lineY,
                            float lineH) {
      // variables for baseline information
      float baseline = 0;
      float tallest = 0;
      int baseline_align = 0;
      boolean allcenter = true, allright = true;
      float leftx = printBox.x + printBox.width, rightx = 0;

      ecnt = Math.min(pg.getPaintableCount(), ecnt);

      // adjust the line elements for vertical alignment
      // this can only be done after the entire line is printed
      // we do this by adjusting the location of the paintable
      for(int i = scnt; i < ecnt; i++) {
         Paintable pt = pg.getPaintable(i);
         Rectangle rect = pt.getBounds();
         BaseElement elem = (BaseElement) pt.getElement();
         int align = elem.getAlignment();

         if((align & H_CENTER) == 0) {
            allcenter = false;
         }

         if((align & H_RIGHT) == 0) {
            allright = false;
         }

         if(allcenter || allright) {
            leftx = Math.min(leftx, rect.x);
            rightx = Math.max(rightx, rect.x + rect.width);
         }

         if((align & V_CENTER) != 0) {
            int y = lineH - rect.height < 0 ? (int) lineY :
               (int) (lineY + (lineH - rect.height) / 2);
            pt.setLocation(new Point(rect.x, y));
         }
         else if((align & V_BOTTOM) != 0) {
            int y = lineH - rect.height < 0 ? (int) lineY :
               (int) (lineY + lineH - rect.height);
            pt.setLocation(new Point(rect.x, y));
         }

         if(pt instanceof TextPaintable) {
            if((elem.getAlignment() & V_TOP) == 0 ||
               (elem.getAlignment() & V_BASELINE) != 0)
            {
               baseline_align++;
            }

            if(rect.height > tallest) {
               tallest = rect.height;
               baseline = pt.getBounds().y + Common.getAscent(elem.getFont());
            }
         }
      }

      // baseline alignment, only make sense if more than
      // 1 text element
      if(baseline_align > 1) {
         for(int i = scnt; i < ecnt; i++) {
            Paintable pt = pg.getPaintable(i);

            // baseline alignment only applied to text elements
            if(pt instanceof TextPaintable) {
               BaseElement elem = (BaseElement) pt.getElement();
               int align = elem.getAlignment();
               Rectangle rect = pt.getBounds();

               if((align & V_BASELINE) != 0) {
                  int y = (int) (baseline - Common.getAscent(elem.getFont()));
                  pt.setLocation(new Point(rect.x, y));
               }
            }
         }
      }

      // centering, this is only necessary if a text wraps into multiple lines
      if(allcenter || allright) {
         int adj = (int) ((printBox.width - (rightx - leftx)) /
            (allcenter ? 2 : 1) + printBox.x - leftx);

         if(adj != 0) {
            for(int i = scnt; i < ecnt; i++) {
               Paintable pt = pg.getPaintable(i);
               Rectangle rect = pt.getBounds();

               pt.setLocation(new Point(rect.x + adj, rect.y));
            }
         }
      }
   }

   /**
    * Process the header/footer elements formatting.
    *
    * @param elements header/footer elements.
    */
   protected void processHF(Enumeration elements) {
      processHF(elements, true);
   }

   /**
    * Process header/footer elements with option to iterate through the
    * sub elements in a section or bean.
    */
   private void processHF(Enumeration elements, boolean iter) {
      while(elements.hasMoreElements()) {
         BaseElement v = (BaseElement) elements.nextElement();

         v.reset();
         processHF(v);

         if(iter) {
            Enumeration subelems = ElementIterator.elements(v);

            if(subelems.hasMoreElements()) {
               processHF(subelems, false);
            }
         }
      }
   }

   /**
    * Process the header text element tags.
    */
   protected void processHF(ReportElement v) {
      if(v != null) {
         if(v instanceof TextBased) {
            TextBased tbased = (TextBased) v;
            String text = (tbased.getText() != null) ? tbased.getText() : "";

            if(text.indexOf('{') >= 0 && text.indexOf('}') > 0) {
               // format header/footer
               if(v instanceof TextElement) {
                  TextElementDef tv = (TextElementDef) v;

                  tv.setTextLens(hfFmt.create(tv.getTextLens()));
               }
               else if(v instanceof TextBoxElement) {
                  TextBoxElementDef tb = (TextBoxElementDef) v;
                  TextLens lens = hfFmt.create(tb.getTextLens());

                  ((TextBoxElement) v).setTextLens(lens);
               }
            }
         }
         else if(v instanceof TableElementDef &&
		 ((TableElementDef) v).isCalc())
         {
            // @by mikec, if this is a plain table ,
            // it may have script that needs to be executed per repeat area.
            // We clone it so the tables would all be showing the correct data.

            TableLens tlens = ((Groupable) v).getBaseTable();

            if(tlens instanceof CalcTableLens) {
               tlens = (TableLens) ((CalcTableLens) tlens).clone();
               ((Groupable) v).setTable(tlens);
            }
         }
      }
   }

   // flags for printFixedContainer return values
   /**
    * If there are elements that have not finished printing on the current
    * page and need to be continued on the next page.
    */
   public static final int MORE_FLOW = 2;
   /**
    * If there are more elements to be printed.
    */
   public static final int MORE_ELEM = 1;
   /**
    * Entired band printed.
    */
   public static final int COMPLETED = 0;

   /**
    * See printFixedContainer
    */
   public int printFixedContainer(StylePage pg, FixedContainer container,
                                  Rectangle areabox, boolean more,
                                  float startH, float headerH, float bandH) {
      return printFixedContainer(pg, container, areabox, more, startH,
                          headerH, bandH, false);
   }

   /**
    * Print the elements in a fixed container.
    * @return true if the fixed container needs to be extended to the
    * next page. This can only happen if the container (band) contains
    * a subreport. In this case the band is always extendable until
    * the entire subreport is finished.
    * @param areabox the area to print the container. The actual area may
    * be extended if any element in the container can grow.
    * @param more true if this is a continuation of printing.
    * @param startH the elements above startH have already been processed.
    * Only print the elements below the startH in the container.
    * @param headerH the header band height for sections.
    * @param bandH the height of the original band, not limited by page boundary
    * @param atTop indicates that printing is starting from the top
    */
   public int printFixedContainer(StylePage pg, FixedContainer container,
                                  Rectangle areabox, boolean more,
                                  float startH, float headerH, float bandH, boolean atTop) {
      int right = areabox.x + areabox.width; // right edge of print area
      int bottom = areabox.y + areabox.height; // bottom of print area

      // save context
      float obandH = bandH;
      Rectangle oprintBox = printBox;
      Rectangle[] oframes = frames;
      Rectangle[] onpframes = npframes;
      Position oprintHead = printHead;
      int ocurrFrame = currFrame;
      int opcnt = pg.getPaintableCount();

      int result = COMPLETED; // return value
      // the clipping in the context of continued band, used by shape
      Rectangle vclip = new Rectangle(0, (int) startH, areabox.width,
                                      areabox.height);
      boolean isSection = container instanceof SectionBand;
      boolean forceBreakable = isSection && !((SectionBand) container).isBreakable() &&
         atTop && !SectionElementDef.isFixedSize((SectionBand) container);
      boolean breakable = !isSection || ((SectionBand) container).isBreakable();
      int cnt = container.getShapeCount();

      // print shapes
      for(int i = 0; i < cnt; i++) {
         PageLayout.Shape shape = container.getShape(i);
         Rectangle box = shape.getBounds();

         // if in the printable area
         if(box.y + box.height < startH || box.y > startH + areabox.height) {
            continue;
         }

         ShapePaintable pt = new ShapePaintable(shape);

         pt.setContainer(container);
         pt.setPrintableBounds(new Rectangle(areabox));
         pt.setVirtualClip(vclip);
         pg.addPaintable(pt);
      }

      int elemCnt = container.getElementCount(); // optimization

      // reset elements if necessary
      for(int k = 0; k < elemCnt; k++) {
         BaseElement elem = (BaseElement) container.getElement(k);

         // if continuation, we need to skip the
         // elements that are in the middle of processing
         if(more) {
            // @by larryl, if is processing a section, and the element is not
            // processed yet, we reset it to make sure it's printed properly.
            // for page area there is not continuation and the more flag
            // is true for no-repeating elements
            if(isSection) {
               Rectangle bounds = container.getPrintBounds(k);

               if(bounds.y >= startH) {
                  elem.resetPrint();
               }
            }
         }
         else {
            // @by mikec, if this is a plain table ,
            // it may have script that needs to be executed per repeat area.
            // We clone it so the tables would all be showing the correct data.
            if(elem instanceof Groupable) {
               TableLens tlens = ((Groupable) elem).getBaseTable();

               if(tlens instanceof CalcTableLens) {
                  tlens = (TableLens) ((CalcTableLens) tlens).clone();
                  ((Groupable) elem).setTable(tlens);
               }
            }

            elem.resetPrint();
         }

         // @by mikec, if running total, always reset the script
         // if the element is in a keep together block but not in keep paintable
         // list because of the band not finished(be rewound), the script of
         // this element should not be executed again in this print round.
         if(!elem.sexecuted && !more) {
            elem.resetScript();
         }

         elem.sexecuted = false;
      }

      // order the elements into vertical positions so we can adjust positions
      // if elements grow
      // @by jasons also sort by horizontal positions
      int[] ordered = new int[elemCnt];

      for(int i = 0; i < ordered.length; i++) {
         Rectangle bounds = container.getPrintBounds(i);
         int idx = i - 1;

         for(; idx >= 0; idx--) {
            Rectangle bounds2 = container.getPrintBounds(ordered[idx]);

            if(bounds.y > bounds2.y ||
               bounds.y == bounds2.y && bounds.x >= bounds2.x)
            {
               break;
            }

            ordered[idx + 1] = ordered[idx];
         }

         ordered[idx + 1] = i;
      }

      // @by larryl, index of elements that need to continue on the next page
      List<Integer> moreElems = new ArrayList<>();
      // the bottom of all paintable (the startH of next call). This is used
      // to set the continued element's printBounds so it would not be
      // ignored by the check of < startH on the next page
      float moreBottom = 0;
      // the bottom of all elements that has being printed on this band in
      // the original layout (not the print bounds)
      float origBottom = 0;
      boolean hasAreaBreak = false;

      // print each element in a fixed area
      for(int idx = 0; idx < ordered.length; idx++) {
         int ei = ordered[idx];
         BaseElement elem = (BaseElement) container.getElement(ei);
         Rectangle ebounds = container.getBounds(ei);

         // skip invisible column
         // @by larryl, if the element is outside of the band in the original
         // layout, ignore it completely. Otherwise it will cause the
         // method to return MORE_ELEM.
         if(!elem.isVisible() && !false ||
            // non-section has bandH == 0
            ebounds.y >= obandH + startH && obandH > 0)
         {
            continue;
         }

         Rectangle bounds = container.getPrintBounds(ei);

         // subreport is not processed in design mode
         // both subreport and table can grow by default
         String prop = elem.getProperty(ReportElement.GROW);
         boolean cangrow = (prop != null && prop.equalsIgnoreCase("true"));
         boolean expandable = true && isSection || cangrow;

         // if this element is below all previous elements in the original
         // layout, and there are elements in the already printed element that
         // need to be continued on the next page, don't process any more
         // elements so those elements can properly continue on next page
         if(ebounds.y >= origBottom && result == MORE_FLOW) {
            break;
         }
         // check if in the printable area.
         // above printable area
         else if(bounds.y + bounds.height <= startH ||
                 // below printable area and last continued element
                 (bounds.y > startH + areabox.height &&
                 // sometimes the height is negative, this moves the y index up
                 bounds.y + bounds.height > startH + areabox.height) ||
                 // @by larry, ignore elements on right outside of printing area
                 bounds.x > areabox.width) {
            // if skipping elements below the printable area,
            // set flag to MORE_ELEMENT
            if(bounds.y > startH + areabox.height &&
               bounds.y + bounds.height > startH + areabox.height
               && result == COMPLETED)
            {
               result = Math.max(MORE_ELEM, result);
            }

            continue;
         }

         Rectangle obounds = new Rectangle(bounds);

         bounds = new Rectangle(bounds);
         bounds.y -= (int) startH;
         printHead = new Position(0, 0);
         printBox = new Rectangle(areabox.x + bounds.x, areabox.y + bounds.y,
            bounds.width, bounds.height);

         // make sure the bounds is inside the page area

         // right bottom corner
         Point rb = new Point(printBox.x + printBox.width,
                              printBox.y + printBox.height);

         printBox.x = Math.min(right, Math.max(areabox.x, printBox.x));
         printBox.y = Math.min(bottom, Math.max(areabox.y, printBox.y));

         rb.x = Math.min(right, Math.max(areabox.x, rb.x));
         rb.y = Math.min(bottom, Math.max(areabox.y, rb.y));
         printBox.width = rb.x - printBox.x;
         printBox.height = rb.y - printBox.y;

         frames = new Rectangle[] {printBox};
         npframes = null;
         currFrame = 0;
         origBottom = Math.max(ebounds.y + ebounds.height, origBottom);

         // remaining space on page
         int maxHeight = oprintBox.y + oprintBox.height - printBox.y;

         // if the element is a expandable, allow it to grow
         if(expandable) {
            printBox.height = maxHeight;

            npframes = new Rectangle[] {null};

            // if the next page frame is set in report, use the npframe in
            // report but subtract the header band height
            if(onpframes != null && onpframes.length > 0) {
               // use the x and width of the current print box as the width
               // is fixed in section and should not change on next page. The
               // x is not exactly right but it is not really used to calculate
               // anything when printing on the current page.
               npframes[0] = new Rectangle(printBox);
               npframes[0].height = onpframes[0].height - (int) headerH;
               npframes[0].y = (int) (onpframes[0].y + headerH);
            }
            // for continuation, the next page frame is the full page
            // subtract the header band height
            else {
               npframes[0] = new Rectangle(printBox);
               npframes[0].y = oprintBox.y + (int) headerH;
               npframes[0].height = oprintBox.height - (int) headerH;
            }
         }

         if(elem instanceof PainterElement) {
            PainterElementDef pe = (PainterElementDef) elem;

            // @by larryl, force the painter margin to be zero in section as
            // it does not make sense to set it in the fixed position layout
            pe.setMargin(new Insets(0, 0, 0, 0));

            // if an image can grow, set the size to it's preferred size
            if(cangrow) {
               if(!false) {
                  // if painter can grow, force it to be breakable
                  pe.setLayout(ReportSheet.PAINTER_BREAKABLE);
                  // clear fixed size
                  pe.setSize(null);
                  Size psize = pe.getPreferredSize();
                  int pwidth = (int) psize.width;

                  // for textbased element, just use the width of print bounds
                  if(pe instanceof TextBoxElementDef) {
                     Size size = new Size(obounds.width, obounds.height, 72);
                     pwidth = (int) size.width;

                     TextPainter tpainter = (TextPainter) pe.getPainter();
                     tpainter.setWidth(obounds.width);
                  }

                  printBox.width = Math.max(printBox.width, pwidth);
                  // make sure it does not extend outside of band
                  printBox.width = Math.min(printBox.width, right - printBox.x);
               }
            }
            // fit painter in the area
            else {
               pe.setSize(new Size(obounds.width, obounds.height, 72));
               pe.setAnchor(null);

               if(pe instanceof TextBoxElementDef) {
                  TextPainter tpainter = (TextPainter) pe.getPainter();
                  tpainter.setWidth(-1);
               }
            }
         }

         elem.setNonFlow(!more && !expandable);

         int pgPaintableCountPrev = pg.getPaintableCount();

         // print the element to page
         boolean rc = false;

         // if element is a table, try to print the element until there is
         // no more space to fit the next table region. before we use the
         // printNext to print so that is handled in the printNext. for
         // performance reason we are calling element.print() directly
         if(elem instanceof TableElementDef) {
            TableElementDef table = (TableElementDef) elem;

            int oH = printBox.height;

            // if table is not breakable, validate with the full size so if the region doesn't
            // fit in the page, it will advance to the next page. (54709)
            if(!table.isBreakable()) {
               printBox.height = bounds.height;
            }

            table.validate(pg, (ReportSheet) this, null);
            printBox.height = oH;

            do {
               float space = printBox.height - printHead.y;
               int fit = table.fitNext(space);

               if(fit < 0) {
                  break;
               }
               else if(fit == 0) {
                  rc = true;
                  hasAreaBreak = true;
                  break;
               }

               rc = elem.print(pg, (ReportSheet) this);
            }
            while(rc);
         }
         // regular element printing
         else {
            rc = elem.print(pg, (ReportSheet) this);
         }

         if(!rc) {
            completeElement(elem, pg);
         }

         // adjust width of text paintable so it fills up the fixed area
         int pgPaintableCount = pg.getPaintableCount();
         int ptop = printBox.y + printBox.height; // topmost paintable location
         int pbottom = printBox.y; // bottom paintable location

         for(int i = pgPaintableCountPrev; i < pgPaintableCount; i++) {
            Paintable pt = pg.getPaintable(i);
            Rectangle rec = pt.getBounds();
            BaseElement ptelem = (BaseElement) pt.getElement();

            // if is text element, stretch the paintable to fill the width
            // @by larryl, if the element is not contained directly in this
            // band, don't move it since it should be handler by it's container
            if(ptelem != null && ptelem.getParent() == container && pt instanceof TextPaintable) {
               // printBox is the area the text element on the section
               // the text paintable should fill the whole area (for bg)
               pt.setLocation(new Point(printBox.x, rec.y));
               ((TextPaintable) pt).setWidth(printBox.width);
            }

            ptop = Math.min(ptop, rec.y);

            // @by larryl, if the paintable is a grid (from subreport), it is
            // shrunk to fit the contents in section so we should not count
            // it for the bottom. Otherwise elements on the next page would be
            // pushed down too far caused by the setPrintBounds() using the
            // bottom not adjusted for grid shrinking
            if(!(pt instanceof GridPaintable)) {
               pbottom = Math.max(pbottom, rec.y + rec.height);
            }

            // @by mikec, if the element is not contained directly in this band,
            // don't set the virtual clip since it should be take by it's real
            // parent.
            if(false && (pt instanceof BasePaintable) &&
               ptelem != null &&
               (ptelem.getParent() == container))
            {
               ((BasePaintable) pt).setVirtualClip(vclip);
            }
         }

         int totalPaintableHeight = pbottom - ptop;

         // if can grow, check for overlapping
         if(expandable) {
            // bottom of paintable to the top of band
            int bottomBounds = (int) (pbottom - areabox.y + startH);
            int bottomOff = bottomBounds; // used for calculating overlapping
            int overlap = 0;
            int overlapIdx; // first overlapping element

            // if continuation, add the entire height of the printable area
            // plus the minimum height on the next page
            if(rc) {
               bottomOff += RESERVED_SPACE + 1;
            }

            // check for overlapping
            // since elements are sorted by y position, only need to look at
            // the first overlapped element
            for(overlapIdx = idx + 1; overlapIdx < ordered.length; overlapIdx++)
            {
               int next = ordered[overlapIdx];
               Rectangle bounds2 = container.getPrintBounds(next);
               Rectangle fixbounds2 = container.getBounds(next);
               Rectangle fixbounds = container.getBounds(ei);
               // if print more, keep original gap or overlap
               int over1 = bottomOff - bounds2.y;
               // @by billh, keep space between previous element and next
               // element, previous element like table might be expanded
               // when printed. The calculation must be based on the original
               // positions
               over1 += fixbounds2.y - fixbounds.y - fixbounds.height;

               // @by billh, when check overlap, to use bounds seems
               // better than to use printBounds for the printBounds
               // of an element is always varying if it crosses pages
               if(fixbounds2.y >= fixbounds.y + fixbounds.height && over1 > 0) {
                  // Rectangle bounds0 = container.getBounds(ei);
                  // if it's overlapping on the original design
                  // int over2 = bounds0.y + bounds0.height -
                  // container.getBounds(next).y;
                  //
                  // if(over2 > 0) {
                  // over1 -= over2;
                  // }

                  // if still overlap after taking into account of the original
                  // overlap, record as adjustment
                  overlap = Math.max(overlap, over1);
                  break;
               }
               // no overlapping, stop now since elements are sorted on Y
               else if(over1 <= 0) {
                  break;
               }
            }

            // if overlap, move all elements below down by the overlap amount
            if(overlap > 0) {
               areabox.height = Math.min(areabox.height + overlap,
                  oprintBox.y + oprintBox.height - areabox.y);

               // if continuation, the continued element's height is set to
               // reach the areabox bottom at the end of the method to make
               // sure they will not be skipped. When we push down the
               // overlapped elements, we need to push it below the bottom of
               // the areabox too
               if(rc && areabox.height + startH > bottomBounds) {
                  overlap += areabox.height + startH - bottomBounds;
               }

               for(int i = overlapIdx; i < ordered.length; i++) {
                  Rectangle bounds2 = container.getPrintBounds(ordered[i]);

                  bounds2.y += overlap;
                  container.setPrintBounds(ordered[i], bounds2);
               }

               bottom = areabox.y + areabox.height;
            }

            // span across pages, continue on next page
            if(rc) {
               result = MORE_FLOW;
               // remember the continued element so we can check for the
               // bounds later to ensure it will not be ignored on next page
               moreElems.add(ei);
               moreBottom = Math.max(moreBottom, bottomBounds);

               // should not break here because other elements at the same
               // y position may still fit and need to be processed
            }
            else if(hasAreaBreak) {
               // should always update the bottomBounds when others current page has the AreaBreak.
               // to make sure the area that is broken to the next page can be print
               // (box.y + box.height < startH).
               moreBottom = Math.max(moreBottom, bottomBounds);
            }
         }
         else {
            // @by larryl, painter element is always stretched to fill the
            // assigned area, so don't adjust the vertical alignment anymore.
            // Theoretically this should be a no-op for painter but the
            // actual height of the paintable may not be exactly the same
            // as the paintable area depending how painter paintable is
            // generated. So it's safer to just skip the logic altogether.
            if(!(elem instanceof PainterElement)) {
               // support vertical alignment of section elements.
               // don't do anything if flows to next page
               int distance;

               switch(elem.getAlignment() & V_ALIGN_MASK) {
               case V_CENTER:
               case V_BASELINE:
                  distance = (printBox.height - totalPaintableHeight) / 2;
                  break;
               case V_BOTTOM:
                  distance = (printBox.height - totalPaintableHeight);
                  break;
               default:
                  distance = 0;
                  break;
               }

               // vertical alignment
               if(distance != 0) {
                  for(int i = pgPaintableCountPrev; i < pgPaintableCount; i++) {
                     Paintable pt = pg.getPaintable(i);
                     Rectangle rec = pt.getBounds();

                     pt.setLocation(new Point(rec.x, rec.y + distance));
                  }
               }
            }

            // @by larryl, if the original bounds is outside of the band,
            // don't count the height that is outside
            int adj = (obounds.y < 0) ? -obounds.y : 0;

            // if more and the print bounds have more space, mark as MORE_FLOW
            if(rc && obounds.height - adj > printBox.height + startH) {
               result = MORE_FLOW;
            }
         }
      }

      // @by billh, here we guarantee that more bottom is not less than
      // actual area bottom, in some cases, if a table row is very high
      // and rewound, more bottm might be not enough to make sure that
      // table's bounds.y +  bounds.height is greater than next startH
      moreBottom = Math.max(moreBottom, startH + areabox.height);

      // if not breakable, we will print the band from start so
      // there is no continuation. in that case, when we reset() the band,
      // we will keep the element size info so the size set from
      // script will not be lost
      // @by stephenwebster, fix bug1400873156569
      // The breakable flag now takes into consideration whether or not the
      // results of a non-breakable band will be forced to break.  This
      // mirrors same condition in SectionElementDef
      // i.e. !band.isBreakable() && atTop && !isFixedSize(band)
      if(breakable || forceBreakable) {
         // for all continued elements, we make sure the bounds is below the
         // startH so it would not be skipped
         for(Integer ei : moreElems) {
            Rectangle obounds = container.getPrintBounds(ei);

            // if the bound is less than the consumed height, increase the
            // height so this element will not be skipped on next page.
            // we don't shrink the height here since that could cause the
            // element to expand and push other elements down (which was
            // side-by-side before)
            if(obounds.y + obounds.height <= moreBottom) {
               obounds.height = (int) moreBottom - obounds.y + RESERVED_SPACE;
               container.setPrintBounds(ei, obounds);
            }
         }
      }

      // set the bounding box for fixed element paintable
      if(false) {
         for(int i = opcnt; i < pg.getPaintableCount(); i++) {
            BasePaintable pt = (BasePaintable) pg.getPaintable(i);
            BaseElement elem = (BaseElement) pt.getElement();

            if(elem == null || elem.getParent() == container) {
               pt.setFrame(areabox);

               if(elem != null) {
                  elem.setFrame(areabox);
               }
            }
         }
      }

      // restore context
      printBox = oprintBox;
      printHead = oprintHead;
      frames = oframes;
      npframes = onpframes;
      currFrame = ocurrFrame;
      return result;
   }

   /**
    * Complete a report element.
    */
   public final void completeElement(ReportElement elem, StylePage page) {
      XTable table = null;

      if(elem instanceof Groupable) {
         table = ((Groupable) elem).getTable();
      }

      if(elem instanceof BindableElement) {
         recordMaxRowHint((BindableElement) elem, page);
      }

      boolean querytimeout = table != null && Util.isTimeoutTable(table);

      if(querytimeout) {
         String val = ((ReportSheet) this).getProperty("display.warning");
         int timeout = Util.getQueryTimeout(elem);

         if(!"false".equals(val)) {
            String info = Catalog.getCatalog().
               getString("designer.common.timeout", elem.getID(), timeout + "");
            assert page != null;
            page.addInfo(info);
         }
      }

      if(elem instanceof ChartElement) {
         String val = ((ReportSheet) this).getProperty("display.warning");
         final UserMessage userMessage = Tool.getUserMessage();

         if(userMessage != null) {
            String warning = userMessage.getMessage();

            if(warning != null && !"false".equals(val)) {
               warning = Tool.replaceAll(warning, "\n", ", ");
               String info = Catalog.getCatalog().
                  getString("designer.common.chartWarning", elem.getID(), warning);

               if(page != null) {
                  page.addInfo(info);
               }
            }
         }
      }
   }

   /**
    * Add max row limits hint.
    */
   private void recordMaxRowHint(BindableElement elem, StylePage page) {
      int amax = -1;
      int tmax = 0;
      TableLens table = null;

      if(elem instanceof Groupable) {
         table = ((Groupable) elem).getTable();
      }

      HashMap maxRowHint = Util.getHintBasedMaxRow(table);
      Object hintMax = maxRowHint == null ? null : maxRowHint.get(Util.HINT_MAX_ROW_KEY);
      Object baseMaxRow = maxRowHint == null ? null : maxRowHint.get(Util.BASE_MAX_ROW_KEY);
      Object subMaxRow = maxRowHint == null ? null : maxRowHint.get(Util.SUB_MAX_ROW_KEY);

      if(baseMaxRow == null && subMaxRow == null && amax == -1) {
         amax = Util.getAppliedMaxRows(table);
      }

      if(amax <= 0 && baseMaxRow == null && subMaxRow == null && hintMax == null) {
         return;
      }

      String elemId = elem.getID();
      String elemLogName = getSheetName() + "." + elemId;
      Catalog catalog = Catalog.getCatalog();
      String warningMsg = null;
      String logMsg = null;

      // 1. current query has maxrow.
      if(baseMaxRow == null && subMaxRow == null) {
         // 2. hint max may be the maxrow from mirror's base table.
         Object limitRow = hintMax != null ? hintMax  : amax;

         // should show min row limit.
         if(limitRow instanceof Integer && amax > 0 && (Integer) limitRow > amax) {
            limitRow = amax;
         }

         String tableOutputLimit = catalog.getString("table.output.maxrow.limited");

         if(!Tool.isEmptyString(tableOutputLimit) && tmax > 0 && limitRow != null &&
            Integer.parseInt(limitRow.toString()) == tmax)
         {
            warningMsg = logMsg = catalog.getString(
               "table.output.maxrow.limited", tmax);
         }
         else {
            warningMsg = catalog.getString(
               "designer.common.maxRows", elemId, limitRow);
            // display detail elem name in the log to provide more detail information for user.
            logMsg = catalog.getString(
               "designer.common.maxRows", elemLogName, limitRow);
         }
      }
      // 3. when sql is mergeable, current query has aggregate or condition, and has maxrow,
      // the maxrow is applied to the base query, so result of the aggregate/condition
      // query will never prompt maxrow limit warning, here we need to check the
      // max row limit for the base query, and prompt for user.
      else if(hintMax != null && baseMaxRow != null) {
         warningMsg = catalog.getString(
            "designer.common.maxRows", elemId, hintMax);
         logMsg = catalog.getString(
            "designer.common.maxRows", elemLogName, hintMax);
      }
      // 4. maxrow settings for sub queries, like join tables.
      else if(subMaxRow != null) {
         warningMsg = catalog.getString(
            "designer.common.maxRows.sub.message", elemId, subMaxRow);
         logMsg = catalog.getString(
            "designer.common.maxRows.sub.message", elemLogName, subMaxRow);
      }

      String val = ((ReportSheet) this).getProperty("display.warning");

      if(!"false".equals(val)) {
         if(page != null) {
            page.addInfo(warningMsg);
            // record the mapping of element and its warning
            elemInfoMap.put(warningMsg, elem.getID());
         }
      }

      LOG.warn(logMsg);
   }

   /**
    * Get report sheet name by context name.
    * @return
    */
   public String getSheetName() {
      String sheetName = null;

      if(this instanceof ReportSheet) {
         ReportSheet sheet = (ReportSheet) this;
         sheetName = sheet.getContextName();

         if(sheetName == null) {
            return null;
         }

         // don't add record for temporary preview report.
         if(sheetName != null && sheetName.indexOf(SUtil.ANALYTIC_REPORT) != -1) {
            String name = sheet.getProperty("reportName");
            sheetName = name != null ? name : sheetName;
         }

         int index = sheetName.lastIndexOf("/");

         if(index != -1 && index < sheetName.length() - 1) {
            return sheetName.substring(index + 1);
         }

         index = sheetName.indexOf(":");

         if(index != -1 && index < sheetName.length() - 1){
            return sheetName.substring(index + 1).trim();
         }

         return sheetName;
      }

      return null;
   }

   /**
    * Check if an element should be skiped.
    * @param min height of the header element. For most elements, this
    * should be zero. For section, this is the section header band height.
    * @param h element height.
    */
   public boolean skip(double min, double h) {
      if(printHead.y > min) {
         return false;
      }

      // check if the next frame is big enough for the band
      if(frames != null) {
         for(Rectangle frame : frames) {
            if(frame.height > h) {
               return false;
            }
         }
      }

      if(npframes != null) {
         for(Rectangle npframe : npframes) {
            if(npframe.height > h) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Set the value of an element.
    */
   public void setValue(ReportElement elem, Object data)
      throws IllegalArgumentException
   {
      if(elem instanceof TextElement) {
         if(data instanceof String) {
            ((TextElement) elem).setText((String) data);
         }
         else if(data instanceof TextLens) {
            ((TextElement) elem).setTextLens((TextLens) data);
         }
         else {
            ((TextElement) elem).setText(toString(data));
         }
      }
      else if(elem instanceof TextBoxElement) {
         if(data instanceof String) {
            ((TextBoxElement) elem).setTextLens(
               new DefaultTextLens((String) data));
         }
         else if(data instanceof TextLens) {
            ((TextBoxElement) elem).setTextLens((TextLens) data);
         }
         else {
            ((TextBoxElement) elem).setText(toString(data));
         }
      }
      else if(elem instanceof TableElement) {
         if(data instanceof TableLens) {
            String prop = SreeEnv.getProperty("SRO.debug.max");

            if(prop != null && prop.length() > 0) {
               TableLens tlens = (TableLens) data;
               data = new MaxRowsTableLens(tlens, Integer.parseInt(prop));
            }

            ((TableElement) elem).setTable((TableLens) data);
         }
         else if(data instanceof Object[][]) {
            TableLens lens = ((TableElement) elem).getTable();
            TableStyle style = null;

            if(lens instanceof TableStyle) {
               style = (TableStyle) lens;

               while(true) {
                  lens = style.getTable();
                  if(lens instanceof TableStyle) {
                     style = (TableStyle) lens;
                  }
                  else {
                     break;
                  }
               }
            }

            if(lens instanceof DefaultTableLens) {
               ((DefaultTableLens) lens).setData((Object[][]) data);
            }
            else if(style != null) {
               style.setTable(new DefaultTableLens((Object[][]) data));
            }
            else {
               ((TableElement) elem).setTable(
                  new DefaultTableLens((Object[][]) data));
            }
         }
         else if(!false) {
            throw new IllegalArgumentException("Only TableLens or FormLens " +
               "can be used in a Table: " + data.getClass());
         }
      }
      else if(elem instanceof SectionElement) {
         if(data instanceof TableLens) {
            ((SectionElement) elem).setTable((TableLens) data);
         }
         else if(data instanceof Object[][]) {
            ((SectionElement) elem).setTable(
               new DefaultTableLens((Object[][]) data));
         }
         else if(!false) {
            throw new IllegalArgumentException("Only TableLens can" +
               " be used in a Section: " + data.getClass());
         }
      }
      else if(elem instanceof ChartElement) {
         if(data instanceof DataSet) {
            ((ChartElement) elem).setDataSet((DataSet) data);
         }
         else if(data instanceof TableLens) {
            if(elem instanceof ChartElementDef) {
               ((ChartElementDef) elem).setTable((TableLens) data);
            }
            else {
               ((ChartElement) elem).setDataSet(
                  new XTableDataSet((TableLens) data));
            }
         }
         else if(!false) {
            throw new IllegalArgumentException("Only DataSet can" +
               " be used in a Chart: " + data.getClass());
         }
      }
      else if(elem instanceof PainterElementDef) {
         // check if it's a presenter
         Painter painter = ((PainterElement) elem).getPainter();

         if(painter instanceof PresenterPainter) {
            PresenterPainter presenter = (PresenterPainter) painter;

            if(data != null && presenter.isPresenterOf(data.getClass())) {
               presenter.setObject(data);
               return;
            }
         }

         if(data instanceof Image) {
            ((PainterElementDef) elem).setPainter(
               new ImagePainter((Image) data));
         }
         else if(data instanceof Painter) {
            ((PainterElement) elem).setPainter((Painter) data);
         }
         else if(data instanceof Component) {
            ((PainterElement) elem).setPainter(
               new ComponentPainter((Component) data));
         }
         else if(data == null || data.equals("")) {
            ((PainterElement) elem).setPainter(new EmptyPainter(null, 1, 1));
         }
         else if(!false) {
            throw new IllegalArgumentException("Only Image, Component, or " +
               " Painter can" + " be used in a Painter: " + data + "(" +
               data.getClass() + ")");
         }
      }
      else if(!false) {
         throw new IllegalArgumentException(elem + ":" + data);
      }
   }

   /**
    * Set the script runtime.
    */
   public void setScriptEnv(ReportScriptEnv scriptenv) {
      this.scriptenv = scriptenv;
   }

   /**
    * Get the script runtime.
    */
   public ReportScriptEnv getScriptEnv() {
      if(scriptenv == null) {
         scriptenv = ReportScriptEnvRepository.getScriptEnv((ReportSheet) this);

         // add the script objects to the env
         if(scriptobjs != null) {
            Enumeration keys = scriptobjs.keys();

            while(keys.hasMoreElements()) {
               String name = (String) keys.nextElement();
               Object obj = scriptobjs.get(name);

               scriptenv.put(name, obj);
            }
         }

         // set ToolBar to the scope
         assert scriptenv != null;

         // set drillfrom viewsheet to the scope
         try {
            WorksheetService service = WorksheetEngine.getWorksheetService();

            // set the pviewsheet so a script using pviewsheet won't fail
            scriptenv.put("pviewsheet", new PViewsheetScriptable());

            if(vars != null && vars.get("drillfrom") != null &&
               service instanceof ViewsheetService)
            {
               String id = vars.get("drillfrom") + "";
               Principal user = ThreadContext.getContextPrincipal();
               RuntimeViewsheet sheet =
                  ((ViewsheetService) service).getViewsheet(id, user);

               if(sheet != null) {
                  scriptenv.put("pviewsheet", sheet.getViewsheetSandbox().getScope());
               }
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to add the drill from viewsheet to the script scope", ex);
         }
      }

      return scriptenv;
   }

   /**
    * Re-create the script runtime environment from elements.
    */
   public void resetScriptEnv() {
      // re-populate the report environment
      if(scriptenv != null) {
         scriptenv.reset();
      }
   }

   /**
    * Destroy the script env.
    */
   public void deleteScriptEnv() {
      if(scriptenv != null) {
         scriptenv.dispose();
      }

      scriptenv = null;
   }

   /**
    * Add a script object.
    */
   public void addScriptObject(String name, Object obj) {
      // hashtable created on-demand to conserve space
      if(scriptobjs == null) {
         scriptobjs = new Hashtable<>();
      }

      scriptobjs.put(name, obj);

      if(scriptenv != null) {
         scriptenv.put(name, obj);
      }
   }

   /**
    * Get a script object set through addScriptObject.
    */
   public Object getScriptObject(String name) {
      return scriptobjs == null ? null : scriptobjs.get(name);
   }

   /**
    * Reset onLoad so that the next call to runOnLoad will execute the
    * onLoad script.  This is separate from the report reset method so
    * that the onLoad script will not be called multiple times in the
    * process of replet generation, for example, but only when needed.
    */
   public void resetOnLoad() {
      current = 0; // prevent onLoad called multiple times
      Enumeration elems = ElementIterator.elements((ReportSheet) this);

      while(elems.hasMoreElements()) {
         ReportElement elem = (ReportElement) elems.nextElement();

         if(elem instanceof BaseElement) {
            ((BaseElement) elem).resetOnLoad();
         }
      }
   }

   /**
    * Set the report parameters.
    */
   public void setVariableTable(VariableTable vars) {
      this.vars = vars;
   }

   /**
    * Get the report parameters.
    */
   public VariableTable getVariableTable() {
      return vars;
   }

   /**
    * This method is optionally called after a report is completely finished
    * processing (pages generated) so a report can swap out selective objects.
    */
   public void complete() {
      // @by larryl, don't clear out cache in design mode. Some element may
      // not be processed in design time (e.g. subreport), and iterating
      // and getting table may force the elements to be processed out of order
      if(false) {
         return;
      }

      Enumeration elems = ElementIterator.elements((ReportSheet) this);

      while(elems.hasMoreElements()) {
         ReportElement elem = (ReportElement) elems.nextElement();

         if(elem.isVisible() && elem instanceof Groupable) {
            // @by ankurp, must get top table so we do not reexecute scripts
            TableLens top = ((Groupable) elem).getTopTable();

            // clear out all cached data
            while(top != null) {
               if(top instanceof CachedTableLens) {
                  ((CachedTableLens) top).clearCache();
               }

               if(top instanceof TableFilter) {
                  top = ((TableFilter) top).getTable();
               }
               else {
                  break;
               }
            }
         }
      }
   }

   /**
    * Set the header/footer tracker.
    */
   public void setHFTextFormatter(HFTextFormatter fmt) {
      hfFmt = fmt;
   }

   /**
    * Get the header/footer tracker.
    */
   public HFTextFormatter getHFTextFormatter() {
      return getHFTextFormatter(false);
   }

   /**
    * Get the header/footer tracker.
    */
   HFTextFormatter getHFTextFormatter(boolean create) {
      if(hfFmt == null && create) {
         hfFmt = new HFTextFormatter(new Date(), pgStart, pgTotal);
      }

      return hfFmt;
   }

   /**
    * Get the build number.
    */
   public static String getBuildNumber() {
      return Tool.getBuildNumber();
   }

   /**
    * Convert an object to a string.
    */
   public String toString(Object data) {
      if(data == null) {
         return "";
      }
      else if(data instanceof String) {
         return (String) data;
      }

      Format fmt = getFormat(data.getClass());

      if(fmt != null) {
         return fmt.format(data);
      }

      return Tool.toString(data);
   }

   protected static void append(Vector all, Vector elements) {
      for(int i = 0; i < elements.size(); i++) {
         if(!all.contains(elements.elementAt(i))) {
            all.addElement(elements.elementAt(i));
         }
      }
   }

   protected static void append(Vector all, Object[] arr) {
      for(int i = 0; i < arr.length; i++) {
         all.addElement(arr[i]);
      }
   }

   /**
    * Make a copy of this report.
    */
   @Override
   protected Object clone() {
      try {
         StyleCore report = (StyleCore) super.clone();
         // @by jasons, create a new instance of the lock so that separate
         // instances don't lock each other
         report.pagingLock = new Object();
         report.istore = (IndexedStorage) ((XMLIndexedStorage) istore).clone();

         return report;
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Copy this to that.
    * @param report the report copied to.
    * @param flag if true flag all copied elements as from template.
    */
   protected void copyStyleCore(StyleCore report, boolean flag) {
      report.margin = (Margin) margin.clone();
      report.painterMargin = (Insets) painterMargin.clone();
      report.presentermap = (Hashtable) presentermap.clone();
      report.formatmap = new Hashtable<>(formatmap);

      report.headerFromEdge = headerFromEdge;
      report.footerFromEdge = footerFromEdge;
      report.printHead = new Position(0, 0);
      report.printBox = new Rectangle(72, 72, 468, 648);
      report.pageBox = new Rectangle(72, 72, 468, 648);
      report.lastHead = new Position(0, 0);
      report.frames = null;
      report.npframes = null;

      report.contexts = (Hashtable) contexts.clone();
      report.idmap = new Hashtable<>(idmap);
      report.parameters = Tool.deepCloneCollection(parameters);
      report.horFlow = horFlow;

      // header footer data
      report.headerElements = report.cloneElements(headerElements);
      report.footerElements = report.cloneElements(footerElements);
      report.firstHeader = report.cloneElements(firstHeader);
      report.firstFooter = report.cloneElements(firstFooter);
      report.evenHeader = report.cloneElements(evenHeader);
      report.evenFooter = report.cloneElements(evenFooter);
      report.oddHeader = report.cloneElements(oddHeader);
      report.oddFooter = report.cloneElements(oddFooter);

      report.currHeader = currHeader.clone(this, flag, true);
      report.currFooter = currFooter.clone(this, flag, false);

      report.prop = (Properties) prop.clone();
      report.scriptenv = null;

      report.scriptobjs = null;

      report.istore = (IndexedStorage) ((XMLIndexedStorage) istore).clone();

      // @by mikec, when a report is cloned, all objs should be kept for
      // otherwise when it is cloned and processed for exporting or printing,
      // the script will fail and the result will be incorrect.
      if(scriptobjs != null) {
         Enumeration keys = scriptobjs.keys();

         while(keys.hasMoreElements()) {
            String name = (String) keys.nextElement();
            Object obj = scriptobjs.get(name);

            report.addScriptObject(name, obj);
         }
      }

      // @by larryl, if a report is cloned, the parameters should be kept since
      // a report could be cloned and processed for exporting and printing,
      // if the scripts are executed again (single page, onPrint, ...), they
      // should work in the same ways as the live report
      if(vars != null) {
         try {
            report.vars = (VariableTable) vars.clone();

            report.addScriptObject("parameter", report.vars);
            report.addScriptObject("request", report.vars);
         }
         catch(Exception ex) {
            LOG.warn("Failed to copy report parameters", ex);
         }
      }

      report.hfFmt = null;
      report.bglayout = bglayout;
      report.bgsize = (bgsize != null) ? new Dimension(bgsize) : null;

      if(bg != null) {
         if(bg instanceof Color) {
            report.bg = new Color(((Color) bg).getRGB());
         }
         else if(bg instanceof MetaImage) {
            // @by jasons MetaImage and doesn't have a clone method, so we have
            // to make the copy manually.
            MetaImage img = (MetaImage) bg;

            try {
               MetaImage img2 =
                  new MetaImage((ImageLocation) img.getImageLocation().clone());
               // @by larryl, need to set the image in case the image is
               // embedded and can't be loaded from image location again
               img2.setImage(img.getImage());
               report.bg = img2;
            }
            catch(Exception exc) {
               LOG.error("Failed to set image", exc);
               report.bg = bg;
            }
         }
         else {
            LOG.error("Unknown background type: " + bg.getClass().getName());
            report.bg = bg;
         }
      }
   }

   /**
    * Make a copy of element vector.
    */
   protected Vector cloneElements(Vector elems) {
      Vector vec = new Vector();

      for(int i = 0; i < elems.size(); i++) {
         ReportElement elem = (ReportElement) elems.elementAt(i);

         elem = (ReportElement) elem.clone();

         if(elem != null) {
            ((BaseElement) elem).setReport((ReportSheet) this);
         }

         vec.addElement(elem);
      }

      return vec;
   }

   /**
    * Make a copy of element map (id -> vector).
    */
   private Hashtable cloneElementsMap(Hashtable elemmap) {
      Hashtable map = new Hashtable();
      Enumeration keys = elemmap.keys();
      Enumeration elems = elemmap.elements();

      while(keys.hasMoreElements()) {
         Object key = keys.nextElement();
         Vector elem = (Vector) elems.nextElement();

         map.put(key, cloneElements(elem));
      }

      return map;
   }

   /**
    * Get original hash code.
    */
   @Override
   public int addr() {
      return super.hashCode();
   }

   /**
    * Parse asset repository.
    */
   public void parseAssetRepository(Element node) throws Exception {
      Element elem = Tool.getChildNodeByTagName(node, "reportAssetRepository");

      if(elem != null) {
         ((XMLIndexedStorage) istore).parseXML(elem);
      }
   }

   /**
    * Write asset repository.
    */
   public void writeAssetRepository(PrintWriter writer) {
      ((XMLIndexedStorage) istore).writeXML(writer);
   }

   /**
    * Get one sheet.
    * @param entry the specified sheet entry.
    * @param user the specified user.
    * @param permission <tt>true</tt> to check permission, <tt>false</tt>
    * otherwise.
    * @return the associated sheet.
    */
   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user,
                                 boolean permission, AssetContent ctype)
      throws Exception
   {
      return getSheet(entry, user, permission, ctype, true);
   }

   @Override
   public AbstractSheet getSheet(AssetEntry entry, Principal user,
                                 boolean permission, AssetContent ctype, boolean useCache)
      throws Exception
   {
      return super.getSheet(entry, user, permission, ctype, useCache);
   }

   /**
    * Check the data model folder permission.
    *
    * @param folder the specified folder.
    * @param source the specified source.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
      return true;
   }

   /**
    * Check the query folder permission.
    *
    * @param folder the specified folder.
    * @param source the specified source.
    * @param user  the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean checkQueryFolderPermission(String folder, String source,
      Principal user)
   {
      return true;
   }

   /**
    * Check the query permission.
    * @param query the specified query.
    * @param user the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean checkQueryPermission(String query, Principal user) {
      return true;
   }

   /**
    * Check the datasource permission.
    * @param dname the specified datasource.
    * @param user the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean checkDataSourcePermission(String dname, Principal user) {
      return true;
   }

   /**
    * Check the datasource folder permission.
    * @param folder the specified datasource folder.
    * @param user the specified user.
    * @return <tt>true</tt> if pass, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean checkDataSourceFolderPermission(String folder,
                                                     Principal user)
   {
      return true;
   }

   /**
    * Check if the next print area or page is larger than the current
    * print area.
    */
   public boolean isNextAreaLarger() {
      // if there is larger area, continue
      for(int i = 0; frames != null && i < frames.length; i++) {
         if(frames[i].height > printBox.height) {
            return true;
         }
      }

      // if there is larger area on next page, continue
      for(int i = 0; npframes != null && i < npframes.length;
          i++) {
         if(npframes[i].height > printBox.height) {
            return true;
         }
      }

      return false;
   }

   /**
    * Reset the report sheet.
    */
   public synchronized void reset() {
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException {
      s.defaultReadObject();

      char ch = s.readChar();

      if(ch == 'I') {
         byte[] buf = (byte[]) s.readObject();

         if(buf != null) {
            int w = s.readInt();
            int h = s.readInt();

            bg = Encoder.decodeImage(w, h, buf);
         }
      }
      else {
         bg = s.readObject();
      }
   }

   private void writeObject(java.io.ObjectOutputStream stream)
      throws java.io.IOException {
      stream.defaultWriteObject();

      if(bg instanceof Image) {
         stream.writeChar('I');
         Image icon = (Image) bg;
         byte[] buf = Encoder.encodeImage(icon);
         stream.writeObject(buf);

         if(buf != null) {
            stream.writeInt(icon.getWidth(null));
            stream.writeInt(icon.getHeight(null));
         }
      }
      else {
         stream.writeChar('O');
         stream.writeObject(bg);
      }
   }

   /**
    * Record elem's warning informationan and elem id and
    * warning info's bounds.
    * @param key warning.
    * @param value a map<elem id, bounds>.
    */
   public void putElemWarnings(String key, Map<String, Rectangle> value) {
      elemInfoBoundsMap.put(key, value);
   }

   /**
    * Return the map which contains element id and
    * the element's warnings' information.
    */
   public Map<String, Map<String, Rectangle>> getElemInfoBoundsMap() {
      return elemInfoBoundsMap;
   }

   /**
    * Set the map which contains element id and
    * the element's warnings' information.
    */
   public void setElemInfoBoundsMap(Map<String, Map<String, Rectangle>> map) {
      elemInfoBoundsMap = map;
   }

   /**
    * Get the map which contains the mapping of element's warnings
    * and elemID.
    */
   public Map<String, String> getElemInfoMap() {
      return elemInfoMap;
   }

   /**
    * Get the lock object for paging.
    */
   public Object getPagingLock() {
      return pagingLock == null ? new Object() : pagingLock;
   }

   // all variables with double type are inch values, and int type
   // are pixel values
   protected Margin margin = new Margin(1, 1, 1, 1); // default margin in inch
   protected transient Margin cmargin = margin; // current margin in inch
   protected Margin pmargin = new Margin(g_pmargin); // current printer margin
   // the pmargin is used to offset the printer margin. this should be
   // taken care of by the print job, but there is a bug on win32
   // that ignores the printer margin setting, it is fixed in jdk 1.1.7
   // BUT it reappeared in jdk 1.2!!!
   protected static Margin g_pmargin = new Margin(0, 0, 0, 0); // printer margin

   static {
      try {
         if(SreeEnv.getProperty("os.name").startsWith("Win")) {
            String ver = SreeEnv.getProperty("java.version");

            if(ver.compareTo("1.1.7") < 0 ||
               ver.compareTo("1.2") >= 0 && ver.compareTo("1.2.2") < 0) {
               g_pmargin = new Margin(0.25, 0.25, 0.25, 0.25);
            }
         }
      }
      catch(Exception ignore) {
      }
   }

   // page setup
   protected double headerFromEdge = 0.5;        // in inch
   protected double footerFromEdge = 0.75; // in inch
   protected String header = null;        // page header
   // the page index where the page numbering and header/footer starts
   protected int pgStart = 0;
   protected int pgTotal = 0;
   // the following variables are used during population of the sheet
   protected int alignment = StyleConstants.H_LEFT;
   protected double indent = 0;        // indentation level (inch)
   protected Position anchor = null;        // distance from last element
   protected int hindent = 0;                // hanging indent (pixel)
   protected double[] tabStops = { 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5,
                                   6, 6.5, 7, 7.5, 8 };
   protected int spacing = 0;        // current line spacing
   protected Insets padding = new Insets(0, 1, 0, 1); // cell padding
   /*
    * we should not set current font's default value to be "Verdana", if set,
    * when use API to create report with CJK characters, or use designer to
    * insert text/textbox, as the default font is "Verdana", the display will
    * be too bad.
    */
   protected Font font = Util.DEFAULT_FONT;        // current font
   protected Color foreground = Color.black;        // current foreground
   protected Color background = null;        // current background
   protected int autosize = ReportSheet.TABLE_FIT_PAGE; // table layout policy
   // painter layout policy
   protected int painterLayout = ReportSheet.PAINTER_NON_BREAK;
   // painter external space
   protected Insets painterMargin = new Insets(1, 1, 1, 1);
   protected double tableW = 0;        // table width in inches
   protected Hashtable presentermap = new Hashtable(); // Class -> Presenter
   protected Hashtable<Class<?>, Format> formatmap = new Hashtable<>();
   protected boolean justify = false; // true to fully justify text
   protected int textadv = 3; // text advance after an element
   protected int sepadv = 4; // separator advance after an element
   protected int wrapping = ReportSheet.WRAP_BOTH; // current wrapping style
   protected boolean orphan = false; // widow/orphan control
   protected boolean tableorphan = false; // table orphan control
   // these two variables are used by the elements during printing
   // and modified in the element's print() method

   // current print location in a page in pixels
   // SilverStream changes: the Cell inner class can only access a
   // public member. Originally protected.
   public Position printHead = new Position(0, 0);
   // the line is abstract in the sense it does not corresponds to a text
   // line, but a 'line' of document elements, and the height the the
   // current max height of the element on the 'line'
   protected float lineH = 0; // this variable keeps the current line height
   // the bounds of the current print, changed for body, header, and footer
   // SilverStream changes: the Cell inner class can only access a public
   // member. Originally protected.
   public Rectangle printBox = new Rectangle(72, 72, 468, 648);
   // printable area on the page, same as printBox if no page area is used
   protected Rectangle pageBox = new Rectangle(72, 72, 468, 648);
   // the bottom right position of the last painter element, this is used
   // during wrapping of text around a painter
   protected Position lastHead = new Position(0, 0);
   // this is set to the last line height if an element takes up the entire
   // line
   protected float advanceLine = 0;
   // the variables used in printing
   protected int current = 0;        // element index
   // frame is an area define to print (with area). it's the same as
   // printBox under most situations, exception printBox can be within
   // a frame in some instances
   // all frames in the current page
   // SilverStream: the Cell inner class can only access a public member.
   // Originally protected.
   public Rectangle[] frames = null;
   // next page frames. this is empty unless a element-associated page
   // area is defined and the element is encounted in the current page
   // SilverStream: the Cell inner class can only access a public member.
   // Originally protected.
   public Rectangle[] npframes = null;
   // current printing frame
   // SilverStream: the Cell inner class can only access a public member.
   // Originally protected.
   public int currFrame = 0;
   protected Integer nextOrient = null; // next page orientation
   protected boolean rewinded = false; // true if last band is rewinded
   protected Hashtable contexts = new Hashtable(); // report context map
   protected Hashtable<String, Integer> idmap = new Hashtable<>();
   protected Vector<UserVariable> parameters = new Vector<>(); // report parameters

   // header footer data
   protected Vector headerElements = new Vector(); // header elements
   protected Vector footerElements = new Vector(); // footer elements
   protected Vector firstHeader = new Vector();        // first page header
   protected Vector firstFooter = new Vector();        // first page footer
   protected Vector evenHeader = new Vector();        // even page header
   protected Vector evenFooter = new Vector();        // even page footer
   protected Vector oddHeader = new Vector();        // odd page header
   protected Vector oddFooter = new Vector();        // odd page footer

   // the currently worked on header and footer
   protected StyleCurrentElements currHeader = new StyleCurrentElements(DEFAULT_HEADER, headerElements);
   protected StyleCurrentElements currFooter = new StyleCurrentElements(DEFAULT_FOOTER, footerElements);
   protected boolean horFlow = true; // horizontal flow (or vertical)

   protected Locale locale = Locale.getDefault();
   // report properties
   protected Properties prop = new Properties();
   protected transient Object bg; // background object (Color or Image)
   protected ImageLocation bgimage; // background image location
   protected Dimension bgsize = null; // background size
   protected int bglayout = StyleConstants.BACKGROUND_TILED;// background layout
   protected transient ReportScriptEnv scriptenv = null; // script runtime

   private transient Hashtable<String, Object> scriptobjs = null; // script objects
   private transient VariableTable vars = null; // report parameters
   protected transient HFTextFormatter hfFmt = null; // header/footer formatter
   private static final int RESERVED_SPACE = 15;
   private transient Object pagingLock = new Object();
   // warning informations ----> element id
   private Map<String,String> elemInfoMap = new HashMap<>();
   // warning information -------> (elem id------->warning's bounds)
   private Map<String,Map<String,Rectangle>> elemInfoBoundsMap = new HashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(StyleCore.class);
}
