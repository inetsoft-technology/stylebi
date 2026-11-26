/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatagovTableTest {
   @Test
   void calendarDateKeepsSeconds() throws Exception {
      JsonObject json = Json.createObjectBuilder()
         .add("meta", Json.createObjectBuilder().add("view", Json.createObjectBuilder()
            .add("columns", Json.createArrayBuilder().add(Json.createObjectBuilder()
               .add("name", "created").add("dataTypeName", "calendar_date")))))
         .add("data", Json.createArrayBuilder()
            .add(Json.createArrayBuilder().add("2025-08-03T04:27:57"))
            .add(Json.createArrayBuilder().add("2025-08-03T13:45:30"))
            .add(Json.createArrayBuilder().add("2025-08-03T08:05:59")))
         .build();

      DatagovTable table = new DatagovTable(json, 0, new DatagovQuery());
      String[] expected = { "04:27:57", "13:45:30", "08:05:59" };
      SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");

      for(String exp : expected) {
         assertTrue(table.next());
         Object val = table.getObject(0);
         assertTrue(val instanceof Date, "parsed as date: " + val);
         assertEquals(exp, fmt.format((Date) val));
      }
   }
}
