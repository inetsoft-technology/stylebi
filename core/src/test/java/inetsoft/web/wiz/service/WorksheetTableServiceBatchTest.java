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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the batch table-creation dependency check in {@link WorksheetTableService}.
 *
 * <p>{@code createTables} loops over a batch of table definitions, building each one with
 * {@code addOneTable}. When a table fails, later tables in the same batch that declare it as a
 * base/join dependency must be skipped with a clear message instead of being attempted (which
 * would fail anyway, with a more confusing "table not found" error) or silently building against
 * a half-created worksheet.
 *
 * <p>{@link WorksheetTableService#firstMissingDependency} is the pure aggregation-layer check that
 * powers this: it reports the first declared dependency of a table that is either already marked
 * failed in this batch, or simply absent from the worksheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceBatchTest {

   private static WorksheetTableService service() {
      // firstMissingDependency uses only its parameters, never instance state, so null
      // dependencies are safe here.
      return new WorksheetTableService(null, null, null, null, null, null, null);
   }

   @Test
   void firstMissingDependencyFlagsFailedAndAbsentBases() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "A"));   // A exists & healthy

      WorksheetTable join = new WorksheetTable();
      join.setTableName("J");
      join.setTableType("relational join table");
      join.setBaseTables(java.util.List.of("A", "B"));           // B never created

      WorksheetTableService svc = service();
      // B is absent from the worksheet → reported.
      assertEquals("B", svc.firstMissingDependency(ws, join, new java.util.HashSet<>()));

      // A is present but marked failed → reported.
      var failedA = new java.util.HashSet<>(java.util.Set.of("A"));
      assertEquals("A", svc.firstMissingDependency(ws, join, failedA));

      // all deps healthy → null.
      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "B"));
      assertNull(svc.firstMissingDependency(ws, join, new java.util.HashSet<>()));
   }

   @Test
   void dependentOfFailedBaseIsSkippedWithClearMessage() {
      // Pure aggregation-path check: a join whose base is absent is flagged, not attempted.
      Worksheet ws = new Worksheet();
      WorksheetTable join = new WorksheetTable();
      join.setTableName("J");
      join.setTableType("relational join table");
      join.setBaseTables(java.util.List.of("MISSING"));

      WorksheetTableService svc = service();
      assertEquals("MISSING", svc.firstMissingDependency(ws, join, new java.util.HashSet<>()));
   }
}
