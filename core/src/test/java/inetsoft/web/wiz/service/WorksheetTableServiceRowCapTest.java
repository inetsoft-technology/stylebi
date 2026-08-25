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
package inetsoft.web.wiz.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which target kinds have to justify a table built with no row cap.
 *
 * <p>The check exists because a paginated table with no cap requests pages until the service runs
 * out of data, on every render — and {@code createTables} builds wiz analytics with an unlimited
 * design row count, so "every render" is the normal case rather than the edge one.
 *
 * <p>It was reachable only for an endpoint, which was right while an endpoint was the only kind
 * that could paginate. The query kind addresses any connector, and pagination there is set through
 * a property like any other: {@code RestJsonQuery.setPaginationType} writes the pagination spec
 * directly, and {@code AbstractRestQuery.isPaged()} reads that spec, so neither goes anywhere near
 * the endpoint machinery. A query-kind request that set a pagination type and no cap would have
 * been built uncapped and silently.
 */
@Tag("core")
class WorksheetTableServiceRowCapTest {
   @Test
   void anEndpointHasToJustifyAnUncappedTable() {
      assertTrue(WorksheetTableService.rowCapRequiredFor("endpoint"));
   }

   /**
    * The gap this pins. A query-kind request can set a pagination type through its parameters, so
    * it can page, so it has to answer the same question an endpoint does.
    */
   @Test
   void aQueryHasToJustifyAnUncappedTable() {
      assertTrue(WorksheetTableService.rowCapRequiredFor("query"));
   }

   /**
    * A local file is read whole in one pass. There are no pages and no per-call bill, so demanding
    * a cap would refuse a correct request for a cost that does not exist.
    */
   @Test
   void aFileDoesNot() {
      assertFalse(WorksheetTableService.rowCapRequiredFor("file"));
   }

   /**
    * The exemption is a list of one, not a whitelist of the kinds that existed when it was written.
    * A kind added later is covered until someone decides otherwise, which is the safe direction: a
    * wrong exemption is a metered API walked to the end on every render, a wrong inclusion is one
    * message asking for a number.
    */
   @Test
   void aKindNobodyHasWrittenYetIsCoveredByDefault() {
      assertTrue(WorksheetTableService.rowCapRequiredFor("some-future-kind"));
   }
}
