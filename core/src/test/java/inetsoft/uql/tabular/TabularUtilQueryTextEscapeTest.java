/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.uql.VariableTable;
import inetsoft.uql.jdbc.util.VarSQL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78594: query text of the SQL-like tabular connectors (CQL, HiveQL,
 * OrientDB SQL, Aerospike SQL) was URL-encoded instead of escaped for the
 * dialect's string literal. {@code @Property(sql=true)} properties now choose
 * their literal escape style; plain String properties (URLs) keep the
 * URL-encoding added for #71366.
 * <p>
 * The {@link #lex} helper reads the generated literal the way each family of
 * engines does, to show the value stays inside one literal and decodes back to
 * the input.
 */
@Tag("core")
class TabularUtilQueryTextEscapeTest {
   private static final String QUOTED = "SELECT * FROM t WHERE name = '$(p)'";
   private static final String PREFIX = "SELECT * FROM t WHERE name = ";
   private static final String[] VALUES = {
      "x'OR(1)IN(1)OR'", "O'Brien", "x\\'/**/OR/**/1=1--", "x\\", "a\"b", "a b=c&d", "\\\\''\\"
   };

   // ---- SQL doubling (CQL) --------------------------------------------------

   @Test
   void sqlStyle_breakOut_staysInLiteral() {
      assertEquals(PREFIX + "'x''OR(1)IN(1)OR'''", replace(new CqlBean(), QUOTED, "x'OR(1)IN(1)OR'"));
   }

   @Test
   void sqlStyle_obrien_roundTrips() {
      assertEquals(PREFIX + "'O''Brien'", replace(new CqlBean(), QUOTED, "O'Brien"));
   }

   @Test
   void sqlStyle_everyValue_isOneAnsiLiteralWithTheExactValue() {
      for(String value : VALUES) {
         String out = replace(new CqlBean(), QUOTED, value);
         assertEquals(List.of(value), lex(out.substring(PREFIX.length()), Lexer.ANSI), value);
      }
   }

   // ---- backslash escapes (HiveQL, OrientDB) --------------------------------

   @Test
   void backslashStyle_breakOut_staysInLiteral() {
      assertEquals(PREFIX + "'x\\'OR(1)IN(1)OR\\''",
                   replace(new BackslashBean(), QUOTED, "x'OR(1)IN(1)OR'"));
      assertEquals(PREFIX + "'x\\\\\\'/**/OR/**/1=1--'",
                   replace(new BackslashBean(), QUOTED, "x\\'/**/OR/**/1=1--"));
   }

   @Test
   void backslashStyle_obrien_roundTrips() {
      assertEquals(PREFIX + "'O\\'Brien'", replace(new BackslashBean(), QUOTED, "O'Brien"));
   }

   @Test
   void backslashStyle_everyValue_isOneHiveLiteralWithTheExactValue() {
      for(String value : VALUES) {
         String out = replace(new BackslashBean(), QUOTED, value);
         assertEquals(List.of(value), lex(out.substring(PREFIX.length()), Lexer.BACKSLASH), value);
         // double-quoted literal too
         out = replace(new BackslashBean(), "SELECT * FROM t WHERE name = \"$(p)\"", value);
         assertEquals(List.of(value), lex(out.substring(PREFIX.length()), Lexer.BACKSLASH), value);
      }
   }

   // ---- unknown dialect (Aerospike): quote + backslash doubling ------------

   @Test
   void unknownDialect_neverBreaksOutUnderAnyLexer() {
      for(String value : VALUES) {
         String lit = replace(new UnknownDialectBean(), QUOTED, value).substring(PREFIX.length());

         for(Lexer lexer : Lexer.values()) {
            assertNotNull(lex(lit, lexer), lexer + ": " + lit);
         }

         // ANSI and MySQL-style dialects read back the exact value except
         // that a backslash may be doubled
         assertEquals(List.of(value), lex(lit, Lexer.MYSQL), value);
      }
   }

   // ---- encoding no longer applied to query text ----------------------------

   @Test
   void queryText_spaceEqualsAmpersand_notPercentEncoded() {
      assertEquals(PREFIX + "'a b=c&d'", replace(new CqlBean(), QUOTED, "a b=c&d"));
      assertEquals(PREFIX + "'a b=c&d'", replace(new BackslashBean(), QUOTED, "a b=c&d"));
   }

   // ---- unquoted placeholders (VarSQL STRING semantics, as for JDBC/Mongo) --

   @Test
   void unquotedStringPlaceholder_becomesEscapedLiteral() {
      assertEquals("SELECT * FROM t WHERE id =  '1/**/OR/**/(1)IN(1)'",
                   replace(new CqlBean(), "SELECT * FROM t WHERE id = $(p)", "1/**/OR/**/(1)IN(1)"));
      assertEquals("SELECT * FROM t WHERE id =  'x\\' OR \\'1'",
                   replace(new BackslashBean(), "SELECT * FROM t WHERE id = $(p)", "x' OR '1"));
   }

   @Test
   void unquotedNumberPlaceholder_staysRaw() {
      assertEquals("SELECT * FROM t LIMIT  5",
                   replace(new CqlBean(), "SELECT * FROM t LIMIT $(p)", 5));
   }

   @Test
   void rawEmbedPlaceholder_isNotEscaped() {
      assertEquals("SELECT * FROM my_table",
                   replace(new CqlBean(), "SELECT * FROM $(@p)", "my_table"));
   }

   // ---- arrays ---------------------------------------------------------------

   @Test
   void arrayInList_isListOfEscapedLiterals() {
      Object value = new String[] { "a'b", "c d" };
      assertEquals("SELECT * FROM t WHERE name IN ( 'a''b' , 'c d' )",
                   replace(new CqlBean(), "SELECT * FROM t WHERE name IN ($(p))", value));
      assertEquals("SELECT * FROM t WHERE name IN ( 'a\\'b' , 'c d' )",
                   replace(new BackslashBean(), "SELECT * FROM t WHERE name IN ($(p))", value));
   }

   @Test
   void arrayInsideQuotes_isJoinedAndEscaped() {
      // a single prompt value containing a comma arrives as an array
      Object value = new String[] { "Smith", " O'Brien" };
      assertEquals(PREFIX + "'Smith, O''Brien'", replace(new CqlBean(), QUOTED, value));
      assertEquals(PREFIX + "'Smith, O\\'Brien'", replace(new BackslashBean(), QUOTED, value));
   }

   // ---- date variable keeps its query date format ---------------------------

   @Test
   void dateWithFormat_isFormattedThenQuoted() {
      VariableTable vars = new VariableTable();
      vars.put("d", java.sql.Date.valueOf("2024-01-02"));
      vars.putFormat("d", "yyyy/MM/dd");

      CqlBean bean = new CqlBean();
      bean.setQuery("SELECT * FROM t WHERE d = '$(d)' OR d = $(d)");
      TabularUtil.replaceVariables(bean, vars);
      assertEquals("SELECT * FROM t WHERE d = '2024/01/02' OR d =  '2024/01/02'", bean.getQuery());
      // the caller's table is not modified
      assertEquals("yyyy/MM/dd", vars.getFormat("d"));
      assertInstanceOf(java.sql.Date.class, getQuietly(vars, "d"));
   }

   // ---- URL properties keep URL-encoding (#71366) ---------------------------

   @Test
   void urlProperty_isStillUrlEncoded() {
      String value = "Plant Operations - MPS/L&D/SSC";
      UrlBean bean = new UrlBean();
      bean.setQuery("https://host/api?area=$(p)");
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(bean, vars);
      assertEquals("https://host/api?area=Plant%20Operations%20-%20MPS/L%26D/SSC", bean.getQuery());
   }

   @Test
   void mongoDefaultStyle_isStillJson() {
      MongoBean bean = new MongoBean();
      bean.setQuery("{name: '$(p)'}");
      VariableTable vars = new VariableTable();
      vars.put("p", "O'Brien");
      TabularUtil.replaceVariables(bean, vars);
      assertEquals("{name: '\\u004f\\u0027Brien'}", bean.getQuery());
   }

   // ---- helpers --------------------------------------------------------------

   private static String replace(QueryBean bean, String template, Object value) {
      bean.setQuery(template);
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(bean, vars);
      return bean.getQuery();
   }

   private static Object getQuietly(VariableTable vars, String name) {
      try {
         return vars.get(name);
      }
      catch(Exception ex) {
         throw new AssertionError(ex);
      }
   }

   /** How an engine reads a string literal. */
   enum Lexer {
      /** '' is a quote, backslash is a plain character (ANSI, CQL). */
      ANSI,
      /** backslash escapes the next character, '' ends the literal (HiveQL). */
      BACKSLASH,
      /** backslash escapes and '' is a quote (MySQL default). */
      MYSQL
   }

   /**
    * Read text that should consist only of string literals (adjacent literals
    * allowed, as HiveQL concatenates them) and return their decoded values,
    * or null if any text lies outside a literal, i.e. the value broke out.
    */
   private static List<String> lex(String text, Lexer lexer) {
      List<String> tokens = new ArrayList<>();
      int i = 0;
      int n = text.length();

      while(i < n) {
         char q = text.charAt(i);

         if(q != '\'' && q != '"') {
            return null;
         }

         StringBuilder value = new StringBuilder();
         i++;
         boolean closed = false;

         while(i < n) {
            char c = text.charAt(i);

            if(c == '\\' && lexer != Lexer.ANSI && i + 1 < n) {
               value.append(text.charAt(i + 1));
               i += 2;
            }
            else if(c == q && lexer != Lexer.BACKSLASH && i + 1 < n && text.charAt(i + 1) == q) {
               value.append(q);
               i += 2;
            }
            else if(c == q) {
               i++;
               closed = true;
               break;
            }
            else {
               value.append(c);
               i++;
            }
         }

         if(!closed) {
            return null;
         }

         tokens.add(value.toString());
      }

      return tokens;
   }

   public interface QueryBean {
      String getQuery();

      void setQuery(String query);
   }

   public static class CqlBean implements QueryBean {
      @Property(label = "Query", sql = true, literalEscapeStyle = VarSQL.LiteralEscapeStyle.SQL)
      public String getQuery() {
         return query;
      }

      public void setQuery(String query) {
         this.query = query;
      }

      private String query;
   }

   public static class BackslashBean implements QueryBean {
      @Property(label = "Query", sql = true,
                literalEscapeStyle = VarSQL.LiteralEscapeStyle.BACKSLASH)
      public String getQuery() {
         return query;
      }

      public void setQuery(String query) {
         this.query = query;
      }

      private String query;
   }

   public static class UnknownDialectBean implements QueryBean {
      @Property(label = "Query", sql = true, literalEscapeStyle = VarSQL.LiteralEscapeStyle.SQL,
                backslashIsEscapeChar = true)
      public String getQuery() {
         return query;
      }

      public void setQuery(String query) {
         this.query = query;
      }

      private String query;
   }

   public static class MongoBean implements QueryBean {
      @Property(label = "Query", sql = true)
      public String getQuery() {
         return query;
      }

      public void setQuery(String query) {
         this.query = query;
      }

      private String query;
   }

   public static class UrlBean implements QueryBean {
      @Property(label = "URL")
      public String getQuery() {
         return query;
      }

      public void setQuery(String query) {
         this.query = query;
      }

      private String query;
   }
}
