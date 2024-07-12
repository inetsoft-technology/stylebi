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
 * This annotation can be applied to a property method (getter) to mark
 * it as a bean property.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Property {
   // display label for the property
   String label() default "";

   // true if this value is a password
   boolean password() default false;

   /**
    * Check if null or empty value allowed.
    */
   boolean required() default false;

   /**
    * Minimum value.
    */
   double min() default Double.NaN;

   /**
    * Maximum value.
    */
   double max() default Double.NaN;

   /**
    * Regular expression validation.
    */
   String[] pattern() default {};

   /**
    * True if this is a SQL string. This affects how variables are replaced.
    */
   boolean sql() default false;

   /**
    * True if replacing date variable using javascript date (timestamp) format.
    */
   boolean jsDateFormat() default false;

   /**
    * True if replacing environment variables for this property
    */
   boolean checkEnvVariables() default false;
}
