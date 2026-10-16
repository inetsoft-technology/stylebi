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

import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #76864: {@link TabularUtil#replaceVariables(Object, VariableTable)} splices
 * variable values into {@code @Property(sql=true)} properties (the MongoDB JSON
 * query text). String values must be JSON-escaped so they cannot close the
 * generated literal and add query structure. Bug #77105 changed the encoding to
 * the fail-closed backslash-u-XXXX form (punctuation and the first char), so
 * these expectations pin that form instead of the old \' / \\ backslash form.
 */
@Tag("core")
class TabularUtilSqlPropertyEscapeTest {
   @Test
   void unquotedPlaceholder_quoteInjectionValue_isUnicodeEscaped() {
      assertEquals("{name:  '\\u0078\\u0027\\u002c admin\\u003a \\u00271' }",
                   replace("{name: $(p)}", "x', admin: '1"));
   }

   @Test
   void unquotedPlaceholder_trailingBackslash_isUnicodeEscaped() {
      assertEquals("{name:  '\\u0078\\u005c' , b: 1}", replace("{name: $(p), b: 1}", "x\\"));
   }

   @Test
   void quotedPlaceholder_apostrophe_isUnicodeEscapedNotDoubled() {
      assertEquals("{name: '\\u004f\\u0027Brien'}", replace("{name: '$(p)'}", "O'Brien"));
   }

   private static String replace(String template, Object value) {
      SqlBean bean = new SqlBean();
      bean.setQuery(template);
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(bean, vars);
      return bean.getQuery();
   }

   public static class SqlBean {
      @Property(label = "Query", sql = true)
      public String getQuery() {
         return query;
      }

      public void setQuery(String query) {
         this.query = query;
      }

      private String query;
   }
}
