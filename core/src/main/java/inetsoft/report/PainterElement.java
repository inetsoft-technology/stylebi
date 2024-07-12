/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report;

import java.awt.*;
import java.util.Enumeration;

/**
 * A painter element is an element with fixed size. It can be anchored
 * to the previous element. The painter element is the base class for
 * chart, textbox, and image.
 */
public interface PainterElement extends ReportElement, HyperlinkSupport {
   /**
    * Get the painter object.
    */
   public Painter getPainter();
   
   /**
    * Set the painter object. The painter object is responsible for 
    * painting the painter element on a report.
    */
   public void setPainter(Painter painter);
   
   /**
    * Get the painter external space.
    */
   public Insets getMargin();
   
   /**
    * Set the painter external space.
    */
   public void setMargin(Insets margin);
   
   /**
    * Set the size of the element.
    * @param size size in inches.
    */
   public void setSize(Size size);
   
   /**
    * Get the size of the element.
    * @return size in inches.
    */
   public Size getSize();
   
   /**
    * Set the wrapping style of this element. Wrapping styles are defined
    * in ReportSheet, e.g. ReportSheet.WRAP_BOTH. Wrappign style determines
    * how other elements are positioned around a painter element. Wrapping
    * is only performed in the painter is anchored.
    */
   public void setWrapping(int wrapping);
   
   /**
    * Get the wrapping style of this element.
    */
   public int getWrapping();
   
   /**
    * Set the layout option. Breakable or non-break. If the option is
    * ReportSheet.PAINTER_BREAKABLE, the painter may be broken up across
    * pages if the current page does not have enough space to accomodate
    * the painter. Otherwise the painter is always drawn as one piece.
    */
   public void setLayout(int opt);
   
   /**
    * Get the layout option of this element.
    */
   public int getLayout();
   
   /**
    * Get the painter anchor (distance from last element and left of report).
    */
   public Position getAnchor();
   
   /**
    * Set painter anchor. An anchor defines the distance of the painter 
    * to the left of the page, and the distance to the bottom of the last
    * element (if anchor.y is positive), or distance to the top of the
    * current line (if anchor.y is negative).
    */
   public void setAnchor(Position anchor);
   
   /**
    * Get the rotation in degrees.
    */
   public int getRotation();
   
   /**
    * Set the rotation degrees. It can be 0, 90, or 270.
    */
   public void setRotation(int degree);
   
   /**
    * Get the hyper link on this element for the specified area.
    */
   public Hyperlink getHyperlink(Shape shape);
   
   /**
    * Set the hyper link of this element for the specified area.
    */
   public void setHyperlink(Shape shape, Hyperlink link);
   
   /**
    * Return the areas that have a hyperlink defined. The hyperlink area is
    * similar to imagemap in HTML. Each sub-area in a painter/image can have
    * a different hyperlink.
    * @return enumeration of Shape objects.
    */
   public Enumeration getHyperlinkAreas();
}

