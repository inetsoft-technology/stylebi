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

import java.awt.*;
import java.util.Enumeration;

/**
 * This is the base class of all document element classes. It contains
 * all common attributes. The attributes are copied from the document
 * when the element is created, so if the attributes change after the
 * element is created, the element is not affected.
 */
public interface ReportElement extends java.io.Serializable, Cloneable {
   /**
    * Property name of a query if the query binding is defined for this
    * element. This is normally set by the designer.
    * For programmatic report creation, data query and aggregation
    * can be done through API directly.
    */
   public static final String QUERY = "query";
   /**
    * Property name of a node path selection specification.
    * This is normally set by the designer.
    * For programmatic report creation, data query and aggregation
    * can be done through API directly.
    */
   public static final String XNODEPATH = "xnodepath";
   /**
    * Property used to control if an element can grow inside a section.
    * It can be set to "true" or "false".
    */
   public static final String GROW = "grow";

   /**
    * Property used to control if an element is autosize.
    * It can be set to "true" or "false".
    */
   public static final String AUTOSIZE = "autosize";

   /**
    * Get the id of this element.
    */
   public String getID();

   /**
    * Set the id of this element. The element ID must be unique across
    * an entire report. The default ID generated for each element is
    * always unique. If the ID is manually changed, the caller must
    * make sure the new ID is unique.
    */
   public void setID(String id);

   /**
    * Get the full name of this element. If this element is inside a bean,
    * the name contains the bean ID appended with the element ID. It
    * can be used together with element ID to uniquely identify an element
    * in a report (elements in a bean may have same ID as the element in
    * the report that uses the bean).
    */
   public String getFullName();

   /**
    * Set the full name of this element. If the beans are nested, the name of
    * the bean (bean element ID) are concatenated using a dot, e.g.
    * Bean1.Bean2.Text1.
    */
   public void setFullName(String name);

   /**
    * Get the horizontal alignment. The alignment is defined in StyleConstants
    * H_LEFT, H_CENTER, H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   public int getAlignment();

   /**
    * Set the horizontal alignment. The value is one of H_LEFT, H_CENTER,
    * H_RIGHT, and V_TOP, V_CENTER, V_RIGHT.
    */
   public void setAlignment(int alignment);

   /**
    * Get the element indentation in inches.
    */
   public double getIndent();

   /**
    * Set the element indentation in inches.
    */
   public void setIndent(double indent);

   /**
    * Get element font.
    */
   public Font getFont();

   /**
    * Set element font.
    */
   public void setFont(Font font);

   /**
    * Get element foreground color.
    */
   public Color getForeground();

   /**
    * Set element foreground color.
    */
   public void setForeground(Color foreground);

   /**
    * Get element background color.
    */
   public Color getBackground();

   /**
    * Set element background color.
    */
   public void setBackground(Color background);

   /**
    * Get element line spacing in pixels.
    */
   public int getSpacing();

   /**
    * Set element line spacing in pixels.
    */
   public void setSpacing(int spacing);

   /**
    * Check if this element is visible. Non-visible elements are
    * not printed.
    * @return true if element is visible.
    */
   public boolean isVisible();

   /**
    * Set the visibility of this element.
    * @param vis false to hide an element.
    */
   public void setVisible(boolean vis);

   /**
    * Check if this element should be kept on the same page as the next
    * element.
    */
   public boolean isKeepWithNext();

   /**
    * Set the keep with next flag of this element. If this is true,
    * This element is always printed on the same page as the next element.
    */
   public void setKeepWithNext(boolean keep);

   /**
    * Get an property of this element. The properties are used to extend
    * the report elements and allows additional information to be attached
    * to the elements.
    * @param name property name.
    * @return property value.
    */
   public String getProperty(String name);

   /**
    * Set an property value.
    * @param name property name.
    * @param attr property value. Use null value to remove an
    * property.
    */
   public void setProperty(String name, String attr);

   /**
    * Set the attributes of this element.
    */
   public void setContext(ReportElement elem);

   /**
    * Return the element type.
    */
   public String getType();

   /**
    * Return the size that is needed for this element.
    */
   public Size getPreferredSize();

   /**
    * Set an user object. The object must be serializable.
    */
   public void setUserObject(Object obj);

   /**
    * Get the user object.
    */
   public Object getUserObject();

   /**
    * Set the css class of the element.
    * @param elementClass class name as defined in the css style sheet.
    */
   public void setCSSClass(String elementClass);

   /**
    * Get the css class of the element
    */
   public String getCSSClass();

   /**
    * Create a clone of this object.
    */
   public Object clone();

   /**
    * Get the css type of the element
    */
   public String getCSSType();

   /**
    * Apply CSS styling to this report element (except for the user-set stuff)
    */
   public void applyStyle();

   /**
    * Get the report this element is contained in.
    */
   public ReportSheet getReport();

   /**
    * Check if the element has been disposed
    */
   default boolean isDisposed() {
      return false;
   }
}

