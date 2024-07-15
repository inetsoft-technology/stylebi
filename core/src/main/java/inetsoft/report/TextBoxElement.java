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

import inetsoft.report.internal.BorderedElement;

import java.awt.*;

/**
 * A TextBox is a rectangular area for printing text contents. It is
 * a painter element. This means it can be anchored and resized. Another
 * main difference between a text box and a text element is that textbox
 * does not flow around other elements, or flow across pages. It is normally
 * used to present small amount of text when text position needs to be
 * controlled.
 */
public interface TextBoxElement extends PainterElement, BorderedElement {
   /**
    * Set the border around this text box.
    * @param border line style in StyleConstants, e.g. THIN_LINE.
    */
   public void setBorder(int border);

   /**
    * Get the border around this text box.
    * @return border line style.
    */
   public int getBorder();

   /**
    * Set the border color. If the border color is not set, the foreground
    * color is used to draw the border.
    */
   public void setBorderColor(Color color);

   /**
    * Get the border color.
    */
   public Color getBorderColor();

   /**
    * Set the shadow option of this text box. If shadow is turned on,
    * a drop shadow is added to the text box.
    */
   public void setShadow(boolean shadow);

   /**
    * Check the shadow option of this text box.
    */
   public boolean isShadow();

   /**
    * Set the textbox shape. One of StyleConstants.BOX_RECTANGLE or
    * StyleConstants.BOX_ROUNDED_RECTANGLE.
    * @param shape textbox shape option.
    */
   public void setShape(int shape);

   /**
    * Get the textbox shape.
    * @return the textbox shape.
    */
   public int getShape();

   /**
    * Set the corner width and height for rounded rectangle shape.
    */
   public void setCornerSize(Dimension corner);

   /**
    * Get the corner width and height of rounded rectangle.
    */
   public Dimension getCornerSize();

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   public boolean isJustify();

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   public void setJustify(boolean justify);

   /**
    * Return the text in the text lens.
    */
   public String getText();

   /**
    * Set the text contained in this text element.
    */
   public void setText(String text);

   /**
    * Set the text contained in this text element.
    */
   public void setTextLens(TextLens text);

   /**
    * Return the text lens of this text element.
    */
   public TextLens getTextLens();

   /**
    * Get the text alignment.
    */
   public int getTextAlignment();

   /**
    * Set the text alignment within the text box.
    */
   public void setTextAlignment(int align);

   /**
    * Get box padding space.
    */
   public Insets getPadding();

   /**
    * Set box padding space.
    */
   public void setPadding(Insets padding);

   /**
    * Get the data in object, it's used for binding.
    */
   public Object getData();

   /**
    * Set the data in object, it's used for binding.
    */
   public void setData(Object val);

   /**
    * Get the textID, which is used for i18n support.
    */
   public String getTextID();

   /**
    * Set the textID, which is used for i18n support.
    */
   public void setTextID(String textID);
}

