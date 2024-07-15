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
package inetsoft.report.internal.info;

import inetsoft.report.Size;
import inetsoft.report.StyleConstants;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * A TextBox is a rectangular area for printing text contents. It is
 * a painter element. This means it can be anchored and resized. Another
 * main difference between a text box and a text element is that textbox
 * does not flow around other elements, or flow across pages. It is normally
 * used to present small amount of text when text position needs to be
 * controlled.
 */
public class TextBoxElementInfo extends PainterElementInfo implements TextBasedInfo {
   /**
    * Construct a text box element info.
    */
   public TextBoxElementInfo() {
      super();
      setFont(Util.DEFAULT_FONT);
   }

   /**
    * Return the text in the text lens.
    */
   public String getText() {
      return this.text;
   }

   /**
    * Set the text contained in this text element.
    */
   public void setText(String text) {
      this.text = text;
   }

   /**
    * Set the border around this text box.
    * @param border line style in StyleConstants, e.g. THIN_LINE.
    */
   public void setBorder(int border) {
      dirty = dirty || this.border != border;
      this.border = border;
   }

   /**
    * Get the border around this text box.
    * @return border line style.
    */
   public int getBorder() {
      return border;
   }

   /**
    * Set the border color around this text box.
    */
   public void setBorderColor(Color color) {
      dirty = dirty || !Tool.equals(this.borderColor, color);
      this.borderColor = color;
   }

   /**
    * Get the border color around this text box.
    */
   public Color getBorderColor() {
      return borderColor;
   }

   /**
    * Set the individual border line styles. This overrides the default border
    * setting.
    * @param borders line styles defined in StyleConstants, e.g. THIN_LINE.
    */
   public void setBorders(Insets borders) {
      dirty = dirty || !Tool.equals(this.borders, borders);
      this.borders = borders;
   }

   /**
    * Get the individual border line styles.
    * @return border line style.
    */
   public Insets getBorders() {
      return this.borders;
   }

   /**
    * Set the textbox shape. One of StyleConstants.BOX_RECTANGLE or
    * StyleConstants.BOX_ROUNDED_RECTANGLE.
    * @param shape textbox shape option.
    */
   public void setShape(int shape) {
      dirty = dirty || this.shape != shape;
      this.shape = shape;
   }

   /**
    * Get the textbox shape.
    * @return the textbox shape.
    */
   public int getShape() {
      return this.shape;
   }

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   public boolean isJustify() {
      return this.justify;
   }

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   public void setJustify(boolean justify) {
      dirty = dirty || this.justify != justify;
      this.justify = justify;
   }

   /**
    * Get the text alignment.
    */
   public int getTextAlignment() {
      return this.talignment;
   }

   /**
    * Set the text alignment within the text box.
    */
   public void setTextAlignment(int align) {
      dirty = dirty || talignment != align;
      talignment = align;
   }

   /**
    * Set the highlight group setting.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return hg;
   }

   /**
    * Get the highlight group setting.
    */
   @Override
   public void setHighlightGroup(HighlightGroup group) {
      hg = group;
   }

   /**
    * Set the shadow option of this text box.
    */
   public void setShadow(boolean shadow) {
      dirty = dirty || this.shadow != shadow;
      this.shadow = shadow;
   }

   /**
    * Check the shadow option of this text box.
    */
   public boolean isShadow() {
      return shadow;
   }

   /**
    * Set the corner width and height for rounded rectangle shape.
    */
   public void setCornerSize(Dimension corner) {
      dirty = dirty || !Tool.equals(this.corner, corner);
      this.corner = corner;
   }

   /**
    * Get the corner width and height of rounded rectangle.
    */
   public Dimension getCornerSize() {
      return corner;
   }

   /**
    * Get text box padding space.
    */
   public Insets getPadding() {
      return padding;
   }

   /**
    * Set text box padding space.
    */
   public void setPadding(Insets padding) {
      dirty = dirty || !Tool.equals(this.padding, padding);
      this.padding = padding;
   }

   // super methods

   /**
    * Set the painter size
    */
   @Override
   public void setSize(Size size) {
      dirty = dirty || !Tool.equals(getSize(), size);
      super.setSize(size);
   }

   /**
    * Alignment, H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   @Override
   public void setAlignment(int alignment) {
      dirty = dirty || getAlignment() != (byte) alignment;
      super.setAlignment(alignment);
   }

   /**
    * Indentation in inches.
    */
   @Override
   public void setIndent(double indent) {
      dirty = dirty || getIndent() != (float) indent;
      super.setIndent(indent);
   }

   /**
    * Current font.
    */
   @Override
   public void setFont(Font font) {
      dirty = dirty || !Tool.equals(getFont(), font);
      super.setFont(font);
   }

