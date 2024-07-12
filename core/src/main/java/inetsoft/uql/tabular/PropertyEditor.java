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

import java.lang.annotation.*;

/**
 * This annotation can be applied to a property (getter) method to
 * define the editor for the bean property.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface PropertyEditor {
   /**
    * The number of rows in a text field.
    */
   int rows() default 1;

   /**
    * The number of columns in a text field.
    */
   int columns() default 0;

   /**
    * The tag values used to populate a drop down list.
    */
   String[] tags() default {};

   /**
    * The labels used for the tags in the drop down list.
    */
   String[] labels() default {};

   /**
    * The name of the method used to retrieve dynamic tag values. It may return
    * an array of strings (tags), or an array of pairs (String[][]), with each
    * pair being the tag and label.
    */
   String tagsMethod() default "";

   /**
    * The list of properties on which the editor depends.
    */
   String[] dependsOn() default {};

   /**
    * The name of the method used to determine if the editor should be enabled.
    */
   String enabledMethod() default "";

   /**
    * The enabled status of the editor
    */
   boolean enabled() default true;

   /**
    * Enabled line wrapping
    */
   boolean lineWrap() default false;

   /**
    * The fully-qualified class name of the custom editor.
    */
   String customEditor() default "";

   /**
    * Editor-specific properties.
    */
   EditorProperty[] editorProperties() default {};

   /**
    * Flag indicating whether this is an autocomplete editor.
    * tags or tagsMethod need to be specified for the suggestions
    */
   boolean autocomplete() default false;
}
