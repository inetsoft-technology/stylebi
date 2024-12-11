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
package inetsoft.report;

import inetsoft.graph.data.DataSet;
import inetsoft.report.internal.*;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.painter.ImagePainter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URL;
import java.text.*;
import java.util.*;

/**
 * ReportSheet is the main class for report generation. Each report is
 * represented as a ReportSheet object. Conceptually, a ReportSheet is a
 * runtime report template that layout the formatting of a report. The
 * actuall data in a concrete report can either be embedded in the
 * report or be supplied with a lens object (e.g. TableLens, ChartLens...).
 * <p>
 * Creating a report involved a series of insertions of report elements
 * into the report. During this process, a set of global report
 * attributes can be set and consequently being adopted by the report
 * elements inserted thereafter.
 * <p>
 * There are two types of properties in the ReportSheet. One is the regular
 * property that affect all elements and settings. The other is the
 * property that affect all elements added after the property is set.
 * Any elements added before the call to set property are not affected
 * by the new setting. The second group properties are always set by
 * a method starts with 'setCurrent'.
 * <p>
 * There are two types of reports: StyleSheet and TabularSheet. ReportSheet
 * defines the common API between this two types of reports. StyleSheet
 * provides a purely flow based layout model. TabularSheet provides a
 * grid based layout model. A report is divided into a tabular grid, and
 * layout is done per grid cell.
 * <p>
 * Please refer to the Style Report Programming Guide for more details
 * on the concepts and features of the ReportSheet.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public abstract class ReportSheet extends StyleCore {
   /**
    * Table layout policy. Size column width to fit contents. Allow
    * table rows to wrap.
    */
   public static final int TABLE_FIT_CONTENT = 0;

   /**
    * Table layout policy. Fit the table width to page width.
    */
   public static final int TABLE_FIT_PAGE = 1;
   /**
    * Table layout policy. Table with equal width columns and fit in
    * one page.
    */
   public static final int TABLE_EQUAL_WIDTH = 2;
   /**
    * Table layout policy. Size column width to fit contents. If the
    * table rows wrap, print one table segment per page.
    */
   public static final int TABLE_FIT_CONTENT_1PP = 3;
   /**
    * Table layout policy. Size column width to fit the contents just
    * like TABLE_FIT_CONTENT. Each table segment is then sized (by
    * adjusting the last column) to fit the page width.
    */
   public static final int TABLE_FIT_CONTENT_PAGE = 4;
   /**
    * Painter layout policy. Always paint the painter in one area.
    */
   public static final int PAINTER_NON_BREAK = 0;
   /**
    * Painter layout policy. Allow a painter to be printed across pages.
    */
   public static final int PAINTER_BREAKABLE = 1;
   /**
    * Flag to indicate the default page header. This flag is used to
    * change the currently worked header. All subsequent call to
    * addHeader???() methods add the elements to the default header.
    */
   public static final int DEFAULT_HEADER = 0x100;
   /**
    * Flag to indicate the first page header. This flag is used to
    * change the currently worked header. All subsequent call to
    * addHeader???() methods add the elements to the first page header.
    */
   public static final int FIRST_PAGE_HEADER = DEFAULT_HEADER + 1;
   /**
    * Flag to indicate the even page header. This flag is used to
    * change the currently worked header. All subsequent call to
    * addHeader???() methods add the elements to the even page header.
    */
   public static final int EVEN_PAGE_HEADER = DEFAULT_HEADER + 2;
   /**
    * Flag to indicate the odd page header. This flag is used to
    * change the currently worked header. All subsequent call to
    * addHeader???() methods add the elements to the odd page header.
    */
   public static final int ODD_PAGE_HEADER = DEFAULT_HEADER + 3;
   /**
    * Flag to indicate the default page footer. This flag is used to
    * change the currently worked footer. All subsequent call to
    * addFooter???() methods add the elements to the default footer.
    */
   public static final int DEFAULT_FOOTER = 0x200;
   /**
    * Flag to indicate the first page footer. This flag is used to
    * change the currently worked footer. All subsequent call to
    * addFooter???() methods add the elements to the first page footer.
    */
   public static final int FIRST_PAGE_FOOTER = DEFAULT_FOOTER + 1;
   /**
    * Flag to indicate the even page footer. This flag is used to
    * change the currently worked footer. All subsequent call to
    * addFooter???() methods add the elements to the even page footer.
    */
   public static final int EVEN_PAGE_FOOTER = DEFAULT_FOOTER + 2;
   /**
    * Flag to indicate the odd page footer. This flag is used to
    * change the currently worked footer. All subsequent call to
    * addFooter???() methods add the elements to the odd page footer.
    */
   public static final int ODD_PAGE_FOOTER = DEFAULT_FOOTER + 3;
   /**
    * Flag to indicate the main report body section.
    */
   public static final int BODY = 0;
   /**
    * No wrapping. Text flows on top of element.
    */
   public static final int WRAP_NONE = 0;
   /**
    * Wrapping at the left of the element.
    */
   public static final int WRAP_LEFT = 1;
   /**
    * Wrapping at the right of the element.
    */
   public static final int WRAP_RIGHT = 2;
   /**
    * Wrapping at both left and right.
    */
   public static final int WRAP_BOTH = WRAP_LEFT | WRAP_RIGHT;
   /**
    * ReportElement occupies whole row. No text is allowed at left or right.
    */
   public static final int WRAP_TOP_BOTTOM = 0x100;
   /**
    * Flag to indictate element moving up direction.
    */
   public static final int UP = 0x01;
   /**
    * Flag to indicate element moving down direction.
    */
   public static final int DOWN = 0x10;
   /**
    * Flag to indicate the visual format brush action.
    */
   public static final int BRUSH_VISUAL_FORMAT = 0x100;
   /**
    * Flag to indicate the value format brush action.
    */
   public static final int BRUSH_VALUE_FORMAT = BRUSH_VISUAL_FORMAT + 1;
   /**
    * Flag to indicate the highlight brush action.
    */
   public static final int BRUSH_HIGHLIGHT = BRUSH_VISUAL_FORMAT + 2;

   /**
    * Get the locale used in this report.
    */
   public Locale getLocale() {
      return locale;
   }

   /**
    * Set the locale used in this report. If locale is not set, the default
    * locale of the operating system is used.
    */
   public void setLocale(Locale locale) {
      this.locale = locale;
      refreshFormats();
   }

   /**
    * Get the background layout of the page.
    * @return background layout.
    */
   public int getBackgroundLayout() {
      return bglayout;
   }

   /**
    * Set the background layout of the page.
    * for the int layout parameter, use
    * <code>StyleConstants</code>
    * StyleConstants.BACKGROUND_TILED or
    * StyleConstants.BACKGROUND_CENTER
    * @param layout background layout.
    */
   public void setBackgroundLayout(int layout) {
      bglayout = layout;
   }

   /**
    * Get the background image size.
    * @return background image size.
    */
   public Dimension getBackgroundSize() {
      return bgsize;
   }

   /**
    * Set the background image size.
    * @param d background imae size.
    */
   public void setBackgroundSize(Dimension d) {
      bgsize = d;
   }

   /**
    * Set the background size of the page.
    * @param width background width.
    * @param height background height.
    */
   public void setBackgroundSize(int width, int height) {
      setBackgroundSize(new Dimension(width, height));
   }

   /**
    * Get the number of parameters defined in this report. The runtime
    * parameters of a report is the union of the parameters defined
    * in the report, and the parameters defined in the queries used
    * in the report. If a parameter is defined in both places, the
    * definition in the report is used.
    * @return number of parameters defined in the report.
    */
   public int getParameterCount() {
      return parameters.size();
   }

   /**
    * Get the report parmaters.
    */
   public Vector<UserVariable> getParameters() {
      return parameters;
   }

   /**
    * Get a report parameter definition.
    * @param idx parameter index.
    * @return parameter definition.
    */
   public UserVariable getParameter(int idx) {
      return parameters.get(idx);
   }

   /**
    * Add a new parameter to the report. If a parameter with the same name
    * already exists, the existing definition is replaced by the name
    * parameter definition.
    */
   public void addParameter(UserVariable param) {
      for(int i = 0; i < getParameterCount(); i++) {
         if(getParameter(i).getName().equals(param.getName())) {
            parameters.setElementAt(param, i);
            return;
         }
      }

      parameters.addElement(param);
   }

   /**
    * Add a new parameter to the report. If a parameter with the same name
    * already exists, the existing definition is replaced by the name
    * parameter definition.
    */
   public void addParameter(int index, UserVariable param) {
      for(int i = 0; i < getParameterCount(); i++) {
         if(getParameter(i).getName().equals(param.getName())) {
            parameters.setElementAt(param, i);
            return;
         }
      }

      parameters.add(index, param);
   }

   /**
    * Removes all the parameters from the report parameter list.
    */
   public void removeAllParameters() {
      parameters.clear();
   }

   /**
    * Remove a parameter from the report parameter list.
    */
   public void removeParameter(int idx) {
      parameters.removeElementAt(idx);
   }

   /**
    * Set the page margin. The unit is in inches.
    * @param margin page margin.
    */
   public void setMargin(Margin margin) {
      this.margin = margin;
   }

   /**
    * Get the page margin in inches.
    * @return page margin.
    */
   public Margin getMargin() {
      return margin;
   }

   /**
    * Get the report background. Returns the user value or the css value if the
    * user value is not set.
    */
   public Object getBackground() {
      if(bg != null) {
         return bg;
      }

      return cssBackgroundColor;
   }

   /**
    * Get the report background.
    */
   public Object getUserBackground() {
      return bg;
   }

   /**
    * Set the background of this report. The background can be either a
    * Color or an Image object.
    * @param bg report background.
    */
   public void setUserBackground(Object bg) {
      this.bg = bg;
   }

   public Color getCSSBackgroundColor() {
      return cssBackgroundColor;
   }

   public void setCSSBackgroundColor(Color cssBackgroundColor) {
      this.cssBackgroundColor = cssBackgroundColor;
   }

   /**
    * Set the page header position from the top of the page. The unit
    * is in inches.
    * @param inch header position.
    */
   public void setHeaderFromEdge(double inch) {
      headerFromEdge = inch;
   }

   /**
    * Get the page header position from the top of the page. The unit
    * is in inches.
    * @return header position.
    */
   public double getHeaderFromEdge() {
      return headerFromEdge;
   }

   /**
    * Set the page footer position from the bottom of the page.
    * The unit is in inches.
    * @param inch footer position.
    */
   public void setFooterFromEdge(double inch) {
      footerFromEdge = inch;
   }

   /**
    * Get the page footer position from the bottom of the page.
    * The unit is in inches.
    * @return footer position.
    */
   public double getFooterFromEdge() {
      return footerFromEdge;
   }

   /**
    * Get the page header bounds.
    * @param pgsize page size in pixels.
    * @return header region.
    */
   public Rectangle getHeaderBounds(Dimension pgsize) {
      Margin margin = getTopReport().cmargin;
      Margin pmargin = getTopReport().pmargin;
      double headerFromEdge = getTopReport().headerFromEdge;

      return new Rectangle((int) ((margin.left - pmargin.left) * 72),
         (int) ((headerFromEdge - pmargin.top) * 72),
         pgsize.width - (int) ((margin.left + margin.right) * 72),
         (int) ((margin.top - headerFromEdge) * 72));
   }

   /**
    * Get the page footer bounds.
    * @param pgsize page size in pixels.
    * @return footer region.
    */
   public Rectangle getFooterBounds(Dimension pgsize) {
      Margin margin = getTopReport().cmargin;
      Margin pmargin = getTopReport().pmargin;
      double footerFromEdge = getTopReport().footerFromEdge;

      return new Rectangle((int) ((margin.left - pmargin.left) * 72),
         pgsize.height - (int) (footerFromEdge * 72),
         pgsize.width - (int) ((margin.left + margin.right) * 72),
         (int) (footerFromEdge * 72));
   }

   /**
    * Get the margin of the current page in inches.
    * @return page margin.
    */
   public Margin getCurrentMargin() {
      return cmargin;
   }

   /**
    * Set the unit used to convert the measurements
    * @param unit unit string
    */
   public void setUnit(String unit) {
      this.unit = unit;
   }

   /**
    * Get the unit used to convert the measurements
    * @return unit string
    */
   public String getUnit() {
      return unit;
   }

   /**
    * Check whether is custom page size.
    */
   public boolean isCustomPageSize() {
      return customPageSize;
   }

   /**
    * Set whether is custom page size.
    */
   public void setCustomPageSize(boolean customPageSize) {
      this.customPageSize = customPageSize;
   }

   /**
    * Set the page index where the page numbering starts.
    * @param idx the page index where the page numbering starts. The
    * page would be page one.
    */
   public void setPageNumberingStart(int idx) {
      pgStart = idx;
      pgTotal = -idx;

      // set in the counter so the new setting would take effect
      if(hfFmt != null) {
         hfFmt.setPageNumberingStart(idx);
      }
   }

   /**
    * Get the page numbering start index.
    * @return page index of the first page.
    */
   public int getPageNumberingStart() {
      return pgStart;
   }

   /**
    * Set the flow direction to horizontal. This controls how text wraps
    * around an anchored painter. If the direction is horizontal, the
    * text lines are flow left to right, on both sides of the image,
    * and then downward. If the horizontal flow is false, the text flows
    * in the left size of the image first, then advance to the right
    * side of the image after the left size area is exhausted.
    */
   public void setHorizontalWrap(boolean hor) {
      horFlow = hor;
   }

   /**
    * Check the horizontal flow option.
    */
   public boolean isHorizontalWrap() {
      return horFlow;
   }

   /**
    * Save the current report context in the context repository. If the
    * named context already exists, it is replaced with the current
    * context values. A context contains all values set with the
    * setCurrent...() methods.
    * @param name context name.
    */
   public void saveContext(String name) {
      contexts.put(name, new Context(this));
   }

   /**
    * Retrieve the context values from the named context and set the
    * values in the report.
    * @param name context name.
    */
   public void selectContext(String name) {
      Context ct = (Context) contexts.get(name);

      if(ct != null) {
         ct.restore();
      }
   }

   /**
    * Removed the named context from the repository.
    * @param name context name.
    */
   public void removeContext(String name) {
      contexts.remove(name);
   }

   /**
    * This following attributes are used only when adding new elements
    * to the sheet. They are effective at the time of the setting call,
    * and are used by all elements added after the call, until the
    * next setter on the attribute is called.
    */

   /**
    * Set the current alignment of the report elements. The alignment
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param align alignment flag, a bitwise OR of the H_LEFT, H_CENTER,
    * H_RIGHT, and V_TOP, V_CENTER, V_BOTTOM.
    */
   public void setCurrentAlignment(int align) {
      alignment = align;
   }

   /**
    * Get the current setting of the alignment.
    * @return alignment flag.
    */
   public int getCurrentAlignment() {
      return alignment;
   }

   /**
    * Set the current indentation level. The value is in inches. The indent
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param inch indentation size.
    */
   public void setCurrentIndent(double inch) {
      indent = inch;
   }

   /**
    * Get the current indentation in inches.
    * @return indentation.
    */
   public double getCurrentIndent() {
      return indent;
   }

   /**
    * Set the tab stops.
    * @param pos tab stops in inches.
    */
   public void setCurrentTabStops(double[] pos) {
      tabStops = pos;
   }

   /**
    * Return the current tab stop setting.
    * @return tab stop positions in inches.
    */
   public double[] getCurrentTabStops() {
      return tabStops;
   }

   /**
    * Set the current wrapping style. This affects how the text around
    * a Painter/Image/TextBox is wrapped.
    * @param wrapping one of the WRAP_NONE, WRAP_LEFT, WRAP_RIGHT,
    * WRAP_BOTH, and WRAP_TOP_BOTTOM.
    */
   public void setCurrentWrapping(int wrapping) {
      this.wrapping = wrapping;
   }

   /**
    * Get the current wrapping style.
    * @return wrapping option.
    */
   public int getCurrentWrapping() {
      return wrapping;
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
   public void moveAnchor(Position anchor) {
      this.anchor = anchor;
   }

   /**
    * Set the current line spacing in pixels. The line spacing is the space
    * between the two lines. The line spacing parameter
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param spacing line spacing.
    */
   public void setCurrentLineSpacing(int spacing) {
      this.spacing = spacing;
   }

   /**
    * Get the current line spacing setting.
    * @return line spacing.
    */
   public int getCurrentLineSpacing() {
      return spacing;
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
   public void setCurrentFont(Font font) {
      this.font = font;
   }

   /**
    * Get the current font setting.
    * @return current document font.
    */
   public Font getCurrentFont() {
      return font;
   }

   /**
    * Set the current document foreground color. The foreground color
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param fg foreground color.
    */
   public void setCurrentForeground(Color fg) {
      this.foreground = fg;
   }

   /**
    * Get the current document foreground color.
    * @return foreground color.
    */
   public Color getCurrentForeground() {
      return foreground;
   }

   /**
    * Set the current document background color. The background color
    * is effective after the call, and will be used by all elements added
    * after the call upto the next call of this method.
    * @param bg background color.
    */
   public void setCurrentBackground(Color bg) {
      this.background = bg;
   }

   /**
    * Get the current document background color.
    * @return background color.
    */
   public Color getCurrentBackground() {
      return background;
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
   public void setCurrentTableLayout(int autosize) {
      this.autosize = autosize;
   }

   /**
    * Get the current table layout mode.
    * @return layout mode.
    */
   public int getCurrentTableLayout() {
      return autosize;
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
   public void setCurrentPainterLayout(int policy) {
      painterLayout = policy;
   }

   /**
    * Get the current painter layout policy.
    * @return painter layout policy.
    */
   public int getCurrentPainterLayout() {
      return painterLayout;
   }

   /**
    * Set the space around the painter elements. This controls the painter,
    * text box, and chart elements' external spacing.
    * @param margin painter external space.
    */
   public void setCurrentPainterMargin(Insets margin) {
      painterMargin = margin;
   }

   /**
    * Get the fixed size element external space.
    * @return space around painter elements.
    */
   public Insets getCurrentPainterMargin() {
      return painterMargin;
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
   public void setCurrentCellPadding(Insets padding) {
      this.padding = padding;
   }

   /**
    * Get the current table cell padding.
    * @return cell padding setting.
    */
   public Insets getCurrentCellPadding() {
      return padding;
   }

   /**
    * Set the width of the table in inches. This parameter is only used
    * if the table layout is set to TABLE_FIT_PAGE or TABLE_EQUAL_WIDTH.
    * @param inch table width.
    */
   public void setCurrentTableWidth(double inch) {
      tableW = inch;
   }

   /**
    * Get the current table width setting.
    * @return table width.
    */
   public double getCurrentTableWidth() {
      return tableW;
   }

   /**
    * If justify is set to true, text lines are fully justified.
    * @param justify text justification.
    */
   public void setCurrentJustify(boolean justify) {
      this.justify = justify;
   }

   /**
    * Check if text is justified.
    * @return justification setting.
    */
   public boolean isCurrentJustify() {
      return justify;
   }

   /**
    * Set the amount to advance following each text element. The default
    * advance is 3 pixels.
    * @param textadv text element advance pixels.
    */
   public void setCurrentTextAdvance(int textadv) {
      this.textadv = textadv;
   }

   /**
    * Get the advance of text elements.
    * @return text advance in pixels.
    */
   public int getCurrentTextAdvance() {
      return textadv;
   }

   /**
    * Set the amount to advance below a separator element. The default
    * is 4 pixels.
    * @param adv advance in pixels.
    */
   public void setCurrentSeparatorAdvance(int adv) {
      sepadv = adv;
   }

   /**
    * Get the separator vertical trailing advance.
    * @return advance distance in pixels.
    */
   public int getCurrentSeparatorAdvance() {
      return sepadv;
   }

   /**
    * Set the table widow/orphan control option.
    * @param orphan true to eliminate widow/orphan rows.
    */
   public void setCurrentTableOrphanControl(boolean orphan) {
      this.tableorphan = orphan;
   }

   /**
    * Check the current widow/orphan control setting.
    * @return widow/orphan control option.
    */
   public boolean isCurrentTableOrphanControl() {
      return tableorphan;
   }

   /**
    * Set the widow/orphan line control option.
    * @param orphan true to eliminate widow/orphan lines.
    */
   public void setCurrentOrphanControl(boolean orphan) {
      this.orphan = orphan;
   }

   /**
    * Check the current widow/orphan control setting.
    * @return widow/orphan control option.
    */
   public boolean isCurrentOrphanControl() {
      return orphan;
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
   public void addPresenter(Class type, Presenter p) {
      presentermap.put(type, p);
   }

   /**
    * Get the presenter object registered for this class or one of it's
    * super classes.
    * @param type class to search for.
    * @return the presenter for this object.
    */
   public Presenter getPresenter(Class type) {
      return getPresenter(presentermap, type);
   }

   /**
    * Remove the specified presenter from the registry. Objects added
    * before the call are not affected.
    * @param type object type.
    */
   public void removePresenter(Class type) {
      presentermap.remove(type);
   }

   /**
    * Clear the presenter registry.
    */
   public void clearPresenter() {
      presentermap.clear();
   }

   /**
    * Register a format for the specified class. The format is
    * used to convert an object to a string for all values of the
    * specified type.
    * @param type type of the values to present.
    * @param p format object.
    */
   public void addFormat(Class type, Format p) {
      formatmap.put(type, p);
   }

   /**
    * Get the format object registered for this class or one of it's
    * super classes.
    * @param type class to search for.
    * @return the format for this object.
    */
   @Override
   public Format getFormat(Class type) {
      return super.getFormat(type);
   }

   /**
    * Remove the specified format from the registry. Objects added
    * before the call are not affected.
    * @param type object type.
    */
   public void removeFormat(Class type) {
      formatmap.remove(type);
   }

   /**
    * Clear the format registry.
    */
   public void clearFormat() {
      formatmap.clear();
   }

   /**
    * Header and footer options.
    */

   /**
    * Set the currently worked on header. All subsequent calls to
    * addHeader???() methods will add the element to the current
    * header. The headers can be either DEFAULT_HEADER, FIRST_PAGE_HEADER,
    * EVEN_PAGE_HEADER, or ODD_PAGE_HEADER.
    * @param hflag header flag.
    */
   public void setCurrentHeader(int hflag) {
      Vector currHeader = null;

      switch(hflag) {
      case DEFAULT_HEADER:
         currHeader = headerElements;
         break;
      case FIRST_PAGE_HEADER:
         currHeader = (firstHeader == null) ?
            (firstHeader = new Vector()) :
            firstHeader;
         break;
      case EVEN_PAGE_HEADER:
         currHeader = (evenHeader == null) ?
            (evenHeader = new Vector()) :
            evenHeader;
         break;
      case ODD_PAGE_HEADER:
         currHeader = (oddHeader == null) ?
            (oddHeader = new Vector()) :
            oddHeader;
         break;
      }

      if(currHeader != null) {
         this.currHeader.setElements(currHeader);
         this.currHeader.setSelected(hflag);
      }
   }

   /**
    * Set the currently worked on footer. All subsequent calls to
    * addFooter???() methods will add the element to the current
    * footer. The footers can be either DEFAULT_FOOTER, FIRST_PAGE_FOOTER,
    * EVEN_PAGE_FOOTER, or ODD_PAGE_FOOTER.
    * @param hflag footer flag.
    */
   public void setCurrentFooter(int hflag) {
      Vector currFooter = null;

      switch(hflag) {
      case DEFAULT_FOOTER:
         currFooter = footerElements;
         break;
      case FIRST_PAGE_FOOTER:
         currFooter = (firstFooter == null) ?
            (firstFooter = new Vector()) :
            firstFooter;
         break;
      case EVEN_PAGE_FOOTER:
         currFooter = (evenFooter == null) ?
            (evenFooter = new Vector()) :
            evenFooter;
         break;
      case ODD_PAGE_FOOTER:
         currFooter = (oddFooter == null) ?
            (oddFooter = new Vector()) :
            oddFooter;
         break;
      }

      if(currFooter != null) {
         this.currFooter.setElements(currFooter);
         this.currFooter.setSelected(hflag);
      }
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
   public String addHeaderObject(Object obj) {
      Presenter presenter = (Presenter) presentermap.get(obj.getClass());
      String id = null;

      if(presenter != null) {
         id = addHeaderElement(new PainterElementDef(this,
            new PresenterPainter(obj, presenter)));
      }
      else {
         id = addHeaderText(toString(obj));
      }

      return id;
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
   public String addHeaderText(String text) {
      return addHeaderText(new DefaultTextLens(text));
   }

   /**
    * Add a text element to the document header. The contents of the
    * TextLens is processed in the same way as the addHeaderText(String)
    * parameter.
    * @param text text content lens.
    * @return element id.
    */
   public String addHeaderText(TextLens text) {
      return addHeaderElement(new TextElementDef(this, text));
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
   public String addHeaderTextBox(TextLens text) {
      return addHeaderElement(new TextBoxElementDef(this, text));
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
   public String addHeaderTextBox(TextLens text, int border, double winch,
      double hinch, int talign) {
      TextBoxElement box = new TextBoxElementDef(this, text, winch, hinch);

      box.setBorder(border);
      box.setTextAlignment(talign);
      return addHeaderElement(box);
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
   public String addHeaderPainter(Painter area) {
      if(area instanceof ScaledPainter) {
         Size size = ((ScaledPainter) area).getSize();

         return addHeaderPainter(area, size.width, size.height);
      }

      return addHeaderElement(new PainterElementDef(this, area));
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
   public String addHeaderPainter(Painter area, double winch, double hinch) {
      return addHeaderElement(new PainterElementDef(this, area, winch, hinch));
   }

   /**
    * Add an image to the document header.
    * @param image image object.
    * @return element id.
    */
   public String addHeaderImage(Image image) {
      return addHeaderPainter(new ImagePainter(image));
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
   public String addHeaderImage(Image image, double winch, double hinch) {
      return addHeaderPainter(new ImagePainter(image), winch, hinch);
   }

   /**
    * Add an image to the document header.
    * @param image image URL.
    * @return element id.
    */
   public String addHeaderImage(URL image) {
      try {
         return addHeaderImage(Tool.getImage(image.openStream()));
      }
      catch(Exception e) {
         LOG.warn("Failed to add image to document header: " + image, e);
      }

      return null;
   }

   /**
    * Add horizontal space to the document header. The space is added after
    * the current element.
    * @param pixels space in pixels.
    * @return element id.
    */
   public String addHeaderSpace(int pixels) {
      return addHeaderElement(new SpaceElementDef(this, pixels));
   }

   /**
    * Add one or more newline to the document header.
    * @param n number of newline.
    * @return element id.
    */
   public String addHeaderNewline(int n) {
      return addHeaderElement(new NewlineElementDef(this, n, false));
   }

   /**
    * Add a break to the document header.
    * @return element id.
    */
   public String addHeaderBreak() {
      return addHeaderElement(new NewlineElementDef(this, 1, true));
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
   public String addFooterObject(Object obj) {
      Presenter presenter = (Presenter) presentermap.get(obj.getClass());
      String id = null;

      if(presenter != null) {
         id = addFooterElement(new PainterElementDef(this,
            new PresenterPainter(obj, presenter)));
      }
      else {
         id = addFooterText(toString(obj));
      }

      return id;
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
   public String addFooterText(String text) {
      return addFooterText(new DefaultTextLens(text));
   }

   /**
    * Add a text element to the document footer. The contents of the
    * TextLens is processed in the same way as the addFooterText(String)
    * parameter.
    * @param text text content lens.
    * @return element id.
    */
   public String addFooterText(TextLens text) {
      return addFooterElement(new TextElementDef(this, text));
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
   public String addFooterTextBox(TextLens text) {
      return addFooterElement(new TextBoxElementDef(this, text));
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
   public String addFooterTextBox(TextLens text, int border, double winch,
      double hinch, int talign) {
      TextBoxElement box = new TextBoxElementDef(this, text, winch, hinch);

      box.setBorder(border);
      box.setTextAlignment(talign);
      return addFooterElement(box);
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
   public String addFooterPainter(Painter area) {
      if(area instanceof ScaledPainter) {
         Size size = ((ScaledPainter) area).getSize();

         return addFooterPainter(area, size.width, size.height);
      }

      return addFooterElement(new PainterElementDef(this, area));
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
   public String addFooterPainter(Painter area, double winch, double hinch) {
      return addFooterElement(new PainterElementDef(this, area, winch, hinch));
   }

   /**
    * Add an image to the document footer.
    * @param image image object.
    * @return element id.
    */
   public String addFooterImage(Image image) {
      return addFooterPainter(new ImagePainter(image));
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
   public String addFooterImage(Image image, double winch, double hinch) {
      return addFooterPainter(new ImagePainter(image), winch, hinch);
   }

   /**
    * Add an image to the document footer.
    * @param image image URL.
    * @return element id.
    */
   public String addFooterImage(URL image) {
      try {
         return addFooterImage(Tool.getImage(image.openStream()));
      }
      catch(Exception e) {
         LOG.warn("Failed to add image to document footer: " + image, e);
      }

      return null;
   }

   /**
    * Add horizontal space to the document footer. The space is added after
    * the current element.
    * @param pixels space in pixels.
    * @return element id.
    */
   public String addFooterSpace(int pixels) {
      return addFooterElement(new SpaceElementDef(this, pixels));
   }

   /**
    * Add one or more newline to the document footer.
    * @param n number of newline.
    * @return element id.
    */
   public String addFooterNewline(int n) {
      return addFooterElement(new NewlineElementDef(this, n, false));
   }

   /**
    * Add a break to the document footer.
    * @return element id.
    */
   public String addFooterBreak() {
      return addFooterElement(new NewlineElementDef(this, 1, true));
   }

   /**
    * Find the index of the specified element. An element is identified
    * by an unique ID. Null IDs are ignored.
    * @param id element ID.
    * @return element object or null if not found.
    */
   public ReportElement getElement(String id) {
      Vector elements = getAllElements();
      Vector[] vs = {elements, headerElements, footerElements, firstHeader,
         firstFooter, evenHeader, evenFooter, oddHeader, oddFooter};

      for(int i = 0; i < vs.length; i++) {
         // some vectors are not always initialized
         if(vs[i] == null) {
            continue;
         }

         for(int j = 0; j < vs[i].size(); j++) {
            ReportElement v = (ReportElement) vs[i].elementAt(j);

            if(v.getID() != null && v.getID().equals(id)) {
               return v;
            }

            // check elements in sections
            if(v instanceof SectionElement) {
               ReportElement rc = Util.getElement((SectionElement) v, id);

               if(rc != null) {
                  return rc;
               }
            }
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
   public String addHeaderElement(ReportElement e) {
      ((BaseElement) e).setReport(this);
      currHeader.addElement(e);
      return e.getID();
   }

   /**
    * Return the number of elements in the document header.
    * @return number of elements.
    */
   public int getHeaderElementCount() {
      return currHeader.size();
   }

   /**
    * Return the number of the elements in the document header, include
    * the elements in a section which in the document header.
    * @return number of elements.
    */
   public int getAllHeaderElementCount() {
      int count = 0;
      BaseElement[] headerElements = new BaseElement[currHeader.size()];
      currHeader.toArray(headerElements);

      for(int i = 0; i < headerElements.length; i++) {
         count++;
         Enumeration elems = ElementIterator.elements(headerElements[i]);

         while(elems != null && elems.hasMoreElements()) {
            count++;
            elems.nextElement();
         }
      }

      return count;
   }

   /**
    * Get the specified element in the header.
    * @param idx element index.
    * @return document header element.
    */
   public ReportElement getHeaderElement(int idx) {
      return (ReportElement) currHeader.elementAt(idx);
   }

   /**
    * Get the index of the specified element.
    * @param e element.
    * @return element index.
    */
   public int getHeaderElementIndex(ReportElement e) {
      return currHeader.indexOf(e, 0);
   }

   /**
    * Remove the specified element.
    * @param idx element index.
    */
   public void removeHeaderElement(int idx) {
      synchronized(currHeader) {
         if(idx >= 0 && idx < currHeader.size()) {
            currHeader.removeElementAt(idx);
         }
      }
   }

   /**
    * Replace the specified element.
    * @param idx element index.
    */
   public void replaceHeaderElement(int idx, ReportElement e) {
      synchronized(currHeader) {
         if(idx >= 0 && idx < currHeader.size()) {
            currHeader.setElementAt(e, idx);
         }
      }
   }

   /**
    * Insert the element at specified position (before).
    * @param idx position to insert.
    * @param e element.
    */
   public void insertHeaderElement(int idx, ReportElement e) {
      synchronized(currHeader) {
         // @by henryh, allow inserting at last
         if(idx >= 0 && idx <= currHeader.size()) {
            currHeader.insertElementAt(e, idx);
         }
      }
   }

   /**
    * Add an element to the document footer. Classes extending the ReportSheet
    * can extend element classes from the Element, and use
    * this method for adding the element to the document.
    * @param e document element.
    * @return element id.
    */
   public String addFooterElement(ReportElement e) {
      ((BaseElement) e).setReport(this);
      currFooter.addElement(e);
      return e.getID();
   }

   /**
    * Return the number of elements in the document footer.
    * @return number of elements.
    */
   public int getFooterElementCount() {
      return currFooter.size();
   }

   /**
    * Return the number of the elements in the document footer, include
    * the elements in a section which in the document footer.
    * @return number of elements.
    */
   public int getAllFooterElementCount() {
      int count = 0;
      BaseElement[] footerElements = new BaseElement[currFooter.size()];
      currFooter.toArray(footerElements);

      for(int i = 0; i < footerElements.length; i++) {
         count++;
         Enumeration elems = ElementIterator.elements(footerElements[i]);

         while(elems != null && elems.hasMoreElements()) {
            count++;
            elems.nextElement();
         }
      }

      return count;
   }

   /**
    * Get the specified element in the footer.
    * @param idx element index.
    * @return document footer element.
    */
   public ReportElement getFooterElement(int idx) {
      return (ReportElement) currFooter.elementAt(idx);
   }

   /**
    * Get the index of the specified element.
    * @param e element.
    * @return element index.
    */
   public int getFooterElementIndex(ReportElement e) {
      return currFooter.indexOf(e, 0);
   }

   /**
    * Remove the specified element.
    * @param idx element index.
    */
   public void removeFooterElement(int idx) {
      synchronized(currFooter) {
         if(idx >= 0 && idx < currFooter.size()) {
            currFooter.removeElementAt(idx);
         }
      }
   }

   /**
    * Replace the specified element.
    * @param idx element index.
    */
   public void replaceFooterElement(int idx, ReportElement e) {
      synchronized(currFooter) {
         if(idx >= 0 && idx < currFooter.size()) {
            currFooter.setElementAt(e, idx);
         }
      }
   }

   /**
    * Insert the element at specified position (before).
    * @param idx position to insert.
    * @param e element.
    */
   public void insertFooterElement(int idx, ReportElement e) {
      synchronized(currFooter) {
         // @by henryh, allow inserting at last
         if(idx >= 0 && idx <= currFooter.size()) {
            currFooter.insertElementAt(e, idx);
         }
      }
   }

   /**
    * Move header or footer element up or down.
    * @param elem the element.
    * @param direction the moving direction. ReportSheet.UP or
    * ReportSheet.DOWN.
    */
   protected synchronized void moveHeaderFooterElement(
      ReportElement elem, int direction) {
      int idx = getHeaderElementIndex(elem);

      if(idx >= 0) {
         moveHeaderFooterElement(elem, true, direction);
      }
      else {
         idx = getFooterElementIndex(elem);
         if(idx >= 0) {
            moveHeaderFooterElement(elem, false, direction);
         }
      }
   }

   /**
    * Move header or footer element up or down.
    * @param elem the element.
    # @param header Flag to indicate if the elment is a header element.
    */
   private synchronized void moveHeaderFooterElement(
      ReportElement elem, boolean header, int direction) {
      int idx = header ?
         getHeaderElementIndex(elem) :
         getFooterElementIndex(elem);
      int oidx = idx;
      int count = (direction == UP) ? 0 :
         (header ? getHeaderElementCount() : getFooterElementCount());
      int dir = (direction == UP) ? 1 : -1;
      int delta = (direction == UP) ? 1 : 0;

      for(idx -= dir; (count + idx * dir) > 0; idx -= dir) {
         BaseElement em2 = header ?
            (BaseElement) getHeaderElement(idx) :
            (BaseElement) getFooterElement(idx);

         if(!em2.isFlowControl()) {
            break;
         }
      }

      if((count + idx * dir + delta) > 0) {
         if(header) {
            if(direction == UP) {
               removeHeaderElement(oidx);
               insertHeaderElement(idx, elem);
            }
            else {
               insertHeaderElement(idx + 1, elem);
               removeHeaderElement(oidx);
            }
         }
         else {
            if(direction == UP) {
               removeFooterElement(oidx);
               insertFooterElement(idx, elem);
            }
            else {
               insertFooterElement(idx + 1, elem);
               removeFooterElement(oidx);
            }
         }
      }
   }

   /**
    * Remove the specified header or footer element.
    * @param elem the element being removed.
    */
   protected synchronized void removeHeaderFooterElement(
      ReportElement elem) {
      int idx = getHeaderElementIndex(elem);

      if(idx >= 0) {
         removeHeaderElement(idx);
      }
      else {
         idx = getFooterElementIndex(elem);
         if(idx >= 0) {
            removeFooterElement(idx);
         }
      }
   }

   /**
    * Replace the specified header or footer element.
    * @param elem the element being removed.
    */
   protected synchronized void replaceHeaderFooterElement(
      ReportElement elem, ReportElement nelem)
   {
      int idx = getHeaderElementIndex(elem);

      if(idx >= 0) {
         replaceHeaderElement(idx, nelem);
      }
      else {
         idx = getFooterElementIndex(elem);
         if(idx >= 0) {
            replaceFooterElement(idx, nelem);
         }
      }
   }

   /**
    * Get the elements of the specified type. The type is one of the
    * header or footer types, or zero for the body.
    * @param type element type.
    * @return elements of specified type.
    */
   public Vector getElements(int type) {
      switch(type) {
      case DEFAULT_HEADER:
         return headerElements;
      case FIRST_PAGE_HEADER:
         return firstHeader;
      case EVEN_PAGE_HEADER:
         return evenHeader;
      case ODD_PAGE_HEADER:
         return oddHeader;
      case DEFAULT_FOOTER:
         return footerElements;
      case FIRST_PAGE_FOOTER:
         return firstFooter;
      case EVEN_PAGE_FOOTER:
         return evenFooter;
      case ODD_PAGE_FOOTER:
         return oddFooter;
      }

      return getAllElements();
   }

   /**
    * Get the max pages.
    * @return the max pages of this report sheet.
    */
   public int getMaxPages() {
      return getMaxPages(true);
   }
   /**
    * Get the max pages.
    * @param isUsedGlobal get global max pages if max pages is undefine.
    * @return the max pages of this report sheet.
    */
   public int getMaxPages(boolean isUsedGlobal) {
      String val = getProperty("max.pages");

      if(val == null || val.trim().length() == 0 || "0".equals(val)) {
         val = isUsedGlobal ?
            SreeEnv.getProperty("report.output.maxpages", "0") : "0";
      }

      return Integer.parseInt(val);
   }

   /**
    * Set the max pages.
    * @param pages the specified max pages.
    */
   public void setMaxPages(int pages) {
      setProperty("max.pages", pages + "");
   }

   /**
    * Methods to print pages.
    */

   /**
    * This method can be used the set the data source/content of elements
    * already in the report. Elements are identified with an unique ID. It's
    * the report creator's responsibility to make sure the IDs are unique
    * and the name exists (IDs are normally assigned in the Report Designer
    * GUI dialogs).
    * <p>
    * The acceptable type of data depends on the type of the element
    * identified by the ID. The following table lists the element type
    * and data types.
    * <p>
    * <table>
    * <tr>
    * <td>Table</td><td>TableLens, Object[][]</td>
    * </tr>
    * <tr>
    * <td>Section</td><td>TableLens, Object[][]</td>
    * </tr>
    * <tr>
    * <td>Form</td><td>FormLens</td>
    * </tr>
    * <tr>
    * <td>Chart</td><td>ChartLens, TableLens</td>
    * </tr>
    * <tr>
    * <td>Text, TextBox</td><td>String, TextLens, Object</td>
    * </tr>
    * <tr>
    * <td>Painter (or Image, Component)</td><td>Image, Component, Painter</td>
    * </tr>
    * <tr>
    * <td>Composite</td><td>ElementContainer</td>
    * </tr>
    * <tr>
    * <td>Button</td><td>String</td>
    * </tr>
    * <tr>
    * <td>CheckBox</td><td>String</td>
    * </tr>
    * <tr>
    * <td>Choice</td><td>Object or Object[]</td>
    * </tr>
    * <tr>
    * <td>TextField</td><td>String</td>
    * </tr>
    * <tr>
    * <td>TextArea</td><td>String</td>
    * </tr>
    * </table>
    * <p>
    * If an element is not found for the specified ID, a
    * NoSuchElementException if thrown. If the data type does not match the
    * element type, an IllegalArgumentException is thrown.
    * @param id element ID.
    * @param data element content/data source.
    */
   public void setElement(String id, Object data)
      throws NoSuchElementException, IllegalArgumentException
   {
      if(data instanceof ReportElement) {
         replaceElement(id, (ReportElement) data);
      }
      else {
         ReportElement elem = getElement(id);

         if(elem == null) {
            throw new NoSuchElementException(id);
         }

         setValue(elem, data);
      }
   }

   /**
    * replace a element.
    */
   protected abstract void replaceElement(String id, ReportElement elem);

   /**
    * Get the table lens object for the specified table. If the element
    * does not exist, or the element with the ID is not a table, a
    * NoSuchElementException is thrown.
    * @param id table element ID.
    * @return table lens object.
    */
   public TableLens getTable(String id) {
      ReportElement elem = getElement(id);

      if(elem == null || !(elem instanceof TableElement)) {
         throw new NoSuchElementException(id);
      }

      return ((TableElementDef) elem).getBaseTable();
   }

   /**
    * Get the chart of the specified chart element. The ID must
    * refer to an unique chart element created from a report template.
    * If the chart element is defined in a report template, the chart data
    * returned by this method is an Dataset, can be used to set
    * various chart attributes using the AttributeDataSet API.
    * @param id chart element ID.
    * @return chart data set.
    */
   public DataSet getDataSet(String id) {
      ReportElement elem = getElement(id);

      if(elem == null || !(elem instanceof ChartElement)) {
         throw new NoSuchElementException(id);
      }

      return ((ChartElement) elem).getDataSet();
   }

   /**
    * Get the text contents of a text or text box element.
    * @param id element ID.
    * @return element contents.
    */
   public String getText(String id) {
      ReportElement elem = getElement(id);

      if(elem == null || !(elem instanceof TextBased)) {
         throw new NoSuchElementException(id);
      }

      return ((TextBased) elem).getText();
   }

   /**
    * Set the directory where this template is saved. This affects the
    * relative path of images. It serves as the current directory when
    * resolving a relative file path.
    * @param dir directory full path.
    */
   public void setDirectory(String dir) {
      topdir = dir;

      if(dir != null && dir.indexOf(":\\") > 0) {
         topdir = dir.toLowerCase();
      }
   }

   /**
    * Get the current directory of this report.
    */
   public String getDirectory() {
      return topdir;
   }

   /**
    * Print one page. Return true if more contents need to be printed.
    * Normally print(PrintJob) should be used for printing. This function
    * is used by print() to print individual pages.
    * <p>
    * A StylePage contains information on how to print a particular page.
    * Its print() method can be used to perform the actual printing of
    * the page contents to a printer graphics.
    *
    * @param pg style page.
    */
   public abstract boolean printNext(StylePage pg);

   /**
    * Print the next page. It may be called to print from a middle of
    * of page provided the printBox is setup correctly before the call.
    * For regular printing, printNext() should be used.
    */
   public abstract boolean printNextPage(StylePage pg);

   /**
    * Get the page orientation for the next page. The page orientation
    * can be changed in middle of a report by setting it in PageLayout
    * for StyleSheet, or setting the row orientation in a TabularSheet.
    * @return the next page orientation. Null if using default orientation.
    */
   public Integer getNextOrientation() {
      return nextOrient;
   }

   /* silverstream javac force this method to be pulic, original protected */

   /**
    * Print the next page area. The area is pointed by printBox.
    * The printHead pointer is NOT reset in this method. It should point to
    * the point where the printing should start.
    * @param pg page to print.
    * @param flow true to flow the next page if full.
    * @return true if more to print.
    */
   @Override
   public synchronized boolean printNextArea(StylePage pg, Vector elements, boolean flow) {
      // print the elements until end of page or finished all elements
      while(current < elements.size() &&
            (printHead.y < printBox.height ||
             ((BaseElement) elements.get(current)).isBreakArea()))
      {
         if(printNextLine(pg, elements, flow)) {
            break;
         }
      }

      boolean res = current < elements.size();

      if(!res && false && elements.size() > 0) {
         BaseElement lastElement = (BaseElement) elements.get(elements.size() - 1);
         // @by larryl, in designer, force a new page after pagebreak
         // so it's clear user will enter content on the new page
         // note that sheet break should be ignored
         res = res || lastElement.isBreakArea();
      }

      if(!res && elements.size() > 0 && current == elements.size()) {
         res = applyPageBreak((BaseElement) elements.get(current - 1), pg.getPageNum(), res);
      }

      return res;
   }

   /**
    * Print the next line or next block element.
    * @param flow true to flow the next page if full.
    * @return true if more to print and end of area is reached.
    */
   protected synchronized boolean printNextLine(StylePage pg, Vector elements, boolean flow) {
      if(current >= elements.size()) {
         return false;
      }

      // optimize away alignLine for text elements inside a section
      boolean insideSection =
         ((BaseElement) elements.elementAt(0)).isInSection();
      int endpar = current;

      // the endpar points to the element after the last on the paragraph
      for(endpar = current; endpar < elements.size(); endpar++) {
         BaseElement v = (BaseElement) elements.elementAt(endpar);

         // this starts a paragraph
         if(v.isBlock() || v.isNewline() && endpar > current) {
            break;
         }
      }

      // take cares of vertical anchor
      BaseElement elem1 = (BaseElement) elements.elementAt(current);

      if(elem1 instanceof PainterElement) {
         PainterElementDef pe = (PainterElementDef) elem1;

         if(pe.getAnchor() != null && (pe.getAnchor().y - pe.getAnchorDepth()) > 0) {
            // remember the element this is anchored against
            if(current > 0) {
               pe.setAnchorElement((ReportElement) elements.elementAt(current - 1));
            }

            float oy = printHead.y;

            printHead.y += (pe.getAnchor().y - pe.getAnchorDepth()) * 72;

            // if the anchor takes up the remaining space in the current page,
            // just set the depth (consumed anchor) and proceed to next page
            if(flow && (printHead.y >= printBox.height ||
                        // @by larryl, avoid infinite loop
                        !pe.isBreakable() &&
                        printHead.y + pe.getPreferredSize().height >= printBox.height))
            {
               pe.setAnchorDepth(pe.getAnchorDepth() + (printBox.height - oy) / 72);
               return true;
            }

            // consume the anchor distance, if this is not set, we won't know
            // the anchor has already been applied and the anchor may be
            // applied again on the next page
            if(flow) {
               pe.setAnchorDepth(pe.getAnchorDepth() + (printBox.height - oy) / 72);
            }
         }
      }

      // reset the current line height
      lineH = 0;

      // block element
      if(endpar == current) {
         BaseElement v = (BaseElement) elements.elementAt(current);

         // ignore invisible elements
         if(!v.checkVisible()) {
            moveToNext(elements);
            return false;
         }

         printHead.x = 0;

         // check for conditional page break
         if(v instanceof CondPageBreakElement) {
            int h = ((CondPageBreakElementDef) v).getMinimumHeight();

            moveToNext(elements);
            v.print(pg, this);
            boolean result = (h + printHead.y > printBox.height);

            if(false) {
               printHead.y += 10;
            }

            return result;
         }

         // page break, advance to next page
         if(v instanceof PageBreakElementDef) {
            v.print(pg, this);
            moveToNext(elements);

            // Bug #3071, if has page layout, export no page, do not return false, use
            // the page layout.
            if(pg instanceof ReportGenerator.SingleStylePage) {
               return false;
            }

            return v.checkVisible();
         }
         // print table
         else if(v instanceof TableElement) {
            boolean more = true;
            boolean first = true;

            TableElementDef table = (TableElementDef) v;
            table.validate(pg, this, null);

            // may need to print multiple times because a table
            // may span across more than one page and section
            do {
               // if next region is larger
               // than the remaining space, advance to next page
               int fit = table.fitNext(printBox.height - printHead.y);
               // @stephenwebster, For bug1417535455177
               // The fitNext method returns 0 when an AreaBreak is encountered.
               // In the case of a single page report output, we do not want to print
               // on a new page, the entire table should be printed.  I have done what is
               // necessary in TableElementDef.layout to prevent an AreaBreak from being added.
               // This is a safeguard to prevent any loss of data in the output in an unexpected
               // case.
               fit = pg instanceof ReportGenerator.SingleStylePage && fit == 0 ? 1 : fit;

               if(fit < 0 && (v.getParent() instanceof FixedContainer)) {
                  return true;
               }
               else if(fit < 0) {
                  // if printing from the top of frame and this region
                  // can not fit, ignore this table otherwise it goes
                  // into infinite loop
                  if(more) {
                     // if more space to print, go to next area
                     if(printHead.y > 0) {
                        return true;
                     }

                     if(isNextAreaLarger()) {
                        return true;
                     }
                  }

                  // in the middle of table and top of page, ignore
                  if(table.getNextRegion() > 0) {
                     LOG.debug("Table " + table.getID() + " truncated due to layout error");
                     moveToNext(elements);
                     return false;
                  }
               }
               // fit == 0 means always advance to next page
               else if(fit == 0) {
                  if(printHead.y > 0 || isNextAreaLarger()) {
                     return true;
                  }
               }

               more = v.print(pg, this);

               if(!more) {
                  completeElement(v, pg);
               }

               first = false;

               // if TABLE_FIT_CONTENT_1PP, advance to next page
               if(more && table.getLayout() == TABLE_FIT_CONTENT_1PP) {
                  return true;
               }
            }
            while(more);
         }
         else {
            if(!v.isBreakable()) {
               Size psize = v.getPreferredSize();

               // the preferred size is more than the remaining space
               // advance to next page
               if(flow && printHead.y + psize.height > printBox.height && printHead.y > 0) {
                  return true;
               }
            }

            // handle anchor
            if(v instanceof PainterElement) {
               // printHead.x = ((PainterElementDef) v).getAnchorX();
               PainterElementDef pe = (PainterElementDef) v;
               Size psize = pe.getPreferredSize();
               float totalw = pe.getAnchorX(), centerw = 0, rightw = 0;
               int align = pe.getAlignment();

               if((align & H_CENTER) != 0) {
                  centerw += psize.width + totalw;
               }
               else if((align & H_RIGHT) != 0 || (align & H_CURRENCY) != 0) {
                  rightw += psize.width;
               }

               float leftx = totalw;
               float centerx = Math.max((printBox.width - centerw) / 2, 0);
               float rightx = Math.max(printBox.width - rightw, 0);

               if((align & H_LEFT) != 0 && leftx >= printHead.x) {
                  printHead.x = leftx;
               }
               else if((align & H_CENTER) != 0 && centerx >= printHead.x) {
                  printHead.x = centerx;
               }
               else if(((align & H_RIGHT) != 0 || (align & H_CURRENCY) != 0) &&
                  rightx >= printHead.x) {
                  printHead.x = rightx;
               }
            }

            // it should only return true if end of page and more to print
            if(v.print(pg, this)) {
               if(v instanceof SectionElementDef) {
                  SectionElementDef s = (SectionElementDef) v;

                  // for section element, if it is not last element or
                  // it raised a valid new page request, return true.
                  if(!s.isPrintedOver() || !isLastElementInReport(current, elements)) {
                     return true;
                  }
               }
               else {
                  return true;
               }
            }
            else {
               completeElement(v, pg);
            }
         }

         // advance the current element pointer
         moveToNext(elements);
      }
      // print inline elements on one paragraph
      else {
         int anchored = -1; // index of last anchored element
         float maxH = 0;

         // check if any element is anchored
         for(int i = current; i < endpar; i++) {
            ReportElement v = (ReportElement) elements.elementAt(i);

            if((v instanceof PainterElement)) {
               PainterElementDef pe = (PainterElementDef) v;

               // if the anchored element exceeds the right bound,
               // don't print any more element on the right
               if(pe.getAnchor() != null) {
                  // if no wrapping, print the painter now and don't count
                  // it as the main flow
                  if(pe.getWrapping() == WRAP_NONE) {
                     Position oph = printHead;
                     float py = oph.y;

                     if(pe.getAnchor().y < 0) {
                        py += -pe.getAnchor().y * 72;
                     }

                     printHead = new Position(pe.getAnchorX(), py);
                     pe.print(pg, this);

                     printHead = oph;
                  }
                  else {
                     // @by larryl, the anchored is used to find element that
                     // are anchored at the current line for wrapping. For
                     // positive/zero y anchor, the y anchor is treated as being
                     // used to advance the element, so it should not be used
                     // for wrapping anymore
                     if(pe.getAnchor().y <= 0 && !pe.isFinished()) {
                        anchored = i;
                     }

                     if(pe.getAnchorX() + pe.getPreferredSize().width > printBox.width) {
                        endpar = i + 1;
                        break;
                     }
                  }
               }
            }
         }

         // alignments are ignored if any element is anchored
         if(anchored >= 0) {
            // calculate the height
            for(int i = current; i < endpar; i++) {
               BaseElement v = (BaseElement) elements.elementAt(i);

               if(v instanceof PainterElement) {
                  PainterElementDef pe = (PainterElementDef) v;
                  Size d = v.getPreferredSize();
                  float anchory = (pe.getAnchor() == null ||
                     pe.getAnchor().y >= 0) ? 0 : -pe.getAnchor().y * 72;

                  // adjust for vertical anchor
                  d.height += anchory;

                  // if anchored below the line
                  // middle of a page, try at next page
                  if(flow && printHead.y + anchory >= printBox.height && printHead.y > 0) {
                     return true;
                  }

                  // if taller than the page
                  if(flow && printHead.y + d.height > printBox.height + 1) {
                     // @by larryl, since we don't handle an anchor painter
                     // going across page, the elements are pushed to the next
                     // page if the anchored painter exceeds the page height.
                     // To properly handle this, we need to have logic to allow
                     // anchored (as well as elements wrapped around the anchor)
                     // to continue across pages. That is complex and of little
                     // use in practice (at least in regular reporting). In this
                     // case the breakable is not honored. If we don't push the
                     // elements to the next page, the elements wrapping around
                     // the painter will be printed outside of the page.
                     //if(!pe.isBreakable() && printHead.y > 0) {
                     if(printHead.y > 0) {
                        // consume vertical anchor
                        pe.setAnchorDepth(pe.getAnchorDepth() + printHead.y / 72);
                        return true;
                     }
                  }

                  // @by jasons don't add the height of the element if it is not
                  //     anchored, otherwise the non-anchored elements will be
                  //     moved down unnecessarily.
                  if(pe.getAnchor() != null) {
                     maxH = Math.max(maxH, d.height);
                  }
               }
            }

            Position ohead = new Position(printHead);
            // get a list of areas divided by the painters, and
            // elements between the painters
            // areas between painters if !horFlow, otherwise painter areas
            Vector sections = new Vector();
            // vector of vector of elements if !horFlow, elements otherwise
            Vector elems = new Vector();
            Vector painters = new Vector(); // painters, 1 less than elems
            Vector es = horFlow ? null : new Vector();

            // populate the vectors
            for(int i = current; i < endpar; i++) {
               BaseElement v = (BaseElement) elements.elementAt(i);

               if(v instanceof PainterElement &&
                  ((PainterElement) v).getAnchor() != null)
               {
                  PainterElementDef pe = (PainterElementDef) v;
                  // right anchored if negative
                  float ax = pe.getAnchorX();

                  // remember the element this is anchored against
                  if(i > current) {
                     pe.setAnchorElement((ReportElement) elements.elementAt(current));
                  }

                  Size psize = pe.getPreferredSize();
                  // position after the painter
                  float newx = ax + Math.min(printBox.width - ax, psize.width);

                  if(horFlow) {
                     float py = (i > current) ?
                        (printHead.y + printBox.y - pe.getAnchor().y * 72) :
                        (printHead.y + printBox.y);

                     float px = ax + printBox.x;
                     Rectangle area = new Rectangle((int) px, (int) py,
                                                    (int) psize.width,
                                                    (int) psize.height);

                     // if only allow right side for wrapping, change the
                     // area so the left side of this painter is not used
                     // for drawing
                     if(pe.getWrapping() == WRAP_RIGHT) {
                        if(sections.size() == 0) {
                           int right = area.x + area.width;
                           area.x = printBox.x;
                           area.width = right - area.x;
                        }
                        else {
                           Rectangle parea = (Rectangle) sections.get(sections.size() - 1);
                           parea.width = area.x + area.width - parea.x;
                           area = null;
                        }
                     }
                     // if left wrapping only, stretch the size so the right
                     // side is taken up
                     else if(pe.getWrapping() == WRAP_LEFT) {
                        area.width = printBox.x + printBox.width - area.x;
                        // force loop to terminate after add, don't break here
                        i = endpar;
                     }

                     if(area != null) {
                        sections.addElement(area);
                     }
                  }
                  else {
                     // add an area before this anchored element
                     Rectangle rect = new Rectangle(
                        (int) (printHead.x + printBox.x),
                        (int) (printHead.y + printBox.y),
                        (int) (ax - printHead.x), (int) maxH);

                     if(rect.width > 0) {
                        sections.addElement(rect);
                        elems.addElement(es);
                        es = new Vector();
                     }
                  }

                  painters.addElement(v);

                  if(pe.getWrapping() != WRAP_NONE) {
                     printHead.x = newx;
                  }
               }
               else {
                  (horFlow ? elems : es).addElement(v);
               }
            }

            // last section
            if(!horFlow) {
               Rectangle rect = new Rectangle((int) (printHead.x + printBox.x),
                  (int) (printHead.y + printBox.y),
                  (int) (printBox.width - printHead.x), (int) maxH);

               sections.addElement(rect);
               elems.addElement(es);
            }

            // print the elements in between in the areas
            int ncurrent = endpar;
            Vector nelements = elements;
            Rectangle oprintBox = printBox;

            // paint painters
            for(int i = 0; i < painters.size(); i++) {
               PainterElementDef v = (PainterElementDef) painters.elementAt(i);
               // swap to the outer printBox
               Position oph = printHead;
               Rectangle opb = printBox;
               float py = ohead.y;

               if(v.getAnchor().y < 0) {
                  py += -v.getAnchor().y * 72;

                  Size psize = v.getPreferredSize();

                  // @by larryl, prevent anchor pusing element outside of page
                  if(py > oprintBox.height - psize.height) {
                     py = oprintBox.height - psize.height;
                  }
               }

               printBox = oprintBox;
               printHead = new Position(v.getAnchorX(), py);

               v.print(pg, this);

               printHead = oph;
               printBox = opb;
            }

            boolean more = false; // true if last element is not done

            // horizontal flow
            if(horFlow) {
               Rectangle[][] grid = calcGrid(ohead.x + oprintBox.x,
                                             oprintBox.y + (current == 0 ? 0 : ohead.y),
                                             oprintBox.width, maxH, sections);

               nelements = elems;
               current = 0;

               // print horizontally (each row)
               for(int i = 0; i < grid.length; i++) {
                  if(grid[i].length == 0) {
                     continue;
                  }

                  int height = 0;

                  // print one line
                  for(int j = 0; j < grid[i].length; j++) {
                     printHead = new Position(0, 0);
                     printBox = grid[i][j];

                     boolean done = printNextLine(pg, nelements, true);

                     height = Math.max(height, (int) printHead.y);
                  }

                  // adjust grid height
                  for(int j = 0; j < grid[i].length; j++) {
                     grid[i][j].y += height;
                     grid[i][j].height -= height;
                  }

                  // if space left and new contents were added, use the
                  // adjusted areas again
                  if(height > 0 && grid[i].length > 0 && grid[i][0].height > 0){
                     i--;
                     continue;
                  }
               }

               more = current < elements.size();
            }
            // vertical flow
            else {
               int secidx = 0;

               // paint elements between the painters
               for(int i = 0; i < elems.size(); i++) {
                  es = (Vector) elems.elementAt(i);

                  // paint the elements in between
                  if(es.size() > 0) {
                     int oi = secidx;
                     secidx = Math.max(secidx, i);

                     if(secidx == 0 || oi != secidx) {
                        printHead.x = printHead.y = 0;
                        printBox = (Rectangle) sections.elementAt(secidx);
                     }

                     nelements = es;
                     current = 0;

                     while((more = printNextArea(pg, nelements, true)) &&
                           secidx < sections.size() - 1) {
                        printHead.x = printHead.y = 0;
                        printBox = (Rectangle) sections.elementAt(++secidx);
                     }
                  }
               }
            }

            printBox = oprintBox;
            printHead = new Position(0, ohead.y + maxH);

            // finish unfinished element
            if(more) {
               // not enough room in this page, continue at the same
               // point in next area
               if(printNextArea(pg, nelements, true)) {
                  Object ce = nelements.elementAt(current);
                  int idx = elements.indexOf(ce);

                  // should always find the element in the original
                  // vector, otherwise it's an internal error
                  if(idx >= 0) {
                     ncurrent = idx;
                  }
               }
               // @by larryl, finished printing all contents in the nelements,
               // set the current to the next element after the last element
               // in nelements
               else if(nelements.size() > 0) {
                  Object ce = nelements.elementAt(nelements.size() - 1);
                  int idx = elements.indexOf(ce);

                  // should always find the element in the original
                  // vector, otherwise it's an internal error
                  if(idx >= 0) {
                     ncurrent = idx + 1;
                  }
               }
            }

            current = ncurrent;
         }
         // no anchored elements, print in sequence
         else {
            for(int endline = current; endline < endpar;) {
               int curr_align = 0;

               // the endline points to the element after the last on line
               for(; endline < endpar; endline++) {
                  BaseElement v = (BaseElement) elements.elementAt(endline);
                  int a = v.getAlignment() & H_ALIGN_MASK;

                  // this starts a newline
                  // if the alignment goes from center -> left, or
                  // right -> center
                  // etc., the element starts a new line
                  if(a < curr_align && !v.isFlowControl() || v.isNewline() && endline > current) {
                     break;
                  }
                  // last element on line, break after this element
                  else if(v.isLastOnLine()) {
                     endline++;
                     break;
                  }

                  if(!v.isFlowControl()) {
                     curr_align = a;
                  }
                  // @by larryl, if this element is a flow control (e.g. space),
                  // set the alignment to be same as the previous element so it
                  // does not mess up the space calculation for
                  // center/right alignment below
                  else {
                     v.setAlignment(curr_align);
                  }
               }

               printHead.x = 0;
               float printY = printHead.y; // original y position
               float lastX = 0; // position right of last printed element
               float totalw = 0, centerw = 0, rightw = 0;

               // reset current line height because this loop always
               // starts a new line
               lineH = 0;

               // calculate the centered and right elements width
               // check if should advance to next page
               for(int i = current; i < endline; i++) {
                  BaseElement v = (BaseElement) elements.elementAt(i);
                  Size psize = v.getPreferredSize();

                  // check if this element would overflow
                  // the preferred size is more than the remaining space
                  // advance to next page
                  if(flow && !v.isBreakable() && printHead.y + psize.height > printBox.height) {
                     boolean next = printHead.y > 0;

                     // @by larryl, if the painter that would pushing this
                     // line to the next page does not belong on the current
                     // line anyway, don't push the whole line to the next page.
                     // Instead just print the contents before the painter.
                     // @by mikec, only push to next line when the
                     // preferred size smaller than page size, otherwise will
                     // fall into a infinite loop.
                     if(next && totalw > 0 && (totalw + psize.width > printBox.width)) {
                        endline = i;
                        break;
                     }

                     // print partial contents if the image is larger
                     // than the page, otherwise it causes infinite loop
                     if(!next) {
                        // check if the next frame is big enough for the
                        // image
                        for(int k = 0; k < frames.length; k++) {
                           if(frames[k].height > psize.height) {
                              next = true;
                              break;
                           }
                        }

                        if(!next && npframes != null) {
                           for(int k = 0; k < npframes.length; k++) {
                              if(npframes[k].height > psize.height) {
                                 next = true;
                                 break;
                              }
                           }
                        }
                     }

                     // more space in next page, don't print partial line
                     if(next) {
                        return true;
                     }
                  }

                  if(v instanceof PainterElement) {
                     PainterElementDef pe = (PainterElementDef) v;

                     // none-wrap painter already printed before and should
                     // be completely ignored now
                     if(pe.getWrapping() != WRAP_NONE) {
                        // total width for anchored element is the anchor
                        // position plus the preferred width
                        if(pe.getAnchor() != null) {
                           float ax = pe.getAnchorX();

                           anchored = i;
                           totalw = ax + psize.width;
                        }
                        else {
                           totalw += psize.width;
                        }
                     }
                  }
                  else {
                     totalw += psize.width;
                  }

                  // if line full, the next element should be printed on
                  // the next line
                  // if anchored, don't break line until the last anchored
                  // element is past
                  if(i > anchored && totalw > printBox.width) {
                     if(!(v instanceof SpaceElement)) {
                        // allow at least one element on the line otherwise
                        // infinite loop
                        // if the last element is breakable, let it stay and
                        // it will wrap to the next line by itself
                        // painter can't be wrapped horizontally
                        boolean hwrap = !(v instanceof PainterElement) && v.isBreakable();

                        endline = Math.max(hwrap ? (i + 1) : i, current + 1);
                     }
                     // if break from the line, totalw should be cutdown either
                     else {
                        totalw -= printBox.width;
                     }
                  }

                  // could be out of bound by previous increment
                  endline = Math.min(endline, elements.size());

                  // endline may be changed in the if above
                  if(i < endline) {
                     int align = v.getAlignment();

                     if((align & H_CENTER) != 0) {
                        centerw += psize.width;
                     }
                     else if((align & H_RIGHT) != 0 || (align & H_CURRENCY) != 0) {
                        rightw += psize.width;
                     }

                     // negative y anchor is the distance from the top of
                     // the line
                     if(v instanceof PainterElement) {
                        PainterElementDef pe = (PainterElementDef) v;

                        if(pe.getAnchor() != null && pe.getAnchor().y < 0) {
                           psize.height += -pe.getAnchor().y * 72;
                        }
                     }

                     maxH = Math.max(maxH, psize.height);
                  }
               }

               float leftx = printHead.x;
               float centerx = Math.max((printBox.width - centerw) / 2, 0);
               float rightx = Math.max(printBox.width - rightw, 0);
               int pcnt = pg.getPaintableCount(); // count before print

               lastHead = new Position(printHead);

               // print line elements
               for(int i = current; i < endline; i++) {
                  BaseElement v = (BaseElement) elements.elementAt(i);
                  int sel = 0;
                  printHead.y = printY;

                  if((v.getAlignment() & H_LEFT) != 0 && leftx >= printHead.x) {
                     printHead.x = leftx;
                     sel = 0;
                  }
                  else if((v.getAlignment() & H_CENTER) != 0 && centerx >= printHead.x) {
                     printHead.x = centerx;
                     sel = 1;
                  }
                  else if(((v.getAlignment() & H_RIGHT) != 0 ||
                           (v.getAlignment() & H_CURRENCY) != 0) &&
                     rightx >= printHead.x) {
                     printHead.x = rightx;
                     sel = 2;
                  }

                  // handle x anchor
                  if(i == anchored) {
                     printHead.x = ((PainterElementDef) v).getAnchorX();
                  }

                  // if we already reached right edge,
                  // advance to next line
                  if(printHead.x >= printBox.width) {
                     if(!insideSection) {
                        alignLine(pcnt, pg.getPaintableCount(), pg, printY + printBox.y, lineH);
                     }

                     advance(0, lineH);
                     printY = printHead.y;
                     lineH = 0;
                     leftx = centerx = rightx = 0;

                     pcnt = pg.getPaintableCount();
                  }

                  // the paintable count before this element is printed
                  int epcnt = pg.getPaintableCount();

                  // print the element to page
                  if(v.print(pg, this)) {
                     if(!insideSection) {
                        lineH = Math.max(lineH, printHead.y - printY);
                        alignLine(pcnt, pg.getPaintableCount(), pg, printY + printBox.y, lineH);
                     }

                     float adjH = 0;

                     // @by larryl, if the remaining space is less than the
                     // minimum height, indicate it's the end of area
                     if(v instanceof PainterElementDef) {
                        adjH = ((PainterElementDef) v).getMinimumHeight();
                     }

                     current = i;
                     printHead.y = Math.max(printHead.y, printY + lineH);

                     // true is nothing printed or no space to print
                     // @by larryl, if in a regular flow and the element has
                     // more to print, return true to print the rest on next pg
                     return (flow && printHead.y >= printBox.height - adjH) ||
                        epcnt == pg.getPaintableCount();
                  }
                  else {
                     completeElement(v, pg);
                  }

                  // for special wrapping case where right side text is
                  // wrapped around a left painter
                  if(v instanceof PainterElement) {
                     lastHead = new Position(printHead);
                  }

                  lastX = printHead.x;

                  switch(sel) {
                  case 0:
                     leftx = printHead.x;
                     break;
                  case 1:
                     centerx = printHead.x;
                     break;
                  case 2:
                     rightx = printHead.x;
                     break;
                  }

                  // adjust the y position if advance line (set by
                  // TextElement)
                  if(advanceLine > 0) {
                     float oprintY = printY;

                     printY = printHead.y - advanceLine;
                     lineH = Math.max(oprintY + lineH - printY, advanceLine);

                     if(!insideSection) {
                        alignLine(pcnt, epcnt + 1, pg, printY + printBox.y, lineH);
                     }

                     pcnt = pg.getPaintableCount() - 1;
                  }
                  else {
                     lineH = Math.max(lineH, printHead.y - printY);
                  }
               }

               if(!insideSection) {
                  alignLine(pcnt, pg.getPaintableCount(), pg, printY + printBox.y, lineH);
               }

               // go to the next line
               printHead.y = printY;
               printHead.x = lastX;
               current = endline;

               // skip newline because we already advanced printHead.y
               // we can't skip the newline if the last element is
               // a break (newline with continuation) otherwise the
               // second newline appears at the same line on the
               // designer (the layout is correct though)
               if(lineH > 0 && current < elements.size() &&
                  !(elements.elementAt(current - 1) instanceof NewlineElement)
                  && (elements.elementAt(current) instanceof NewlineElement))
               {
                  NewlineElementDef nl = (NewlineElementDef) elements.elementAt(current);
                  nl.skip();

                  if(nl.getRemain() <= 0) {
                     // can't skip or it won't showup in design
                     // always print the newline at the bottom of the line
                     Size pd = nl.getPreferredSize();

                     printHead.y += Math.max(lineH - pd.height - 1, 0);
                     nl.print(pg, this);
                     // move print head back to top of line
                     printHead.y = printY;

                     moveToNext(elements);
                  }
               }

               // if a bean and lineAfter is turned off, don't advance to
               // the next line
               advance(0, lineH);
            }
         }
      }

      return false;
   }

   /**
    * Move to the next visible element. Instead of incrementing current, we
    * also skips all invisible elements so hidden elements at the end of the
    * report would not cause an empty page.
    */
   private void moveToNext(Vector elements) {
      do {
         current++;
      }
      while(current < elements.size() && !((BaseElement) elements.get(current)).checkVisible());
   }

   private boolean applyPageBreak(BaseElement v, int pageNum, boolean result) {
      if(!(v instanceof PageBreakElementDef)) {
         return result;
      }

      Vector allElements = getAllElements();

      if(allElements.indexOf(v) == allElements.size() - 1) {
         PageBreakElementDef pageBreakElementDef = (PageBreakElementDef) v;
         String createPageOption = pageBreakElementDef.getCreatePageOption();
         return PageBreakElementDef.ALWAYS_CREATE.equals(createPageOption)
            || PageBreakElementDef.ODD_CREATE.equals(createPageOption) && pageNum % 2 != 0
            || PageBreakElementDef.EVEN_CREATE.equals(createPageOption) && pageNum % 2 == 0;
      }

      return result;
   }

   /**
    * Print the header and footer of the page. The header and footer are
    * printed in printNext(). This method does not need to be called
    * by users.
    * @param pg a style page.
    */
   protected synchronized void printHeaderFooter(StylePage pg) {
      if(hfFmt == null) {
         hfFmt = new HFTextFormatter(new Date(), pgStart, pgTotal);
      }

      int ocurrent = current;
      Rectangle oprintBox = printBox;
      int ocurrFrame = currFrame;
      Rectangle[] oframes = frames;
      Rectangle[] onpframes = npframes;
      Position oprintHead = printHead;
      int pgnum = hfFmt.getPageNumber();
      Integer onextOrient = this.nextOrient;

      Vector nelements = null;
      Dimension pgsize = pg.getPageDimension();
      Vector paragraph = null;

      // @by larryl, if printing a single page view, shrink the page
      // so footer does not drop off to the bottom
      if(pg instanceof ReportGenerator.SingleStylePage) {
         int footer = (int) (getMargin().bottom * 72);
         ReportGenerator.shrinkPage(pg, footer);

         pgsize = pg.getPageDimension();
      }

      // @by billh, only when pgnum greater than zero should we print
      // header and footer elements, but if is design time, we always
      // print them for end users to design them
      if(pgnum >= 1) {
         // frame is the box the current elements are in, it could be
         // different from printBox in some situations
         printBox = getHeaderBounds(pgsize);
         frames = new Rectangle[] {printBox};
         npframes = null;
         currFrame = 0;
         printHead = new Position(0, 0);

         // first page header
         if(pgnum == 1 && firstHeader != null && firstHeader.size() > 0) {
            paragraph = (Vector) firstHeader.clone();
         }
         // odd page header
         else if(pgnum % 2 == 1 && oddHeader != null && oddHeader.size() > 0) {
            paragraph = (Vector) oddHeader.clone();
         }
         // even page header
         else if(pgnum % 2 == 0 && evenHeader != null && evenHeader.size() > 0){
            paragraph = (Vector) evenHeader.clone();
         }
         else {
            paragraph = (Vector) headerElements.clone();
         }

         processHF(paragraph.elements());
         nelements = paragraph;
         current = 0;
         printNextArea(pg, nelements, false);

         printBox = getFooterBounds(pgsize);
         frames = new Rectangle[] {printBox};
         npframes = null;
         currFrame = 0;
         printHead = new Position(0, 0);

         // first page footer
         if(pgnum == 1 && firstFooter != null && firstFooter.size() > 0) {
            paragraph = (Vector) firstFooter.clone();
         }
         // odd page header
         else if(pgnum % 2 == 1 && oddFooter != null && oddFooter.size() > 0) {
           paragraph = (Vector) oddFooter.clone();
         }
         // even page header
         else if(pgnum % 2 == 0 && evenFooter != null && evenFooter.size() > 0){
            paragraph = (Vector) evenFooter.clone();
         }
         else {
            paragraph = (Vector) footerElements.clone();
         }

         processHF(paragraph.elements());
         nelements = paragraph;
         current = 0;
         printNextArea(pg, nelements, false);
      }

      // restore context
      printBox = oprintBox;
      printHead = oprintHead;
      frames = oframes;
      npframes = onpframes;
      currFrame = ocurrFrame;
      current = ocurrent;
      nextOrient = onextOrient;
   }

   /**
    * Fire a pagebreak event.
    */
   public void firePageBreakEvent(StylePage pg, boolean more) {
      try {
         rewinded = false; // reset rewinded flag
         printHeaderFooter(pg);

         hfFmt.nextPage(more);
      }
      catch(ScriptException sex) {
         throw sex;
      }
      catch(Exception ex) {
         LOG.warn("Failed to process page break event", ex);
      }
      finally {
         pg.complete();
      }
   }

   /**
    * Remove all elements from the contents area.
    */
   protected abstract void removeContents();

   /**
    * Remove the specified element.
    * @param id element id in string format.
    */
   public abstract void removeElement(String id);

   /**
    * Move element up or down.
    * @param id element id in string format.
    * @param direction move direction, can be one of ReportSheet.UP or
    * ReportSheet.DOWN.
    */
   public abstract void moveElement(String id, int direction);

   /**
    * Check if the current printing element is the last element on the report.
    * TabularSheet needs to check if it's on the last row.
    */
   private boolean isLastElementInReport(int curr, Vector elements) {
      for(int i = curr + 1; i < elements.size(); i++) {
         ReportElement elem = (ReportElement) elements.get(i);

         if(elem.isVisible()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Remove all report elements.
    */
   public void clear() {
      removeContents();

      // clear all header elements
      Vector[] headers = {headerElements, footerElements, firstHeader,
         firstFooter, evenHeader, evenFooter, oddHeader, oddFooter};

      for(int i = 0; i < headers.length; i++) {
         if(headers[i] != null) {
            headers[i].removeAllElements();
         }
      }

      deleteScriptEnv();
   }

   /**
    * Reset all elemnts in the contents.
    */
   protected abstract void resetContents();

   /**
    * Reset the printing. If there is a partially printed job, the
    * rest of the contents are ignored. The next printNext starts
    * from first page.
    */
   @Override
   public synchronized void reset() {
      super.reset();

      // if onload is called in XSessionManager and not printed, don't
      // reset onload so it does not get run twice
      if(current > 0) {
         resetOnLoad();
      }

      current = 0;
      frames = null;
      npframes = null;
      hfFmt = null;
      nextOrient = null;
      cmargin = margin;

      resetContents();

      // reset all header elements
      Vector[] headers = {headerElements, footerElements, firstHeader,
         firstFooter, evenHeader, evenFooter, oddHeader, oddFooter};

      for(int i = 0; i < headers.length; i++) {
         if(headers[i] != null) {
            for(int j = 0; j < headers[i].size(); j++) {
               ((BaseElement) headers[i].elementAt(j)).reset();
            }
         }
      }
   }

   /**
    * Set the visibility of an element.
    * @param id element id.
    * @param vis true to show element and false to hide it.
    */
   public void setVisible(String id, boolean vis) {
      ReportElement elem = getElement(id);

      if(elem != null) {
         elem.setVisible(vis);
      }
   }

   /**
    * Get a property value.
    * @param name property name.
    * @return property value.
    */
   public String getProperty(String name) {
      if(name.equals("PageSize")) {
         return getPageSizeString();
      }

      if(name.equals("Orientation")) {
         return getOrientationString();
      }

      String val = prop.getProperty(name);

      return val;
   }

   /**
    * Set a property. Properties are attributes in a report template
    * that can be used to store any arbitrary information. It is often
    * used by visual designers for configuration information. The properties
    * may serve as hints on how report is presented at runtime. The
    * currently recognized runtime properties are:<p><pre>
    * report.title
    * report.subject
    * report.author
    * report.keywords
    * report.comments
    * report.created
    * report.modified
    * date.format
    * time.format
    * date.time.format
    * </pre></p>
    * @param name property name.
    * @param val property value.
    */
   public void setProperty(String name, String val) {
      if(name.equals("PageSize")) {
         setPageSize(val);
         return;
      }

      if(name.equals("Orientation")) {
         setOrientation(val);
         return;
      }

      if(val == null) {
         prop.remove(name);
      }
      else {
         prop.put(name, val);
      }

      if(name.equals("date.format") || name.equals("time.format") ||
         name.equals("date.time.format"))
      {
         updateDateFormat(val, name);
      }
   }

   /**
    * Refresh all formats. Get date/time format patterns from the report
    * property or the report env, create a format with locale and then set to
    * the format map. If the locale is changed, it should refresh all formats.
    */
   @Override
   public void refreshFormats() {
      super.refreshFormats();
      String fmt = getProperty("date.format");

      if(fmt != null && fmt.trim().length() > 0) {
         updateDateFormat(fmt, "date.format");
      }

      fmt = getProperty("time.format");

      if(fmt != null && fmt.trim().length() > 0) {
         updateDateFormat(fmt, "time.format");
      }

      fmt = getProperty("date.time.format");

      if(fmt != null && fmt.trim().length() > 0) {
         updateDateFormat(fmt, "date.time.format");
      }
   }

   /**
    * Based on the property value, add appropriate date format to
    * the format map.
    */
   private void updateDateFormat(String format, String type) {
      if(format == null || format.equals("NONE")) {
         return;
      }

      Format fmt = null;

      if(format.equals("FULL")) {
         fmt = DateFormat.getDateInstance(DateFormat.FULL, locale);
      }
      else if(format.equals("LONG")) {
         fmt = DateFormat.getDateInstance(DateFormat.LONG, locale);
      }
      else if(format.equals("MEDIUM")) {
         fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
      }
      else if(format.equals("SHORT")) {
         fmt = DateFormat.getDateInstance(DateFormat.SHORT, locale);
      }
      else { // Custom date format.
         fmt = Tool.createDateFormat(format, locale);
      }

      if(type.equals("date.format")) {
         addFormat(java.sql.Date.class, fmt);
      }
      else if(type.equals("time.format")) {
         addFormat(java.sql.Time.class, fmt);
      }
      else if(type.equals("date.time.format")) {
          addFormat(Date.class, fmt);
          addFormat(java.sql.Timestamp.class, fmt);
      }
   }

   /**
    * Get the page size of current report. If the report is not a top
    * level report such as a bean sheet or sub report sit in another
    * report sheet. Use the parent sheet's page size instead.
    *
    * @return report page size.
    */
   public Size getPageSize() {
      return getTopLevelReport().size;
   }

   /**
    * Set the report page size.
    *
    * @param size page size in inches.
    */
   public void setPageSize(Size size) {
      this.size = size;
   }

   /**
    * Get the orientation of current report. If the report is not a top
    * level report such as a bean sheet or sub report sit in another
    * report sheet. Use the parent sheet's orientation instead.
    */
   public int getOrientation() {
      return getTopLevelReport().orient;
   }

   /**
    * Set the orientation of the report page.
    */
   public void setOrientation(int orient) {
      this.orient = orient;
   }

   /**
    * Check if the page of this report is set to landscape.
    *
    * @return true if is landscape.
    */
   public boolean isLandscape() {
      return this.orient == StyleConstants.LANDSCAPE;
   }

   /**
    * Get the string format of page size.(for backward compatibility)
    *
    * @return a string in the form of: WIDTHxHEIGHT, e.g. 8.5x11.
    */
   private String getPageSizeString() {
      return PaperSize.getName(getPageSize());
   }

   /**
    * Set the report page size. If val is null set to DEFAULT_SIZE.
    *
    * @param val a string in the form of: WIDTHxHEIGHT, e.g. 8.5x11. Or
    * a string value of a Size object or constant.
    */
   private void setPageSize(String val) {
      Size size = DEFAULT_PAGE_SIZE;

      if(val != null) {
         size = PaperSize.getSize(val);
      }

      setPageSize(size);
   }

   /**
    * Get the string format of page orientation.(for backward compatibility)
    *
    * @return a string of of 'Landscape', 'Portrait'.
    */
   private String getOrientationString() {
      return (getOrientation() == LANDSCAPE) ? "Landscape" : "Portrait";
   }

   /**
    * Set the report orientation. If val is null set to portrait.
    *
    * @param val a string of either 'Landscape', 'Portrait', or the
    * string value of the orientation option.
    *
    */
   private void setOrientation(String val) {
      int o = PORTRAIT;

      if(val != null) {
         o = PaperSize.getOrientation(val);
      }

      setOrientation(o);
   }

   /**
    * Return the parent sheet if the report is not a top level report
    * such as a bean sheet or sub report sit in another report sheet.
    */
   private ReportSheet getTopLevelReport() {
      return this;
   }

   /**
    * Handle keep with next. The current printing index is adjusted if
    * if the keepWithNext causes elements to be moved to the next page.
    * @param pg the page that is printed.
    * @param ocurrent element index before cell is printed.
    * @param elements the list of elements used to print the page.
    */
   public synchronized void keepWithNext(StylePage pg, int ocurrent, Vector elements) {
      // @by larryl, loop to handle multiple keepWithNext elements
      int newcurrent;

      while(true) {
         int pcnt = pg.getPaintableCount();

         newcurrent = keepWithNext0(pg, ocurrent, current, elements);

         // @by larryl, if no paintable is removed, we stop checking
         // for keepWithNext. Can't compare the 'current' with the return
         // 'newcurrent' since elements could be removed in a bean, but
         // the current would be the same. In order to support for multiple
         // keepWithNext in beans, we need to compare the paintable count
         if(pcnt == pg.getPaintableCount()) {
            break;
         }

         current = newcurrent;
      }

      current = newcurrent;
   }

   /**
    * Internal method for recursion.
    */
   private synchronized int keepWithNext0(StylePage pg, int ocurrent, int current,
                 Vector elements) {
      if((pg.getPaintableCount() > 1 ||
          pg.getPaintableCount() > 0 && current > ocurrent) &&
         current > 0 && current < elements.size())
      {
         int idx = pg.getPaintableCount() - 1;
         Paintable lastpt = pg.getPaintable(idx);
         ReportElement lastE = lastpt.getElement();
         // fullnames of elements to move to next
         Set removeElems = new HashSet();

         // @by larryl, find last visible element by skipping spacing elements.
         // this is done to allow keepWithNext to ignore the spacing elements
         // between it and the next element
         while(lastE != null && ((BaseElement) lastE).isFlowControl()
               && idx > 0)
         {
            removeElems.add(lastE.getFullName());
            lastpt = pg.getPaintable(--idx);
            lastE = lastpt.getElement();
         }

         // @by larryl, check if the lastE is the first element printed on
         // this page. If yes, we only push it to the next page if the
         // element is not at the top of the page already. Otherwise we
         // will have an infinite loop
         boolean firstE = false;
         boolean found = false;

         for(int i = 0; i < elements.size(); i++) {
            // find the element in the element list
            if(elements.get(i) == lastE) {
               found = true;

               // if first element, must be first on the page
               if(i == 0 || i == 1 &&
                  pg.getPaintable(0) instanceof GridPaintable)
               {
                  firstE = true;
                  break;
               }
               else {
                  ReportElement preLastE = null;
                  firstE = true;

                  // find the last non-flow control element
                  for(int p = i - 1; p >= 0; p--) {
                     BaseElement elemp = (BaseElement) elements.get(p);

                     if(!elemp.isFlowControl()) {
                        preLastE = elemp;
                        break;
                     }
                  }

                  // check if the element before lastE is printed on this page
                  if(preLastE != null) {
                     for(int j = idx - 1; j >= 0; j--) {
                        if(pg.getPaintable(j).getElement() == preLastE) {
                           firstE = false;
                           i += elements.size(); // force outer loop to terminate
                           break;
                        }
                     }
                  }
               }
            }
         }

         // @by larryl, if the element is not on the element list, this means
         // the lastE is inside a bean or subreport. In that case the keep
         // with next should already been called and handled in the
         // bean/subreport so we should ignore it here
         if(!found) {
            return current;
         }

         ReportSheet lastR = ((BaseElement) lastE).getReport();

         // if the lastE is the first element, we check if the npframe is
         // larger than the current frame. This is necessary so if the first
         // element inside a cell of a tabular grid needs to be moved to
         // the next page, it would not be rejected if the cell itself is
         // not at the top of the page.
         if(lastE != null && lastE.isKeepWithNext() &&
            (!firstE || lastR.isNextAreaLarger()) &&
            // keep with next not supported in band or subreport.
            !((BaseElement) lastE).isInSection())
         {
            Paintable firstpt = pg.getPaintable(0);
            int idx0 = 0;

            removeElems.add(lastE.getFullName());

            // find the first element paintable
            while(firstpt != null && firstpt.getElement() == null &&
                  idx0 < pg.getPaintableCount())
            {
               idx0++;
               firstpt = pg.getPaintable(idx0);
            }

            String lastid = lastE.getFullName();
            // undo the element printing, if there are elements before the
            // last element
            if(firstpt.getElement() != null &&
               !firstpt.getElement().getFullName().equals(lastid) ||
               current > ocurrent) {
               for(int k = pg.getPaintableCount() - 1; k >= idx0; k--) {
                  ReportElement elem = pg.getPaintable(k).getElement();

                  if(elem != null && removeElems.contains(elem.getFullName())) {
                     pg.removePaintable(k);
                  }
                  else {
                     break;
                  }
               }

               ((BaseElement) lastE).resetPrint();

               // if element in main flow, adjust current idex
               int curr3 = current;
               int curr2 = current;

               for(; curr2 > 0 &&
                  !elements.elementAt(curr2).equals(lastE); curr2--) {
               }

               // don't decrement current in loop in case the element
               // is not found in elements (in section?)
               if(curr2 >= 0) {
                  current = curr2;
               }

               // @by mikec, in case of the elements between the first
               // reprint element and current element do not generate
               // any paintable(such as newline element), we should force
               // it to resetPrint, otherwise the result after keepwithnext
               // will be different from in the original page.
               // See bug1134496925798.
               for(int i = curr3; i >= current; i--) {
                  if(i >= 0 && i < elements.size()) {
                     BaseElement base = (BaseElement) elements.get(i);
                     base.resetPrint();
                  }
               }
            }
         }
      }

      return current;
   }

   /**
    * This method is provided to work around the jdk 1.1 win32 bug. The
    * PrintJob on win32 does not adjust the origin with the printer
    * margin, resulting printout being shifted to the right. We assume
    * a 0.25 inch margin if application is running on win32 with jdk1.1.6
    * and earlier (the bug should be fixed in jdk1.1.7). However, if
    * a printer is set to another margin value, this method must be
    * explicitly called to change it. Since this method will not be
    * needed once the bug is fixed in jdk, it will become obsolete
    * in the future. Since 0.25inch is the default printer margin on
    * most printers, this method should not need to be called in most
    * cases.
    * <p>
    * Since the same bug reappeared in jdk1.2 after being fixed
    * in jdk1.1.7, it is reactivated and will probably be needed
    * (unfortunately) much longer than its original anticipated usage.
    */
   public static synchronized void setPrinterMargin(Margin pmargin) {
      ReportSheet.g_pmargin = pmargin;
   }

   /**
    * Get the current printer margin. See setPrinterMargin().
    */
   public static Margin getPrinterMargin() {
      return g_pmargin;
   }

   /**
    * Get the Style Report version. The version is in dot separated format,
    * e.g. 1.3.1.
    * @return Style Report version.
    */
   public String getVersion() {
      return version;
   }

   /**
    * Set the Style Report version.
    */
   public void setVersion(String version) {
      this.version = version;
   }

   /**
    * Set the location of a css style sheet to be applied to the report.
    * @param cssLoc a url, a resource path or a file path.
    */
   public void setCSSLocation(String cssLoc) {
      cssLocation = cssLoc;
   }

   /**
    * Get the location of the css style sheet used by this report.
    */
   public String getCSSLocation() {
      return cssLocation;
   }

   /**
    * Get the css class of this report.
    */
   public String getCSSClass() {
      return cssClass;
   }

   /**
    * Set the css class of this report.
    * @param cssClass css class
    */
   public void setCSSClass(String cssClass) {
      this.cssClass = cssClass;
   }

   /**
    * Get the css id of this report.
    */
   public String getCSSId() {
      return cssId;
   }

   /**
    * Set the css id of this report.
    * @param cssId css id
    */
   public void setCSSId(String cssId) {
      this.cssId = cssId;
   }

   /**
    * Get the context name.
    */
   public String getContextName() {
      return name;
   }

   /**
    * Set the context name.
    */
   public void setContextName(String name) {
      this.name = name;
   }

   /**
    * Make a copy of this report.
    */
   @Override
   public Object clone() {
      return clone(true);
   }

   /**
    * Make a copy of this report.
    */
   public Object clone(boolean deep) {
      ReportSheet report = (ReportSheet) super.clone();
      return report;
   }

   /**
    * Copy report sheet to report
    * @param report the report copied to.
    * @param flag if true flag all copied elements as from template.
    */
   protected void copyReportSheet(ReportSheet report, boolean flag,
                                  boolean deep) {
      copyStyleCore(report, flag);

      report.topdir = topdir;
      report.size = size;
      report.orient = orient;
      report.cssLocation = cssLocation;
      report.pgStart = pgStart;
      report.pgTotal = pgTotal;
   }

   /**
    * Check the top report is single page report or not.
    */
   public boolean isSinglePageForTopReport() {
      String prop = getTopReport().getProperty("singlePage");
      return prop != null && prop.equals("true");
   }

   /**
    * Check the top report is export in single page or not.
    */
   public boolean isExportSinglePage() {
      String prop = getTopReport().getProperty("exportSinglePage");
      return prop != null && prop.equals("true");
   }

   {
      // set default properties
      setProperty("sortOnHeader", "true");
   }

   private Size size = DEFAULT_PAGE_SIZE;
   private int orient = PORTRAIT;
   private String topdir = null;
   private String cssLocation = null;
   private String cssClass = null;
   private String cssId = null;
   private Color cssBackgroundColor = null;
   private String name = null;
   private String version;
   private String unit;
   private boolean customPageSize;

   private static final Logger LOG =
      LoggerFactory.getLogger(ReportSheet.class);
}
