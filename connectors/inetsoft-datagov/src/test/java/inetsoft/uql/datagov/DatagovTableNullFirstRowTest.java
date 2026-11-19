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

import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81808: a null first row must not change the column type.
 */
class DatagovTableNullFirstRowTest {
   private static final String JSON =
      "{\"meta\":{\"view\":{\"columns\":[" +
      "{\"name\":\"EventTime\",\"dataTypeName\":\"calendar_date\"}," +
      "{\"name\":\"Amount\",\"dataTypeName\":\"number\"}]}}," +
      "\"data\":[[null,null],[\"2024-02-20T08:05:00\",\"3.5\"]]}";

   @Test
   void nullFirstRowDateColumnStaysDate() {
      JsonObject json = Json.createReader(new StringReader(JSON)).readObject();
      DatagovTable table = new DatagovTable(json, 0, new DatagovQuery());

      assertEquals(Date.class, table.getType(0));
      assertEquals(Double.class, table.getType(1));
      assertTrue(table.next());
      assertNull(table.getObject(0));
      assertTrue(table.next());
      assertInstanceOf(Date.class, table.getObject(0));
   }
}
