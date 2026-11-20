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
package inetsoft.uql.datagov;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81924: a column type set in the worksheet must decide the column type even when the
 * first row of that column is null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, DatagovTableNullValueTest.TestConfig.class,
                                      DatagovTableColumnTypeNullFirstRowTest.SwapperConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DatagovTableColumnTypeNullFirstRowTest {
   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   private static final String JSON =
      "{\"meta\":{\"view\":{\"columns\":[" +
      "{\"name\":\"Flag\",\"dataTypeName\":\"checkbox\"}," +
      "{\"name\":\"EventTime\",\"dataTypeName\":\"calendar_date\"}," +
      "{\"name\":\"Amount\",\"dataTypeName\":\"number\"}," +
      "{\"name\":\"Untouched\",\"dataTypeName\":\"number\"}]}}," +
      "\"data\":[[null,null,null,null]," +
      "[true,\"2024-02-20T08:05:00\",\"3.5\",\"4.5\"]," +
      "[false,\"2024-02-21T08:05:00\",\"7\",\"8\"]]}";

   private XNodeTableLens lens(DatagovQuery query) {
      JsonObject json = Json.createReader(new StringReader(JSON)).readObject();
      XNodeTableLens lens = new XNodeTableLens(new DatagovTable(json, 0, query));
      lens.moreRows(100);
      return lens;
   }

   private DatagovQuery stringQuery() {
      DatagovQuery query = new DatagovQuery();
      query.setColumnType("Flag", XSchema.STRING);
      query.setColumnType("EventTime", XSchema.STRING);
      query.setColumnType("Amount", XSchema.STRING);
      return query;
   }

   @Test
   void declaredStringTypeWinsOverSocrataTypeWhenFirstRowNull() {
      XNodeTableLens lens = lens(stringQuery());

      assertEquals(String.class, lens.getColType(0));
      assertEquals(String.class, lens.getColType(1));
      assertEquals(String.class, lens.getColType(2));

      // header row + 3 data rows; every cell is readable without ClassCastException
      assertEquals(4, lens.getRowCount());
      assertNull(lens.getObject(1, 0));
      assertEquals("true", String.valueOf(lens.getObject(2, 0)));
      assertEquals("false", String.valueOf(lens.getObject(3, 0)));
      assertNotNull(lens.getObject(2, 1));
      assertNotNull(lens.getObject(3, 1));
      assertEquals("3.5", String.valueOf(lens.getObject(2, 2)));
   }

   @Test
   void columnWithoutDeclaredTypeKeepsSocrataTypeWhenFirstRowNull() {
      XNodeTableLens lens = lens(stringQuery());

      assertEquals(Double.class, lens.getColType(3));
      assertEquals(4.5, ((Number) lens.getObject(2, 3)).doubleValue());
   }

   @Test
   void noDeclaredTypesBehavesAsBefore() {
      XNodeTableLens lens = lens(new DatagovQuery());

      assertEquals(Boolean.class, lens.getColType(0));
      assertEquals(java.util.Date.class, lens.getColType(1));
      assertEquals(Double.class, lens.getColType(2));
   }

   @Test
   void declaredTypeAgreesWithValuesWhenFirstRowNotNull() {
      String json = "{\"meta\":{\"view\":{\"columns\":[" +
         "{\"name\":\"Flag\",\"dataTypeName\":\"checkbox\"}]}}," +
         "\"data\":[[true],[null]]}";
      DatagovQuery query = new DatagovQuery();
      query.setColumnType("Flag", XSchema.STRING);
      DatagovTable table = new DatagovTable(
         Json.createReader(new StringReader(json)).readObject(), 0, query);

      assertEquals(String.class, table.getType(0));
   }

   @Configuration
   static class SwapperConfig {
      @Bean
      public XSwapper xSwapper() {
         return new XSwapper();
      }
   }
}
