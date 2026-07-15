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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link WorksheetTableService#shouldProbe}.
 *
 * <p>The bug: a {@code windowColumns}-bearing request was never probed, so a render-time failure
 * in the {@code OVER(...)} evaluation (whether pushed down as SQL, or computed in-memory when the
 * base table can't fully SQL-merge — e.g. an upstream JS-evaluated expression column blocks the
 * merge) never got the chance to surface at construction time. The fix extends {@code shouldProbe}
 * to also probe {@code windowColumns} requests, mirroring the existing {@code expressionColumns}
 * check, so a genuine render-time failure fails loud at creation instead of reaching the viewer as
 * an empty result.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceShouldProbeTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      // shouldProbe reads only its parameter, never instance state, so null dependencies are safe.
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static WorksheetTable request(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
   }

   @Test
   void sqlQueryTableIsAlwaysProbed() throws Exception {
      WorksheetTable req = request("""
         { "tableName": "t", "tableType": "sql query table" }
         """);

      assertTrue(service().shouldProbe(req));
   }

   @Test
   void plainMirrorWithNoExpressionOrWindowColumnsIsNotProbed() throws Exception {
      WorksheetTable req = request("""
         { "tableName": "t", "tableType": "mirror table" }
         """);

      assertFalse(service().shouldProbe(req));
   }

   @Test
   void expressionColumnsAreProbed() throws Exception {
      WorksheetTable req = request("""
         {
           "tableName": "t", "tableType": "mirror table",
           "expressionColumns": [
             { "name": "x", "expression": "field['a'] + 1", "type": "double" }
           ]
         }
         """);

      assertTrue(service().shouldProbe(req));
   }

   @Test
   void windowColumnsAreProbed() throws Exception {
      WorksheetTable req = request("""
         {
           "tableName": "t", "tableType": "mirror table",
           "windowColumns": [
             { "name": "rnk", "fn": "ROW_NUMBER", "orderBy": [{ "field": "a" }] }
           ]
         }
         """);

      assertTrue(service().shouldProbe(req));
   }

   @Test
   void emptyWindowColumnsListIsNotProbed() throws Exception {
      WorksheetTable req = request("""
         { "tableName": "t", "tableType": "mirror table", "windowColumns": [] }
         """);

      assertFalse(service().shouldProbe(req));
   }
}
