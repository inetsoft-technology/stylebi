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
package inetsoft.uql.tabular;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static inetsoft.uql.tabular.ViewAlign.AUTO;
import static inetsoft.uql.tabular.ViewType.COMPONENT;

/**
 * Annotation for representing elements inside a container (@View1 PANEL).
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface View2 {
   /**
    * The type of the view element
    */
   ViewType type() default COMPONENT;

   /**
    * Static label text or title for container
    */
   String text() default "";

   /**
    * Whether to wrap text in a label. Only for label view type.
    */
   boolean wrap() default false;

   /**
    * Foreground color
    */
   String color() default "";

   /**
    * Text font
    */
   String font() default "";

   /**
    * The property name
    */
   String value() default "";

   /**
    * Row number in layout grid
    */
   int row() default -1;

   /**
    * Column number in layout grid
    */
   int col() default -1;

   /**
    * The number of rows to span in grid
    */
   int rowspan() default 1;

   /**
    * The number of columns to span in grid
    */
   int colspan() default 1;

   /**
    * Horizontal alignment
    */
   ViewAlign align() default AUTO;

   /**
    * Vertical alignment
    */
   ViewAlign verticalAlign() default AUTO;

   /**
    * Left padding space
    */
   int paddingLeft() default 1;

   /**
    * Right padding space
    */
   int paddingRight() default 1;

   /**
    * Top padding space
    */
   int paddingTop() default 3;

   /**
    * Bottom padding space
    */
   int paddingBottom() default 3;

   /**
    * Method to call to see if this element should be visible
    */
   String visibleMethod() default "";

   /**
    * Stores button information.
    * If the view type is BUTTON then this needs to be defined.
    */
   Button button() default @Button;
}
