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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class HeadersAndRowsJsonTableTest {
   @Test
   void dateLikeTextKeepsSourceTextInStringColumn() {
      for(String[] col : DateLikeCases.STRING_COLUMNS) {
         XNodeTable tbl = new XNodeTable(load(col));
         tbl.moreRows(999);

         assertEquals(String.class, tbl.getColType(0), Arrays.toString(col));

         for(int i = 0; i < col.length; i++) {
            assertEquals(col[i], tbl.getObject(i + 1, 0), Arrays.toString(col) + " row " + i);
         }
      }
   }

   @Test
   void dateLikeTextBeforeNumberKeepsSourceText() {
      HeadersAndRowsJsonTable table = new HeadersAndRowsJsonTable("headers", "rows");
      table.load("{\"headers\": [\"col\"], \"rows\": [[\"4/9/2030\"], [5]]}");
      XNodeTable tbl = new XNodeTable(table);
      tbl.moreRows(999);

      assertEquals(String.class, tbl.getColType(0));
      assertEquals("4/9/2030", tbl.getObject(1, 0));
   }

   @Test
   void allDateLikeColumnStaysTyped() {
      DateLikeCases.assertTypedColumns(this::load);
   }

   @Test
   void explicitStringTypeKeepsSourceText() {
      HeadersAndRowsJsonTable table = new HeadersAndRowsJsonTable("headers", "rows");
      table.setColumnType("col", XSchema.STRING);
      // plain Java objects, as the REST runners pass them; a JSON string would name the header "\"col\""
      table.load(Map.of("headers", List.of("col"),
                        "rows", List.of(List.of("4/9/2030"), List.of("5/1/2030"))));
      XNodeTable tbl = new XNodeTable(table);
      tbl.moreRows(999);

      assertEquals("4/9/2030", tbl.getObject(1, 0));
      assertEquals("5/1/2030", tbl.getObject(2, 0));
   }

   private HeadersAndRowsJsonTable load(String[] col) {
      StringBuilder rows = new StringBuilder();

      for(String value : col) {
         rows.append(rows.length() == 0 ? "" : ",").append("[\"").append(value).append("\"]");
      }

      HeadersAndRowsJsonTable table = new HeadersAndRowsJsonTable("headers", "rows");
      table.load("{\"headers\": [\"col\"], \"rows\": [" + rows + "]}");
      return table;
   }
}
