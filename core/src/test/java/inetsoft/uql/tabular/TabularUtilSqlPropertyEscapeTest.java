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
 * query text). String values must be JSON backslash-escaped so they cannot close
 * the generated literal and add query structure.
 */
@Tag("core")
class TabularUtilSqlPropertyEscapeTest {
   @Test
   void unquotedPlaceholder_quoteInjectionValue_isBackslashEscaped() {
      assertEquals("{name:  'x\\', admin: \\'1' }", replace("{name: $(p)}", "x', admin: '1"));
   }

   @Test
   void unquotedPlaceholder_trailingBackslash_isDoubled() {
      assertEquals("{name:  'x\\\\' , b: 1}", replace("{name: $(p), b: 1}", "x\\"));
   }

   @Test
   void quotedPlaceholder_apostrophe_isBackslashEscapedNotDoubled() {
      assertEquals("{name: 'O\\'Brien'}", replace("{name: '$(p)'}", "O'Brien"));
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
