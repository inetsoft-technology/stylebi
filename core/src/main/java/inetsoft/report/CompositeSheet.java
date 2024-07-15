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
package inetsoft.report;

import inetsoft.report.internal.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.Format;
import java.util.Arrays;
import java.util.Vector;

/**
 * A composite sheet object groups multiple ReportSheet objects into
 * one report. Reports are printed as one single report. Page numbers
 * are contiguous across reports by default.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class CompositeSheet extends ReportSheet {
   /**
    * Create a composite report.
    */
   public CompositeSheet(ReportSheet[] sheets) {
      this.sheets = sheets;

      if(sheets.length == 0) {
         throw new RuntimeException(
            "CompositeSheet must contain one or more ReportSheets");
      }

      lastS = sheets[sheets.length - 1];
      pagecnts = new int[sheets.length];
      parameters = null;
      // set default properties
      setProperty("sortOnHeader", "true");
   }

   /**
    * Create a composite report, specifying if contiguous page numbers.
    */
   public CompositeSheet(ReportSheet[] sheets, boolean contiguous) {
      this(sheets);
      this.contiguous = contiguous;
   }

   /**
    * Get the number of reports in this composite report.
    */
   public int getReportCount() {
      return sheets == null ? 0 : sheets.length;
   }

   /**
    * Get the specified report in this composite report.
    */
   public ReportSheet getReport(int idx) {
      return sheets[idx];
   }

   /**
    * Remove all elements from the contents area.
    */
   @Override
   protected void removeContents() {
      parameters = null;

      for(int i = 0; i < sheets.length; i++) {
         sheets[i].removeContents();
      }
   }

   /**
    * Reset all elemnts in the contents.
    */
   @Override
   protected void resetContents() {
      parameters = null;

      for(int i = 0; i < sheets.length; i++) {
         sheets[i].resetContents();
      }
   }

   /**
    * Get all elements in the report, including body,
    * and non-flow area elements.
    * @return vector of all elements.
    */
   @Override
   public Vector getAllElements() {
      Vector all = new Vector();

      for(int i = 0; i < sheets.length; i++) {
         append(all, sheets[i].getAllElements());
      }

      return all;
   }

   /**
    * Get all header elements in the report, including headers, element
    * associated headers.
    * @return vector of all header elements.
    */
   @Override
   public Vector getAllHeaderElements() {
      Vector all = new Vector();

      for(int i = 0; i < sheets.length; i++) {
         append(all, sheets[i].getAllHeaderElements());
      }

      return all;
   }

   /**
    * Get all footer elements in the report, including footers, element
    * associated footers.
    * @return vector of of all footer elements.
    */
   @Override
   public Vector getAllFooterElements() {
      Vector all = new Vector();

      for(int i = 0; i < sheets.length; i++) {
         append(all, sheets[i].getAllFooterElements());
      }

      return all;
   }

   /**
    * Set the page margin. The unit is in inches.
    * @param margin page margin.
    */
   @Override
   public void setMargin(Margin margin) {
      invoke(marginMethod, margin);
   }

   /**
    * Get the page margin in inches.
    * @return page margin.
    */
   @Override
   public Margin getMargin() {
      return sheets[0].getMargin();
   }

   /**
    * Set the page header position from the top of the page. The unit
    * is in inches. HeaderFromEdge and top page margin determine
    * the location and size of page headers.
    * @param inch header position.
    */
   @Override
   public void setHeaderFromEdge(double inch) {
      invoke(headerFromEdgeMethod, inch);
   }

   /**
    * Get the page header position from the top of the page. The unit
    * is in inches.
    * @return header position.
    */
   @Override
   public double getHeaderFromEdge() {
      return sheets[0].getHeaderFromEdge();
   }

   /**
    * Set the page footer position from the bottom of the page.
    * The unit is in inches. The FooterFromEdge and bottom page margin
    * determine the position and size of page footers.
    * @param inch footer position.
    */
   @Override
   public void setFooterFromEdge(double inch) {
      invoke(footerFromEdgeMethod, inch);
   }

   /**
    * Get the page footer position from the bottom of the page.
    * The unit is in inches.
    * @return footer position.
    */
   @Override
   public double getFooterFromEdge() {
      return sheets[0].getFooterFromEdge();
   }

   /**
    * Get the page header bounds.
    * @param pgsize page size in pixels.
    * @return header region.
    */
   @Override
   public Rectangle getHeaderBounds(Dimension pgsize) {
      return sheets[0].getHeaderBounds(pgsize);
   }

   /**
    * Get the page footer bounds.
    * @param pgsize page size in pixels.
    * @return footer region.
    */
   @Override
   public Rectangle getFooterBounds(Dimension pgsize) {
      return sheets[0].getFooterBounds(pgsize);
   }

   /**
    * Set the current alignment of the report elements. The alignment
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param align alignment flag, a bitwise OR of the H_LEFT, H_CENTER,
    * H_RIGHT, and V_TOP, V_CENTER, V_BOTTOM.
    */
   @Override
   public void setCurrentAlignment(int align) {
      invoke(alignmentMethod, align);
   }

   /**
    * Get the current setting of the alignment.
    * @return alignment flag.
    */
   @Override
   public int getCurrentAlignment() {
      return sheets[0].getCurrentAlignment();
   }

   /**
    * Set the current indentation level. The value is in inches. The indent
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param inch indentation size.
    */
   @Override
   public void setCurrentIndent(double inch) {
      invoke(indentMethod, inch);
   }

   /**
    * Get the current indentation in inches.
    * @return indentation.
    */
   @Override
   public double getCurrentIndent() {
      return sheets[0].getCurrentIndent();
   }

   /**
    * Set the tab stops.
    * @param pos tab stops in inches.
    */
   @Override
   public void setCurrentTabStops(double[] pos) {
      invoke(tabStopsMethod, pos);
   }

   /**
    * Return the current tab stop setting.
    * @return tab stop positions in inches.
    */
   @Override
   public double[] getCurrentTabStops() {
      return sheets[0].getCurrentTabStops();
   }

   /**
    * Set the current wrapping style. This affects how the text around
    * a Painter/Image/TextBox is wrapped.
    * @param wrapping one of the WRAP_NONE, WRAP_LEFT, WRAP_RIGHT,
    * WRAP_BOTH, and WRAP_TOP_BOTTOM.
    */
   @Override
   public void setCurrentWrapping(int wrapping) {
      invoke(wrappingMethod, wrapping);
   }

   /**
    * Get the current wrapping style.
    * @return wrapping option.
    */
   @Override
   public int getCurrentWrapping() {
      return sheets[0].getCurrentWrapping();
   }

   /**
    * Move the anchor to the new position. The anchor is relative to the
    * bottom of the last element, and the left paintable edge of the page.
    * If the anchor Y is negative, it's treated as the distance from the
    * top of the last element. If the anchor X is positive, it's the
    * distance from the left edge of the page area, otherwise, it's the
    * distance from the right edge of the page area.
    * The next element is placed at the new anchor position. This only
    * affects fixed size elements, Painter, Chart, Component, and Image.
    * @param anchor anchor position.
    */
   @Override
   public void moveAnchor(Position anchor) {
      lastS.moveAnchor(anchor);
   }

   /**
    * Set the current line spacing in pixels. The line spacing is the space
    * between the two lines. The line spacing parameter
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param spacing line spacing.
    */
   @Override
   public void setCurrentLineSpacing(int spacing) {
      invoke(lineSpacingMethod, spacing);
   }

   /**
    * Get the current line spacing setting.
    * @return line spacing.
    */
   @Override
   public int getCurrentLineSpacing() {
      return sheets[0].getCurrentLineSpacing();
   }

   /**
    * Set the current font of the document. The font will be used by
    * the text elements and possibly table elements. In the case of
    * table, if a font is returned from the TableLens, it's always
    * used. Otherwise, the current font is used. The font
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param font current document font.
    */
   @Override
   public void setCurrentFont(Font font) {
      invoke(fontMethod, font);
   }

   /**
    * Get the current font setting.
    * @return current document font.
    */
   @Override
   public Font getCurrentFont() {
      return sheets[0].getCurrentFont();
   }

   /**
    * Set the current document foreground color. The foreground color
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param fg foreground color.
    */
   @Override
   public void setCurrentForeground(Color fg) {
      invoke(foregroundMethod, fg);
   }

   /**
    * Get the current document foreground color.
    * @return foreground color.
    */
   @Override
   public Color getCurrentForeground() {
      return sheets[0].getCurrentForeground();
   }

   /**
    * Set the current document background color. The background color
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param bg background color.
    */
   @Override
   public void setCurrentBackground(Color bg) {
      invoke(backgroundMethod, bg);
   }

   /**
    * Get the current document background color.
    * @return background color.
    */
   @Override
   public Color getCurrentBackground() {
      return sheets[0].getCurrentBackground();
   }

   /**
    * Set the current table layout mode. The available modes are:
    * TABLE_FIT_PAGE, TABLE_FIT_CONTENT, TABLE_EQUAL_WIDTH, and
    * TABLE_FIT_CONTENT_1PP.
    * The table layout mode
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * <p>
    * If the layout is set to TABLE_FIT_PAGE, the width of the table is
    * set to the width of the page, or to the table width explicitly
    * set by the user. The space is distributed to the columns proportional
    * to the preferred width of the columns. The preferred width of
    * a column is returned by the TableLens, or if it's -1, calculated
    * by the ReportSheet based on the contents in the column cells.
    * <p>
    * If the layout is set to TABLE_EQUAL_WIDTH, the width of the table
    * is set to the width of the page, or to the table width explicitly
    * set by the user. All columns are set to equal width.
    * <p>
    * If the layout is set to TABLE_FIT_CONTENT, the widht of the columns
    * are determined by the TableLens.getColWidth() return value, or if
    * it's -1, calculated by the ReportSheet based on the contents in the
    * column cells. If a row is wider than the page width, the rows are
    * wrapped. When rows are wrapped, the header columns (determined by
    * TableLens' getHeaderColCount() function) are always drawn at each
    * table segment. When table is wrapped, the ReportSheet tries to fit
    * as many table regions on a page as possible. It can be forced to
    * print one table region per page by using TABLE_FIT_CONTENT_1PP
    * as the table layout.
    * @param autosize layout mode.
    */
   @Override
   public void setCurrentTableLayout(int autosize) {
      invoke(tableLayoutMethod, autosize);
   }

   /**
    * Get the current table layout mode.
    * @return layout mode.
    */
   @Override
   public int getCurrentTableLayout() {
      return sheets[0].getCurrentTableLayout();
   }

   /**
    * Set the layout policy for painter. If the layout is set to the
    * PAINTER_NON_BREAK, the painter is always printed in one area. If
    * the painter's preferred size is greater than the space left in
    * the current page, the painter is printed on the top of the next
    * page. If the layout is set to the PAINTER_BREAKABLE, the painter
    * is printed with whatever space in the page, and may possibly
    * span across multiple pages.
    * @param policy painter layout policy.
    */
   @Override
   public void setCurrentPainterLayout(int policy) {
      invoke(painterLayoutMethod, policy);
   }

   /**
    * Get the current painter layout policy.
    * @return painter layout policy.
    */
   @Override
   public int getCurrentPainterLayout() {
      return sheets[0].getCurrentPainterLayout();
   }

   /**
    * Set the space around the painter elements. This controls the painter,
    * text box, and chart elements' external spacing.
    * @param margin painter external space.
    */
   @Override
   public void setCurrentPainterMargin(Insets margin) {
      invoke(painterMarginMethod, margin);
   }

   /**
    * Get the fixed size element external space.
    * @return space around painter elements.
    */
   @Override
   public Insets getCurrentPainterMargin() {
      return sheets[0].getCurrentPainterMargin();
   }

   /**
    * Set the cell padding space around the cell contents. The default
    * is 1 pixel on top and bottom, and 2 pixel at each side. Padding
    * is only applied to cells where the content is presented as
    * string text. If the content of the cell is other types, such as
    * Image, or if the content has registered presenters, the padding
    * is ignored. In this case the space around the cell content is
    * controlled by the getInsets() in the TableLens only. The padding
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param padding cell padding space.
    */
   @Override
   public void setCurrentCellPadding(Insets padding) {
      invoke(cellPaddingMethod, padding);
   }

   /**
    * Get the current table cell padding.
    * @return cell padding setting.
    */
   @Override
   public Insets getCurrentCellPadding() {
      return sheets[0].getCurrentCellPadding();
   }

   /**
    * Set the width of the table in inches. This parameter is only used
    * if the table layout is set to TABLE_FIT_PAGE or TABLE_EQUAL_WIDTH.
    * @param inch table width.
    */
   @Override
   public void setCurrentTableWidth(double inch) {
      invoke(tableWidthMethod, inch);
   }

   /**
    * Get the current table width setting.
    * @return table width.
    */
   @Override
   public double getCurrentTableWidth() {
      return sheets[0].getCurrentTableWidth();
   }

   /**
    * The following methods are used to setup the document.
    */

   /**
    * Register a presenter for the specified class. The presenter is
    * used to paint the visual representation of all values of the
    * specified type.
    * @param type type of the values to present.
    * @param p presenter object.
    */
   @Override
   public void addPresenter(Class type, Presenter p) {
      invoke(addPresenterMethod, type, p);
   }

   /**
    * Get the presenter object registered for this class or one of it's
    * super classes.
    * @param type class to search for.
    * @return the presenter for this object.
    */
   @Override
   public Presenter getPresenter(Class type) {
      return StyleCore.getPresenter(presentermap, type);
   }

   /**
    * Remove the specified presenter from the registry. Objects added
    * before the call are not affected.
    * @param type object type.
    */
   @Override
   public void removePresenter(Class type) {
      invoke(removePresenterMethod, type);
   }

   /**
    * Clear the presenter registry.
    */
   @Override
   public void clearPresenter() {
      invoke(clearPresenterMethod);
   }

   /**
    * Register a format for the specified class. The format is
    * used to convert an object to a string for all values of the
    * specified type.
    * @param type type of the values to present.
    * @param p format object.
    */
   @Override
   public void addFormat(Class type, Format p) {
      invoke(addFormatMethod, type, p);
   }

   /**
    * Get the format object registered for this class or one of it's
    * super classes.
    * @param type class to search for.
    * @return the format for this object.
    */
   @Override
   public Format getFormat(Class type) {
      Format format = null;

      for(int i = 0; i < sheets.length && type != null &&
         (format = sheets[i].getFormat(type)) == null; i++) {
      }

      return format;
   }

   /**
    * Remove the specified format from the registry. Objects added
    * before the call are not affected.
    * @param type object type.
    */
   @Override
   public void removeFormat(Class type) {
      invoke(removeFormatMethod, type);
   }

   /**
    * Clear the format registry.
    */
   @Override
   public void clearFormat() {
      invoke(clearFormatMethod);
   }

   /**
    * Set the currently worked on header. All subsequent calls to
    * addHeader???() methods will add the element to the current
    * header. The headers can be either DEFAULT_HEADER, FIRST_PAGE_HEADER,
    * EVEN_PAGE_HEADER, or ODD_PAGE_HEADER.
    * @param hflag header flag.
    */
   @Override
   public void setCurrentHeader(int hflag) {
      invoke(headerMethod, hflag);
   }

   /**
    * Set the currently worked on footer. All subsequent calls to
    * addFooter???() methods will add the element to the current
    * footer. The footers can be either DEFAULT_FOOTER, FIRST_PAGE_FOOTER,
    * EVEN_PAGE_FOOTER, or ODD_PAGE_FOOTER.
    * @param hflag footer flag.
    */
   @Override
   public void setCurrentFooter(int hflag) {
      invoke(footerMethod, hflag);
   }

   /**
    * If justify is set to true, text lines are fully justified.
    * @param justify text justification.
    */
   @Override
   public void setCurrentJustify(boolean justify) {
      invoke(justifyMethod, justify);
   }

   /**
    * Check if text is justified.
    * @return justification setting.
    */
   @Override
   public boolean isCurrentJustify() {
      return sheets[0].isCurrentJustify();
   }

   /**
    * Set the amount to advance following each text element. The default
    * advance is 3 pixels.
    * @param textadv text element advance pixels.
    */
   @Override
   public void setCurrentTextAdvance(int textadv) {
      invoke(textAdvanceMethod, textadv);
   }

   /**
    * Get the advance of text elements.
    * @return text advance in pixels.
    */
   @Override
   public int getCurrentTextAdvance() {
      return sheets[0].getCurrentTextAdvance();
   }

   /**
    * Set the table widow/orphan control option.
    * @param orphan true to eliminate widow/orphan lines.
    */
   @Override
   public void setCurrentTableOrphanControl(boolean orphan) {
      invoke(tableOrphanControlMethod, orphan);
   }

   /**
    * Check the current table widow/orphan control setting.
    * @return widow/orphan control option.
    */
   @Override
   public boolean isCurrentTableOrphanControl() {
      return sheets[0].isCurrentTableOrphanControl();
   }

   /**
    * Set the widow/orphan line control option.
    * @param orphan true to eliminate widow/orphan lines.
    */
   @Override
   public void setCurrentOrphanControl(boolean orphan) {
      invoke(orphanControlMethod, orphan);
   }

   /**
    * Check the current widow/orphan control setting.
    * @return widow/orphan control option.
    */
   @Override
   public boolean isCurrentOrphanControl() {
      return sheets[0].isCurrentOrphanControl();
   }

   /**
    * Add an object to the document header. First the ReportSheet checks if
    * a presenter is register for this type of object. If there is
    * a presenter, a PresenterPainter is created to paint the object
    * using the presenter.
    * <p>
    * If there is no presenter registered at the document for this
    * type of object, the ReportSheet then check if a Format is
    * register for this class. If there is a Format, it's used to
    * format the object into string and treated as a regular text.
    * <p>
    * If there is no format registered for this object type, the
    * object is converted to a string (toString()). The string is process
    * in the same way as the addHeaderText() string.
    * @param obj object value.
    * @return element id.
    */
   @Override
   public String addHeaderObject(Object obj) {
      return lastS.addHeaderObject(obj);
   }

   /**
    * Add a text string to the document header.
    * The header string could be a plain text string, or a text format
    * string similar to the format used by the java.text.MessageFormat
    * class. If a plain string is used, it's printed as the header.
    * Variables can be inserted into the string to construct a message
    * format. The following variables are supported:
    * <p>
    * <ul>
    * <li>{P}<br>Page number.</li>
    * <li>{P,format}<br>Page number in specified format. The format
    * string can be any format supported by the java.text.MessageFormat
    * class for numbers.</li>
    * <li>{N}<br>Number of pages.</li>
    * <li>{N,format}<br>Number of pages in specified format. The format
    * string can be any format supported by the java.text.MessageFormat
    * class for numbers.</li>
    * <li>{D}<br>Date in default date format.</li>
    * <li>{D,format}<br>Date and a date format. The format string can
    * be any format supported by the java.text.SimpleDateFormat class.
    * <li>{T}<br>Time in default time format.</li>
    * <li>{T,format}<br>Time and a time format. The format string can
    * be any format supported by the java.text.SimpleDateFormat class.
    * </ul>
    * @param text text string.
    * @return element id.
    */
   @Override
   public String addHeaderText(String text) {
      return lastS.addHeaderText(text);
   }

   /**
    * Add a text element to the document header. The contents of the
    * TextLens is processed in the same way as the addHeaderText(String)
    * parameter.
    * @param text text content lens.
    * @return element id.
    */
   @Override
   public String addHeaderText(TextLens text) {
      return lastS.addHeaderText(text);
   }

   /**
    * Add a text box to the document header. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well. The text is not translated as the plain
    * text elements.
    * @param text text content.
    * @return element id.
    */
   @Override
   public String addHeaderTextBox(TextLens text) {
      return lastS.addHeaderTextBox(text);
   }

   /**
    * Add a text box to the document header. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well. The text is not translated as the plain
    * text elements.
    * @param text text content.
    * @param border border line style. One of the line styles defined in
    * the StyleConstants class.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @param talign text alignment
    * @return element id.
    */
   @Override
   public String addHeaderTextBox(TextLens text, int border, double winch,
                                  double hinch, int talign) {
      return lastS.addHeaderTextBox(text, border, winch, hinch, talign);
   }

   /**
    * Add a pinter element to the document header. A painter is a self
    * contained
    * object that can paint a document area. It can be used to add any
    * content to the document, through which the program has full control
    * on exact presentation on the document. Painter is the general
    * mechanism used to support some of the more common data types. For
    * example, Component and Image are handled internally by a painter
    * object. The program is free to define its own painter.
    * @param area the painter element.
    * @return element id.
    */
   @Override
   public String addHeaderPainter(Painter area) {
      return lastS.addHeaderPainter(area);
   }

   /**
    * This is same as addHeaderPainter() exception an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param area the painter element.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   @Override
   public String addHeaderPainter(Painter area, double winch, double hinch) {
      return lastS.addHeaderPainter(area, winch, hinch);
   }

   /**
    * Add an image to the document header.
    * @param image image object.
    * @return element id.
    */
   @Override
   public String addHeaderImage(Image image) {
      return lastS.addHeaderImage(image);
   }

   /**
    * This is same as addHeaderImage() exception an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param image image to paint.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   @Override
   public String addHeaderImage(Image image, double winch, double hinch) {
      return lastS.addHeaderImage(image, winch, hinch);
   }

   /**
    * Add an image to the document header.
    * @param image image URL.
    * @return element id.
    */
   @Override
   public String addHeaderImage(URL image) {
      return lastS.addHeaderImage(image);
   }

   /**
    * Add horizontal space to the document header. The space is added after
    * the current element.
    * @param pixels space in pixels.
    * @return element id.
    */
   @Override
   public String addHeaderSpace(int pixels) {
      return lastS.addHeaderSpace(pixels);
   }

   /**
    * Add one or more newline to the document header.
    * @param n number of newline.
    * @return element id.
    */
   @Override
   public String addHeaderNewline(int n) {
      return lastS.addHeaderNewline(n);
   }

   /**
    * Add a break to the document header.
    * @return element id.
    */
   @Override
   public String addHeaderBreak() {
      return lastS.addHeaderBreak();
   }

   /**
    * Add an object to the document footer. First the ReportSheet checks if
    * a presenter is register for this type of object. If there is
    * a presenter, a PresenterPainter is created to paint the object
    * using the presenter.
    * <p>
    * If there is no presenter registered at the document for this
    * type of object, the ReportSheet then check if a Format is
    * register for this class. If there is a Format, it's used to
    * format the object into string and treated as a regular text.
    * <p>
    * If there is no format registered for this object type, the
    * object is converted to a string (toString()). The string is process
    * in the same way as the addFooterText() string.
    * @param obj object value.
    * @return element id.
    */
   @Override
   public String addFooterObject(Object obj) {
      return lastS.addFooterObject(obj);
   }

   /**
    * Add a text string to the document footer.
    * The footer string could be a plain text string, or a text format
    * string similar to the format used by the java.text.MessageFormat
    * class. If a plain string is used, it's printed as the footer.
    * Variables can be inserted into the string to construct a message
    * format. The following variables are supported:
    * <p>
    * <ul>
    * <li>{P}<br>Page number.</li>
    * <li>{P,format}<br>Page number in specified format. The format
    * string can be any format supported by the java.text.MessageFormat
    * class for numbers.</li>
    * <li>{N}<br>Number of pages.</li>
    * <li>{N,format}<br>Number of pages in specified format. The format
    * string can be any format supported by the java.text.MessageFormat
    * class for numbers.</li>
    * <li>{D}<br>Date in default date format.</li>
    * <li>{D,format}<br>Date and a date format. The format string can
    * be any format supported by the java.text.SimpleDateFormat class.
    * <li>{T}<br>Time in default time format.</li>
    * <li>{T,format}<br>Time and a time format. The format string can
    * be any format supported by the java.text.SimpleDateFormat class.
    * </ul>
    * @param text text string.
    * @return element id.
    */
   @Override
   public String addFooterText(String text) {
      return lastS.addFooterText(text);
   }

   /**
    * Add a text element to the document footer. The contents of the
    * TextLens is processed in the same way as the addFooterText(String)
    * parameter.
    * @param text text content lens.
    * @return element id.
    */
   @Override
   public String addFooterText(TextLens text) {
      return lastS.addFooterText(text);
   }

   /**
    * Add a text box to the document footer. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well. The text is not translated as the plain
    * text elements.
    * @param text text content.
    * @return element id.
    */
   @Override
   public String addFooterTextBox(TextLens text) {
      return lastS.addFooterTextBox(text);
   }

   /**
    * Add a text box to the document footer. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well. The text is not translated as the plain
    * text elements.
    * @param text text content.
    * @param border border line style. One of the line styles defined in
    * the StyleConstants class.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @param talign text alignment.
    * @return element id.
    */
   @Override
   public String addFooterTextBox(TextLens text, int border, double winch,
                                  double hinch, int talign) {
      return lastS.addFooterTextBox(text, border, winch, hinch, talign);
   }

   /**
    * Add a pinter element to the document footer. A painter is a self
    * contained
    * object that can paint a document area. It can be used to add any
    * content to the document, through which the program has full control
    * on exact presentation on the document. Painter is the general
    * mechanism used to support some of the more common data types. For
    * example, Component and Image are handled internally by a painter
    * object. The program is free to define its own painter.
    * @param area the painter element.
    * @return element id.
    */
   @Override
   public String addFooterPainter(Painter area) {
      return lastS.addFooterPainter(area);
   }

   /**
    * This is same as addFooterPainter() exception an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param area the painter element.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   @Override
   public String addFooterPainter(Painter area, double winch, double hinch) {
      return lastS.addFooterPainter(area, winch, hinch);
   }

   /**
    * Add an image to the document footer.
    * @param image image object.
    * @return element id.
    */
   @Override
   public String addFooterImage(Image image) {
      return lastS.addFooterImage(image);
   }

   /**
    * This is same as addFooterImage() exception an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param image image to paint.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   @Override
   public String addFooterImage(Image image, double winch, double hinch) {
      return lastS.addFooterImage(image, winch, hinch);
   }

   /**
    * Add an image to the document footer.
    * @param image image URL.
    * @return element id.
    */
   @Override
   public String addFooterImage(URL image) {
      return lastS.addFooterImage(image);
   }

   /**
    * Add horizontal space to the document footer. The space is added after
    * the current element.
    * @param pixels space in pixels.
    * @return element id.
    */
   @Override
   public String addFooterSpace(int pixels) {
      return lastS.addFooterSpace(pixels);
   }

   /**
    * Add one or more newline to the document footer.
    * @param n number of newline.
    * @return element id.
    */
   @Override
   public String addFooterNewline(int n) {
      return lastS.addFooterNewline(n);
   }

   /**
    * Add a break to the document footer.
    * @return element id.
    */
   @Override
   public String addFooterBreak() {
      return lastS.addFooterBreak();
   }

   /**
    * Find the index of the specified element. An element is identified
    * by an unique ID. Null IDs are ignored.
    * @param id element ID.
    * @return element index or -1 if not found.
    */
   @Override
   public ReportElement getElement(String id) {
      for(int i = 0; i < sheets.length; i++) {
         ReportElement elem = sheets[i].getElement(id);

         if(elem != null) {
            return elem;
         }
      }

      return null;
   }

   /**
    * Add an element to the document header. Classes extending the ReportSheet
    * can extend element classes from the Element, and use
    * this method for adding the element to the document.
    * @param e document element.
    * @return element id.
    */
   @Override
   public String addHeaderElement(ReportElement e) {
      parameters = null;
      return lastS.addHeaderElement(e);
   }

   /**
    * Return the number of elements in the document header.
    * @return number of elements.
    */
   @Override
   public int getHeaderElementCount() {
      int cnt = 0;

      for(int i = 0; i < sheets.length; i++) {
         cnt += sheets[i].getHeaderElementCount();
      }

      return cnt;
   }

   /**
    * Get the specified element in the header.
    * @param idx element index.
    * @return document header element.
    */
   @Override
   public ReportElement getHeaderElement(int idx) {
      for(int i = 0; i < sheets.length; i++) {
         if(idx < sheets[i].getHeaderElementCount()) {
            return sheets[i].getHeaderElement(idx);
         }

         idx -= sheets[i].getHeaderElementCount();
      }

      return null;
   }

   /**
    * Get the index of the specified element.
    * @param e element.
    * @return element index.
    */
   @Override
   public int getHeaderElementIndex(ReportElement e) {
      int idx = 0;

      for(int i = 0; i < sheets.length; i++) {
         int i2 = sheets[i].getHeaderElementIndex(e);

         if(i2 >= 0) {
            return i2 + idx;
         }

         idx += sheets[i].getHeaderElementCount();
      }

      return -1;
   }

   /**
    * Remove the specified element.
    * @param idx element index.
    */
   @Override
   public void removeHeaderElement(int idx) {
      parameters = null;

      for(int i = 0; i < sheets.length; i++) {
         if(idx < sheets[i].getHeaderElementCount()) {
            sheets[i].removeHeaderElement(idx);
            return;
         }

         idx -= sheets[i].getHeaderElementCount();
      }
   }

   /**
    * Insert the element at specified position (before).
    * @param idx position to insert.
    * @param e element.
    */
   @Override
   public void insertHeaderElement(int idx, ReportElement e) {
      parameters = null;

      for(int i = 0; i < sheets.length; i++) {
         if(idx < sheets[i].getHeaderElementCount()) {
            sheets[i].insertHeaderElement(idx, e);
         }

         idx -= sheets[i].getHeaderElementCount();
      }
   }

   /**
    * Add an element to the document footer. Classes extending the ReportSheet
    * can extend element classes from the Element, and use
    * this method for adding the element to the document.
    * @param e document element.
    * @return element id.
    */
   @Override
   public String addFooterElement(ReportElement e) {
      parameters = null;

      return lastS.addFooterElement(e);
   }

   /**
    * Return the number of elements in the document footer.
    * @return number of elements.
    */
   @Override
   public int getFooterElementCount() {
      int cnt = 0;

      for(int i = 0; i < sheets.length; i++) {
         cnt += sheets[i].getFooterElementCount();
      }

      return cnt;
   }

   /**
    * Get the specified element in the footer.
    * @param idx element index.
    * @return document footer element.
    */
   @Override
   public ReportElement getFooterElement(int idx) {
      for(int i = 0; i < sheets.length; i++) {
         if(idx < sheets[i].getFooterElementCount()) {
            return sheets[i].getFooterElement(idx);
         }

         idx -= sheets[i].getFooterElementCount();
      }

      return null;
   }

   /**
    * Get the index of the specified element.
    * @param e element.
    * @return element index.
    */
   @Override
   public int getFooterElementIndex(ReportElement e) {
      int idx = 0;

      for(int i = 0; i < sheets.length; i++) {
         int i2 = sheets[i].getFooterElementIndex(e);

         if(i2 >= 0) {
            return i2 + idx;
         }

         idx += sheets[i].getFooterElementCount();
      }

      return -1;
   }

   /**
    * Remove the specified element.
    * @param idx element index.
    */
   @Override
   public void removeFooterElement(int idx) {
      parameters = null;

      for(int i = 0; i < sheets.length; i++) {
         if(idx < sheets[i].getFooterElementCount()) {
            sheets[i].removeFooterElement(idx);
            return;
         }

         idx -= sheets[i].getFooterElementCount();
      }
   }

   /**
    * Insert the element at specified position (before).
    * @param idx position to insert.
    * @param e element.
    */
   @Override
   public void insertFooterElement(int idx, ReportElement e) {
      parameters = null;

      for(int i = 0; i < sheets.length; i++) {
         if(idx < sheets[i].getFooterElementCount()) {
            sheets[i].insertFooterElement(idx, e);
         }

         idx -= sheets[i].getFooterElementCount();
      }
   }

   /**
    * Get the elements of the specified type. The type is one of the
    * header or footer types, or zero for the body.
    * @param type element type.
    * @return elements of specified type.
    */
   @Override
   public Vector getElements(int type) {
      Vector vec = new Vector();

      for(int i = 0; i < sheets.length; i++) {
         Vector elems = sheets[i].getElements(type);

         for(int j = 0; j < elems.size(); j++) {
            vec.addElement(elems.elementAt(j));
         }
      }

      return vec;
   }

   /**
    * Methods to print pages.
    */

   /**
    * Print one page. Return true if more contents need to be printed.
    * Normally print(PrintJob) should be used for printing. This function
    * is used by print() to print individual pages.
    * <p>
    * A StylePage contains information on how to print a particular page.
    * Its print() method can be used to perform the actual printing of
    * the page contents to a printer graphics.
    * @param pg style page.
    */
   @Override
   public synchronized boolean printNext(StylePage pg) {
      Dimension csize = pg.getPageDimension();

      // @by billh, fix customer bug bug1306343897843
      // handle single page properly for composite sheet
      if(csize.height == Integer.MAX_VALUE) {
      	 int idx = 0;
      	 int height = 0;

         for(int i = 0; i < getReportCount(); i++) {
            ReportSheet report = getReport(i);
            report.printNext(pg);
            int nidx = pg.getPaintableCount();

            if(height > 0) {
               for(int j = idx; j < nidx; j++) {
                  Paintable pt = pg.getPaintable(j);
                  Point loc = pt.getLocation();
                  loc.y += height;
                  pt.setLocation(loc);
               }
            }

            Size size = Util.getPageSize(report);
            Dimension dim =
               new Dimension((int) (size.width * 72), (int) (size.height * 72));
            height += dim.height;
            idx = nidx;
         }

         return false;
      }
      else {
         return printNextPage(pg);
      }
   }

   /**
    * Print the next page. It may be called to print from a middle of
    * of page provided the printBox is set up correctly before the call.
    */
   @Override
   public boolean printNextPage(StylePage pg) {
      if(currSheet < sheets.length) {
         boolean more = sheets[currSheet].printNext(pg);

         if(!more) {
            sheets[currSheet].complete();
            ObjectCache.clear();
         }

         pagecnts[currSheet]++;

         if(first) {
            first = false;

            for(int i = 1; contiguous && i < sheets.length; i++) {
               sheets[i].setHFTextFormatter(sheets[0].getHFTextFormatter());
            }

            setHFTextFormatter(sheets[0].getHFTextFormatter());
         }

         if(!more) {
            currSheet++;
         }
         else {
            return true;
         }
      }

      if(currSheet >= sheets.length) {
         currSheet--;
         return false;
      }
      else {
         return true;
      }
   }

   /**
    * Get the page orientation for the next page. The page orientation
    * can be changed in middle of a report by setting it in PageLayout
    * for StyleSheet, or setting the row orientation in a TabularSheet.
    * @return the next page orientation. Null if using default orientation.
    */
   @Override
   public Integer getNextOrientation() {
      Integer orient = sheets[currSheet].getNextOrientation();

      // @by jasons for a composite report, the orientation of the reports may
      // change while the orientation inside the reports doesn't change. Because
      // of this we can't default to null, in which case the orientation won't
      // be changed. Instead, use the orientation of the report itself.
      if(orient == null) {
         return sheets[currSheet].getOrientation();
      }

      return orient;
   }

   /**
    * Get the page size for the next report.
    */
   @Override
   public Size getPageSize() {
      Size size = sheets[currSheet].getPageSize();

      if(size == null) {
         size = super.getPageSize();
      }

      return size;
   }

   /**
    * Print the header and footer of the page. The header and footer can
    * not be printed until all pages have been generated because we won't
    * know the total number of pages.
    * @param pg a style page.
    */
   @Override
   protected synchronized void printHeaderFooter(StylePage pg) {
      int pgidx = first ? 0 : hfFmt.getPageIndex();

      for(int i = 0; i < sheets.length; i++) {
         if(pgidx < pagecnts[i]) {
            sheets[i].printHeaderFooter(pg);
            hfFmt.nextPage(true);
            return;
         }

         pgidx -= pagecnts[i];
      }
   }

   /**
    * Initiate parameters.
    */
   private void initParameters() {
      parameters = new Vector();

      for(int i = 0; i < getReportCount(); i++) {
         ReportSheet report = getReport(i);

         for(int j = 0; j < report.getParameterCount(); j++) {
            parameters.add(report.getParameter(j));
         }
      }
   }

   /**
    * Get a report parameter definition.
    * @param idx parameter index.
    * @return parameter definition.
    */
   @Override
   public UserVariable getParameter(int idx) {
      if(parameters == null) {
         initParameters();
      }

      return (UserVariable) parameters.elementAt(idx);
   }

   /**
    * Get the number of parameters defined in this report. The runtime
    * parameters of a report is the union of the parameters defined
    * in the report, and the parameters defined in the queries used
    * in the report. If a parameter is defined in both places, the
    * definition in the report is used.
    * @return number of parameters defined in the report.
    */
   @Override
   public int getParameterCount() {
      if(parameters == null) {
         initParameters();
      }

      return parameters.size();
   }

   /**
    * Set the script runtime.
    */
   @Override
   public void setScriptEnv(ReportScriptEnv scriptenv) {
      super.setScriptEnv(scriptenv);

      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).setScriptEnv(scriptenv);
      }
   }

   /**
    * Re-create the script runtime environment from elements.
    */
   @Override
   public void resetScriptEnv() {
      super.resetScriptEnv();

      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).resetScriptEnv();
      }
   }

   /**
    * Destroy the script env.
    */
   @Override
   public void deleteScriptEnv() {
      super.deleteScriptEnv();

      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).deleteScriptEnv();
      }
   }

   /**
    * Add a script object.
    */
   @Override
   public void addScriptObject(String name, Object obj) {
      super.addScriptObject(name, obj);

      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).addScriptObject(name, obj);
      }
   }

   /**
    * Reset onLoad so that the next call to runOnLoad will execute the
    * onLoad script.  This is separate from the report reset method so
    * that the onLoad script will not be called multiple times in the
    * process of replet generation, for example, but only when needed.
    */
   @Override
   public void resetOnLoad() {
      super.resetOnLoad();

      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).resetOnLoad();
      }
   }

   /**
    * Set the report parameters.
    */
   @Override
   public void setVariableTable(VariableTable vars) {
      super.setVariableTable(vars);

      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).setVariableTable(vars);
      }
   }

   /**
    * Remove all report elements.
    */
   @Override
   public void clear() {
      parameters = null;
      invoke(clearMethod);
   }

   /**
    * Reset the printing. If there is a partially printed job, the
    * rest of the contents are ignored. The next printNext starts
    * from first page.
    */
   @Override
   public synchronized void reset() {
      parameters = null;
      currSheet = 0;
      first = true;
      pagecnts = new int[sheets.length];
      invoke(resetMethod);
   }

   /**
    * Set the visibility of an element.
    * @param id element id.
    * @param vis true to show element and false to hide it.
    */
   @Override
   public void setVisible(String id, boolean vis) {
      findSheet(id).setVisible(id, vis);
   }

   /**
    * Get a property value.
    * @param name property name.
    * @return property value.
    */
   @Override
   public String getProperty(String name) {
      for(int i = 0; sheets != null && i < sheets.length; i++) {
         String prop = sheets[i].getProperty(name);

         if(prop != null) {
            return prop;
         }
      }

      return null;
   }

   /**
    * Set a property. Properties are attributes in a report template
    * that can be used to store any arbitrary information. It is often
    * used by visual designers on configuration information. The properties
    * may serve as hints on how report is presented at runtime. The
    * currently recognized runtime properties are:<p><pre>
    * PageSize - a string in the form of: WIDTHxHEIGHT, e.g. 8.5x11. Or
    * a string value of a Size object or constant.
    * Orientation - a string of either 'Landscape', 'Portrait', or the
    * string value of the orientation option.
    * <pre>
    * The hints may or may not be honored depending on the platform. For
    * example, page size and orientation can not be changed programmatically
    * on JDK1.1, so the hints are ignored and the user has to set those
    * options on the printer dialog.
    * @param name property name.
    * @param val property value.
    */
   @Override
   public void setProperty(String name, String val) {
      for(int i = 0; i < getReportCount(); i++) {
         getReport(i).setProperty(name, val);
      }
   }

   private void invoke(Method method, Object ... params) {
      for(int i = 0; i < sheets.length; i++) {
         try {
            method.invoke(sheets[i], params);
         }
         catch(Exception e) {
            LOG.warn("Failed to invoke method on sheet: " +
               method.getName() + ", parameters=" + Arrays.toString(params), e);
         }
      }
   }

   /**
    * Find the style sheet with the specified element.
    */
   private ReportSheet findSheet(String id) {
      for(int i = 0; i < sheets.length; i++) {
         ReportElement elem = sheets[i].getElement(id);

         if(elem != null) {
            return sheets[i];
         }
      }

      return lastS;
   }

   /**
    * Set the page index where the page numbering starts.
    * @param idx the page index where the page numbering starts. The
    * page would be page one.
    */
   @Override
   public void setPageNumberingStart(int idx) {
      if(contiguous) {
         sheets[0].setPageNumberingStart(idx);
         sheets[0].setPageTotalStart(0 - idx);
      }
   }

   /**
    * Remove the specified element.
    * @param id element id in string format.
    */
   @Override
   public void removeElement(String id) {
      parameters = null;
      ReportSheet sheet = findSheet(id);

      if(sheet != null) {
         sheet.removeElement(id);
      }
   }

   /**
    * Replace the specified element.
    * @param id element id in string format.
    */
   @Override
   public void replaceElement(String id, ReportElement e) {
      parameters = null;
      ReportSheet sheet = findSheet(id);

      if(sheet != null) {
         sheet.replaceElement(id, e);
      }
   }

   /**
    * Move element up or down.
    * @param id element id in string format.
    * @param direction move direction, can be one of ReportSheet.UP or
    * ReportSheet.DOWN.
    */
   @Override
   public void moveElement(String id, int direction) {
      ReportSheet sheet = findSheet(id);

      if(sheet != null) {
         sheet.moveElement(id, direction);
      }
   }

   ReportSheet[] sheets;
   ReportSheet lastS; // last ReportSheet, for convenience
   int currSheet = 0; // current sheet
   int[] pagecnts; // number of pages for each sheet
   boolean first = true; // true when printing the first page
   boolean contiguous = true; // true when page numbers are contiguous
   static Method marginMethod;
   static Method headerFromEdgeMethod;
   static Method footerFromEdgeMethod;
   // set current method
   static Method alignmentMethod;
   static Method indentMethod;
   static Method tabStopsMethod;
   static Method wrappingMethod;
   static Method lineSpacingMethod;
   static Method fontMethod;
   static Method foregroundMethod;
   static Method backgroundMethod;
   static Method tableLayoutMethod;
   static Method painterLayoutMethod;
   static Method painterMarginMethod;
   static Method cellPaddingMethod;
   static Method tableWidthMethod;
   static Method headerMethod;
   static Method footerMethod;
   static Method justifyMethod;
   static Method textAdvanceMethod;
   static Method orphanControlMethod;
   static Method tableOrphanControlMethod;
   static Method addPresenterMethod;
   static Method removePresenterMethod;
   static Method clearPresenterMethod;
   static Method addFormatMethod;
   static Method removeFormatMethod;
   static Method clearFormatMethod;
   static Method clearMethod;
   static Method resetMethod;

   private static final Logger LOG =
      LoggerFactory.getLogger(CompositeSheet.class);

   static {
      try {
         marginMethod = ReportSheet.class.getMethod("setMargin",
                                                    Margin.class);
         headerFromEdgeMethod = ReportSheet.class.getMethod("setHeaderFromEdge",
                                                            double.class);
         footerFromEdgeMethod = ReportSheet.class.getMethod("setFooterFromEdge",
                                                            double.class);
         alignmentMethod = ReportSheet.class.getMethod("setCurrentAlignment",
                                                       int.class);
         indentMethod = ReportSheet.class.getMethod("setCurrentIndent",
                                                    double.class);
         tabStopsMethod = ReportSheet.class.getMethod("setCurrentTabStops",
                                                      double[].class);
         wrappingMethod = ReportSheet.class.getMethod("setCurrentWrapping",
                                                      int.class);
         lineSpacingMethod = ReportSheet.class.getMethod(
            "setCurrentLineSpacing", int.class);
         fontMethod = ReportSheet.class.getMethod("setCurrentFont",
                                                  Font.class);
         foregroundMethod = ReportSheet.class.getMethod(
            "setCurrentForeground", Color.class);
         backgroundMethod = ReportSheet.class.getMethod("setCurrentBackground",
                                                        Color.class);
         tableLayoutMethod = ReportSheet.class.getMethod(
            "setCurrentTableLayout", int.class);
         painterLayoutMethod = ReportSheet.class.getMethod(
            "setCurrentPainterLayout", int.class);
         painterMarginMethod = ReportSheet.class.getMethod(
            "setCurrentPainterMargin", Insets.class);
         cellPaddingMethod = ReportSheet.class.getMethod(
            "setCurrentCellPadding", Insets.class);
         tableWidthMethod = ReportSheet.class.getMethod("setCurrentTableWidth",
                                                        double.class);
         headerMethod = ReportSheet.class.getMethod("setCurrentHeader",
                                                    int.class);
         footerMethod = ReportSheet.class.getMethod("setCurrentFooter",
                                                    int.class);
         justifyMethod = ReportSheet.class.getMethod("setCurrentJustify",
                                                     boolean.class);
         textAdvanceMethod = ReportSheet.class.getMethod(
            "setCurrentTextAdvance", int.class);
         orphanControlMethod = ReportSheet.class.getMethod(
            "setCurrentOrphanControl", boolean.class);
         tableOrphanControlMethod = ReportSheet.class.getMethod(
            "setCurrentTableOrphanControl", boolean.class);
         addPresenterMethod = ReportSheet.class.getMethod("addPresenter",
                                                          Class.class, Presenter.class);
         removePresenterMethod = ReportSheet.class.getMethod("removePresenter",
                                                             Class.class);
         clearPresenterMethod = ReportSheet.class.getMethod("clearPresenter"
         );
         addFormatMethod = ReportSheet.class.getMethod("addFormat",
                                                       Class.class, Format.class);
         removeFormatMethod = ReportSheet.class.getMethod("removeFormat",
                                                          Class.class);
         clearFormatMethod = ReportSheet.class.getMethod("clearFormat"
         );
         clearMethod = ReportSheet.class.getMethod("clear");
         resetMethod = ReportSheet.class.getMethod("reset");
      }
      catch(Exception e) {
         LOG.warn("Failed to initialize methods", e);
      }
   }
}

