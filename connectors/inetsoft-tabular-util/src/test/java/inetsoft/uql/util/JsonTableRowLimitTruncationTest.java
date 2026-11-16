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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A table says it left data out only when data was offered beyond its row limit, never because it
 * holds exactly as many rows as the limit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class JsonTableRowLimitTruncationTest {
   @Test
   void expandedTableDroppingRowsOfAPageIsTruncated() {
      BaseJsonTable table = expanded(3);
      table.loadStreamed(rows(1, 5));

      assertEquals(3, table.size());
      assertTrue(table.isTruncated());
   }

   @Test
   void expandedTableHoldingExactlyTheLimitIsNotTruncated() {
      BaseJsonTable table = expanded(5);
      table.loadStreamed(rows(1, 5));

      assertEquals(5, table.size());
      assertFalse(table.isTruncated());
   }

   @Test
   void expandedTableOfferedAPageAfterTheLimitIsTruncated() {
      BaseJsonTable table = expanded(2);
      table.loadStreamed(rows(1, 2));
      assertFalse(table.isTruncated());

      table.loadStreamed(rows(3, 4));

      assertEquals(2, table.size());
      assertTrue(table.isTruncated());
   }

   @Test
   void expandedTableWithoutALimitIsNotTruncated() {
      BaseJsonTable table = expanded(0);
      table.loadStreamed(rows(1, 500));

      assertEquals(500, table.size());
      assertFalse(table.isTruncated());
   }

   @Test
   void jsonTableDroppingRowsOfAPageIsTruncated() {
      BaseJsonTable table = flat(3);
      table.loadStreamed(rows(1, 5));

      assertEquals(3, table.size());
      assertTrue(table.isTruncated());
   }

   @Test
   void jsonTableHoldingExactlyTheLimitIsNotTruncated() {
      BaseJsonTable table = flat(5);
      table.loadStreamed(rows(1, 5));

      assertEquals(5, table.size());
      assertFalse(table.isTruncated());
   }

   @Test
   void headersAndRowsTableDroppingRowsIsTruncated() {
      BaseJsonTable table = headersAndRows(2);
      table.loadStreamed(headersAndRowsPage(4));

      assertEquals(2, table.size());
      assertTrue(table.isTruncated());
   }

   @Test
   void headersAndRowsTableHoldingExactlyTheLimitIsNotTruncated() {
      BaseJsonTable table = headersAndRows(4);
      table.loadStreamed(headersAndRowsPage(4));

      assertEquals(4, table.size());
      assertFalse(table.isTruncated());
   }

   private static BaseJsonTable expanded(int maxRows) {
      ExpandedJsonTable table = new ExpandedJsonTable((String) null);
      table.setMaxRows(maxRows);
      table.beginStreamedLoading();
      return table;
   }

   private static BaseJsonTable flat(int maxRows) {
      JsonTable table = new JsonTable();
      table.setMaxRows(maxRows);
      table.beginStreamedLoading();
      return table;
   }

   private static BaseJsonTable headersAndRows(int maxRows) {
      HeadersAndRowsJsonTable table = new HeadersAndRowsJsonTable("headers", "rows");
      table.setMaxRows(maxRows);
      table.beginStreamedLoading();
      return table;
   }

   /** Rows {@code first..last}, each {@code {id: n}}. */
   private static List<Object> rows(int first, int last) {
      List<Object> rows = new ArrayList<>();

      for(int i = first; i <= last; i++) {
         Map<String, Object> row = new LinkedHashMap<>();
         row.put("id", i);
         rows.add(row);
      }

      return rows;
   }

   private static Map<String, Object> headersAndRowsPage(int count) {
      List<Object> rows = new ArrayList<>();

      for(int i = 1; i <= count; i++) {
         rows.add(new ArrayList<>(List.of(i)));
      }

      Map<String, Object> page = new LinkedHashMap<>();
      page.put("headers", new ArrayList<>(List.of("id")));
      page.put("rows", rows);
      return page;
   }
}
