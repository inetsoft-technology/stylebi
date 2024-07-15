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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;

/**
 * Report context (all attributes affecting the elements). This is mostly
 * used internally to handle the report context setting. Applications
 * should call ReportSheet methods to change the current context when
 * populating a report.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Context implements ReportElement, Serializable {
   /**
    * Save the current context of a report.
    */
   public Context(ReportSheet report) {
      this.report = report;
      c_align = report.getCurrentAlignment();
      c_indent = report.getCurrentIndent();
      c_spacing = report.getCurrentLineSpacing();
      c_font = report.getCurrentFont();
      c_fg = report.getCurrentForeground();
      c_bg = report.getCurrentBackground();
      c_autosize = report.getCurrentTableLayout();
      c_policy = report.getCurrentPainterLayout();
      c_padding = report.getCurrentCellPadding();
      c_tblwidth = report.getCurrentTableWidth();
      c_tabstops = report.getCurrentTabStops();
      c_textadv = report.getCurrentTextAdvance();
      c_orphan = report.isCurrentOrphanControl();
   }

   /**
    * Save the current context from an element.
    */
   public Context(ReportElement elem) {
      setContext(elem);
   }

   /**
    * Set the attributes. All attribute values are copied from the element.
    */
   @Override
   public void setContext(ReportElement elem) {
       c_align = elem.getAlignment();
      c_indent = elem.getIndent();
      c_spacing = elem.getSpacing();
      c_font = elem.getFont();
      c_fg = elem.getForeground();
      c_bg = elem.getBackground();
   }

   /**
    * Restore the context in its associated report.
    */
   public void restore() {
      report.setCurrentAlignment(c_align);
      report.setCurrentIndent(c_indent);
      report.setCurrentLineSpacing(c_spacing);
      report.setCurrentFont(c_font);
      report.setCurrentForeground(c_fg);
      report.setCurrentBackground(c_bg);
      report.setCurrentTableLayout(c_autosize);
      report.setCurrentPainterLayout(c_policy);
      report.setCurrentCellPadding(c_padding);
      report.setCurrentTableWidth(c_tblwidth);
      report.setCurrentTabStops(c_tabstops);
      report.setCurrentTextAdvance(c_textadv);
      report.setCurrentOrphanControl(c_orphan);
   }

   /**
    * Get the id of this element.
    */
   @Override
   public String getID() {
      return "";
   }

   /**
    * This method is ignored.
    */
   @Override
   public void setID(String id) {
   }

   /**
    * Get the id of this element.
    */
   @Override
   public String getFullName() {
      return "";
   }

   /**
    * This method is ignored.
    */
   @Override
   public void setFullName(String id) {
   }

   /**
    * This method is ignored.
    */
   @Override
   public boolean isVisible() {
      return true;
   }

   /**
    * This method is ignored.
    */
   @Override
   public void setVisible(boolean vis) {
   }

   /**
    * Return the element type.
    */
   @Override
   public String getType() {
      return "ReportSheet";
   }

   /**
    * Get alignment setting.
    */
   @Override
   public int getAlignment() {
      return c_align;
   }

   /**
    * Set alignment setting.
    */
   @Override
   public void setAlignment(int align) {
      c_align = align;
   }

   /**
    * Get indent in inches.
    */
   @Override
   public double getIndent() {
      return c_indent;
   }

   /**
    * Set indent in inches.
    */
   @Override
   public void setIndent(double indent) {
      c_indent = indent;
   }

   /**
    * Get line spacing.
    */
   @Override
   public int getSpacing() {
      return c_spacing;
   }

   /**
    * Set line spacing.
    */
   @Override
   public void setSpacing(int spacing) {
      c_spacing = spacing;
   }

   /**
    * Get font setting.
    */
   @Override
   public Font getFont() {
      return c_font;
   }

   /**
    * Set font setting.
    */
   @Override
   public void setFont(Font font) {
      c_font = font;
   }

   /**
    * Get foreground color.
    */
   @Override
   public Color getForeground() {
      return c_fg;
   }

   /**
    * Set foreground color.
    */
   @Override
   public void setForeground(Color fg) {
      c_fg = fg;
   }

   /**
    * Get background color.
    */
   @Override
   public Color getBackground() {
      return c_bg;
   }

   /**
    * Set background color.
    */
   @Override
   public void setBackground(Color bg) {
      c_bg = bg;
   }

   /**
    * Return true if RightToLeft to TOC element.
    */
   public boolean isRightToLeft() {
      return this.c_rightToLeft;
   }

   /**
    * Set true if RightToLeft to TOC element
    */
   public void setRightToLeft(boolean rightToLeft) {
      this.c_rightToLeft = rightToLeft;
   }

   /**
    * Get table layout setting.
    */
   public int getTableLayout() {
      return c_autosize;
   }

   /**
    * Set table layout setting.
    */
   public void setTableLayout(int autosize) {
      c_autosize = autosize;
   }

   /**
    * Get painter layout setting.
    */
   public int getPainterLayout() {
      return c_policy;
   }

   /**
    * Set painter layout setting.
    */
   public void setPainterLayout(int policy) {
      c_policy = policy;
   }

   /**
    * Get cell padding space.
    */
   public Insets getCellPadding() {
      return c_padding;
   }

   /**
    * Set cell padding space.
    */
   public void setCellPadding(Insets padding) {
      c_padding = padding;
   }

   /**
    * Get table width setting.
    */
   public double getTableWidth() {
      return c_tblwidth;
   }

   /**
    * Set table width setting.
    */
   public void setTableWidth(double tblwidth) {
      c_tblwidth = tblwidth;
   }

   /**
    * Get tab stops.
    */
   public double[] getTabStops() {
      return c_tabstops;
   }

   /**
    * Set tab stops.
    */
   public void setTabStops(double[] tabstops) {
      c_tabstops = tabstops;
   }

   /**
    * Get text advance amount in pixels.
    */
   public int getTextAdvance() {
      return c_textadv;
   }

   /**
    * Set text advance.
    */
   public void setTextAdvance(int adv) {
      c_textadv = adv;
   }

   /**
    * Check if widow/orphan control is on.
    */
   public boolean isOrphanControl() {
      return c_orphan;
   }

   /**
    * Set widow/orphan control.
    */
   public void setOrphanControl(boolean orphan) {
      c_orphan = orphan;
   }

   /**
    * Check if this element should be kept on the same page as the next
    * element.
    */
   @Override
   public boolean isKeepWithNext() {
      return c_keep;
   }

   /**
    * Set keep with next.
    */
   @Override
   public void setKeepWithNext(boolean keep) {
      c_keep = keep;
   }

   /**
    * Get an property of this element. The properties are used to extend
    * the report elements and allows additional information to be attached
    * to the elements.
    * @param name property name.
    * @return property value.
    */
   @Override
   public String getProperty(String name) {
      return null;
   }

   /**
    * Set an property value.
    * @param name property name.
    * @param attr property value. Use null value to remove an
    * property.
    */
   @Override
   public void setProperty(String name, String attr) {
   }

   /**
    * Set an user object. The object must be serializable.
    */
   @Override
   public void setUserObject(Object obj) {
   }

   /**
    * Get the user object.
    */
   @Override
   public Object getUserObject() {
      return null;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(CloneNotSupportedException ex) {
         LOG.error("Failed to clone context", ex);
      }

      return null;
   }

   @Override
   public Size getPreferredSize() {
      return null;
   }

   /**
    * Set the css class of the element.
    * @param elementClass the name of the class as defined in the
    * css style sheet.
    */
   @Override
   public void setCSSClass(String elementClass) {
      c_cssClass = elementClass;
   }

   /**
    * Get the css class of the element
    */
   @Override
   public String getCSSClass() {
      return c_cssClass;
   }

   /**
    * Apply CSS Style to this report element.
    */
   @Override
   public void applyStyle() {
      // do nothing
   }

   /**
    * CSS Type to be used when styling this element.  Intended to be overridden.
    */
   @Override
   public String getCSSType() {
      return null;
   }

   @Override
   public ReportSheet getReport() {
      return report;
   }

   private ReportSheet report;
   private int c_align;
   private double c_indent;
   private int c_spacing;
   private Font c_font;
   private Color c_fg;
   private Color c_bg;
   private boolean c_rightToLeft;
   private int c_autosize;
   private int c_policy;
   private Insets c_padding;
   private double c_tblwidth;
   private double[] c_tabstops;
   private int c_textadv;
   private boolean c_orphan;
   private boolean c_keep;
   private String c_cssClass = "";

   private static final Logger LOG = LoggerFactory.getLogger(Context.class);
}