   /**
    * Foreground color.
    */
   @Override
   public void setForeground(Color foreground) {
      dirty = dirty || !Tool.equals(getForeground(), foreground);
      super.setForeground(foreground);
   }

   /**
    * Background color.
    */
   @Override
   public void setBackground(Color background) {
      dirty = dirty || !Tool.equals(getBackground(), background);
      super.setBackground(background);
   }

   /**
    * Line spacing in pixels.
    */
   @Override
   public void setSpacing(int spacing) {
      dirty = dirty || getSpacing() != (short) spacing;
      super.setSpacing(spacing);
   }

   /**
    * Set the visibility of this element.
    * @param vis false to hide an element.
    */
   @Override
   public void setVisible(boolean vis) {
      dirty = dirty || isVisible() != vis;
      super.setVisible(vis);
   }

   /**
    * Set the keep with next flag of getVisible()this element.
    */
   @Override
   public void setKeepWithNext(boolean keep) {
      dirty = dirty || isKeepWithNext() != keep;
      super.setKeepWithNext(keep);
   }

   /**
    * Define the hyperlink target (anchor) of this element. If the target is
    * defined, hyperlinks can refered to the location of this element using
    * "#target" notation. This navigation is only used in Style Report
    * Enterprise Edition.
    */
   @Override
   public void setTarget(String target) {
      dirty = dirty || !Tool.equals(getTarget(), target);
      super.setTarget(target);
   }

   /**
    * Set the css class of the element.
    * @param elementClass the class name as defined in the css style sheet.
    */
   @Override
   public void setCSSClass(String elementClass) {
      dirty = dirty || !Tool.equals(getCSSClass(), elementClass);
      super.setCSSClass(elementClass);
   }

   /**
    * Get the presenter to be used in this element.
    */
   @Override
   public PresenterRef getPresenter() {
      return presenter;
   }

   /**
    * Set the presenter to be used in this element.
    */
   @Override
   public void setPresenter(PresenterRef ref) {
      this.presenter = ref;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      TextBoxElementInfo info2 = (TextBoxElementInfo) super.clone();

      if(hg != null) {
         info2.hg = (HighlightGroup) hg.clone();
      }

      if(presenter != null) {
         info2.presenter = (PresenterRef) presenter.clone();
      }

      return info2;
   }

   /**
    * Set the dirty flag.
    */
   public void setDirty(boolean dirty) {
      this.dirty = dirty;
   }

   /**
    * Check if this object has changed.
    */
   public boolean isDirty() {
      return dirty;
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   @Override
   public String getTagName() {
      return "textBoxElementInfo";
   }

   /**
    * Create an ElementInfo.
    */
   @Override
   protected ElementInfo create() {
      return new TextBoxElementInfo();
   }

   /**
    * Get the default info in section.
    */
   @Override
   public  ElementInfo createInSection(boolean autoResize, String name) {
      TextBoxElementInfo info =
         (TextBoxElementInfo) super.createInSection(autoResize, name);

      if(autoResize) {
         info.setProperty("grow", "true");
      }

      info.borders =
        new Insets(StyleConstants.THIN_LINE, StyleConstants.THIN_LINE,
                   StyleConstants.THIN_LINE, StyleConstants.THIN_LINE);
      info.borderColor = Color.decode("-16777216");
      info.text = "Label";
      return info;
   }

   @Override
   public void copy(ElementInfo info) {
      super.copy(info);

      if(info instanceof TextElementInfo) {
         justify = ((TextElementInfo) info).isJustify();
      }
      else if(info instanceof TextBoxElementInfo) {
         justify = ((TextBoxElementInfo) info).justify;
      }
   }

   // used for optimization to allow sharing ElementInfo in TextPainter
   private boolean dirty = false;

   // text saved for serialization, changes on this text will not effect on
   // the text lens reside in TextElementDef. If user want to change the content
   // in the TextElementDef, must call TextElementDef.setText explicitily.
   private String text;
   private HighlightGroup hg;

   private boolean justify;
   private int border = StyleConstants.THIN_LINE;
   // text alignment
   private int talignment = StyleConstants.H_LEFT | StyleConstants.V_TOP;
   private int shape = StyleConstants.BOX_RECTANGLE;
   private Insets borders = null;// individual border setting
   private boolean shadow = false; // shadow
   private Insets padding = new Insets(0, 0, 0, 0); // padding
   private Dimension corner = null; // corner size
   private Color borderColor;
   private PresenterRef presenter;

   private static final Logger LOG =
      LoggerFactory.getLogger(TextBoxElementInfo.class);
}
