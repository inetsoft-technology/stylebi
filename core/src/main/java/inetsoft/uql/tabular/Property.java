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
    * Note that string values substituted into such a property are encoded
    * for JSON query text (as used by the MongoDB query property), not with
    * SQL quote doubling: punctuation, and the first character of a string
    * value, are written as <code>&#92;uXXXX</code>, which bson decodes back to
    * the exact value inside a quoted string, which fails to parse in
    * structural context, and which is kept as literal escape text inside a
    * {@code /regex/} literal; see {@code VarSQL.LiteralEscapeStyle#JSON}.
    * Number, Boolean and Date values are substituted unescaped so they keep
    * their type. Put string placeholders inside {@code '...'} strings (or
    * unquoted), leave numeric/boolean placeholders unquoted, and use
    * {@code {$regex: '...$(name)...'}} rather than a {@code /regex/} literal.
    * <p>
    * Values are never safe inside server-side JavaScript source
    * ({@code $where}, {@code $function.body}, {@code $accumulator}, mapReduce
    * functions): the decoded value becomes code. Pass values as data through
    * {@code $function.args} or use {@code $expr} operators instead, and
    * consider disabling server-side JavaScript in MongoDB
    * ({@code security.javascriptEnabled: false}).
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
