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

import inetsoft.uql.jdbc.util.VarSQL;

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
    * Regular expression validation. When more than one pattern is given they are positional
    * alternatives, not conjunctive constraints: the first applies to a folder selection and the
    * second to a file selection, so a value is valid if it matches the pattern for its kind.
    */
   String[] pattern() default {};

   /**
    * True if this is query text (SQL, CQL, JSON, ...) rather than a URL or
    * other plain text. This affects how variables are replaced: the property
    * goes through {@code VarSQL} (quote-aware, a value inside a quoted
    * literal is escaped, an unquoted string value becomes an escaped
    * literal, {@code $(@name)} embeds raw text) using the escaping chosen by
    * {@link #literalEscapeStyle()}; a String property without this flag has
    * its values URL-encoded instead (Bug #78594).
    * <p>
    * With the default {@link #literalEscapeStyle()} (JSON), string values
    * substituted into such a property are encoded
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
    * How a variable value is escaped when it is spliced into a {@link #sql()}
    * property as a string literal. Must match the target engine's literal
    * syntax: {@code JSON} for MongoDB query text (the default),
    * {@code SQL} (quote doubling) for ANSI-style dialects such as CQL, and
    * {@code BACKSLASH} for dialects that escape with a backslash and do not
    * accept {@code ''} (HiveQL, OrientDB SQL). Ignored unless {@link #sql()}
    * is true (Bug #78594).
    */
   VarSQL.LiteralEscapeStyle literalEscapeStyle() default VarSQL.LiteralEscapeStyle.JSON;

   /**
    * With {@code literalEscapeStyle = SQL}, also double backslashes in a
    * spliced value. Use it when backslash is an escape character in the
    * dialect, or when the dialect is not known: quote and backslash doubling
    * together cannot end the literal whichever way the engine reads them, at
    * worst a backslash in the value is doubled (Bug #78594).
    */
   boolean backslashIsEscapeChar() default false;

   /**
    * True if backtick-quoted names are quoted identifiers in the dialect of a
    * {@link #sql()} property with a non-JSON {@link #literalEscapeStyle()}
    * (HiveQL, OrientDB SQL): quotes inside backticks then do not start a
    * string literal, and a value spliced inside backticks has its backticks
    * and trailing backslashes removed so it cannot end the identifier
    * (Bug #78594).
    */
   boolean backtickIdentifiers() default false;

   /**
    * The comment syntax of the language of a {@link #sql()} property, so
    * quotes and placeholders inside comments are ignored: {@code SQL}
    * ({@code --} and <code>/* *&#47;</code>, the default), {@code HASH}
    * ({@code #}, R) or {@code C_STYLE} ({@code //} and <code>/* *&#47;</code>,
    * JavaScript). It must match the language, or an apostrophe in a comment
    * can put a later quoted placeholder outside its literal. Lines that start
    * with the line comment token are also skipped when the property's
    * variables are listed. Ignored unless {@link #sql()} is true
    * (Bug #78757).
    */
   VarSQL.CommentSyntax commentSyntax() default VarSQL.CommentSyntax.SQL;

   /**
    * With the default JSON {@link #literalEscapeStyle()}, copy backslashes in
    * the template text as written instead of dropping them; inside a
    * {@code '...'} or {@code "..."} literal a backslash still escapes the next
    * character for quote tracking ({@code VarSQL.TemplateBackslash#LITERAL_ESCAPE}).
    * Set it for JavaScript text, where escapes in string and regex literals
    * (a backslash-n, a backslash-quote, a backslash-d in a regex) must reach
    * the engine unchanged. The other styles always
    * keep template backslashes. The MongoDB query keeps the default, which
    * drops them (Bug #78757).
    */
   boolean keepTemplateBackslashes() default false;

   /**
    * True if replacing date variable using javascript date (timestamp) format.
    */
   boolean jsDateFormat() default false;

   /**
    * True if replacing environment variables for this property
    */
   boolean checkEnvVariables() default false;
}
