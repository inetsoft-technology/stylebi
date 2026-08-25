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

import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A field belonging to one target kind, sent with another, is refused rather than ignored.
 *
 * <p>Every one of these fields is read on exactly one path. Sent with a different kind it is not
 * rejected by anything downstream — it is simply never looked at, and the table builds and reports
 * success having ignored whatever the caller put in it. A request half-migrated between kinds, or
 * one that named the wrong kind, then looks exactly like a request that worked.
 *
 * <p>The rejections have to run in both directions to be worth anything, which is what these pin:
 * an older kind's field on a query request, and the new {@code queryParams} on an older one.
 */
@Tag("core")
class WorksheetTableServiceKindFieldsTest {
   @Test
   void aQueryRequestRefusesTheEndpointsParameterMap() {
      WorksheetTable.TabularSource src = source();
      src.setParameters(Map.of("since", "2024-01-01"));

      assertMessage(assertThrows(IllegalArgumentException.class,
                                 () -> service().rejectForeignFields(src)),
                    "tabularSource.parameters");
   }

   @Test
   void aQueryRequestRefusesTheFilesOptionBag() {
      WorksheetTable.TabularSource src = source();
      src.setParams(Map.of("delimiter", ","));

      assertMessage(assertThrows(IllegalArgumentException.class,
                                 () -> service().rejectForeignFields(src)),
                    "tabularSource.params");
   }

   @Test
   void aQueryRequestRefusesTheEndpointsResponseShapeFields() {
      WorksheetTable.TabularSource src = source();
      src.setJsonPath("$.data[*]");

      assertMessage(assertThrows(IllegalArgumentException.class,
                                 () -> service().rejectForeignFields(src)),
                    "jsonPath");
   }

   /**
    * A lookup level names an endpoint, which is the one thing this kind does not have — it is why a
    * query request has no target either.
    */
   @Test
   void aQueryRequestRefusesTheLookupChain() {
      WorksheetTable.TabularSource src = source();
      src.setLookup(List.of("Charge Refunds"));

      assertMessage(assertThrows(IllegalArgumentException.class,
                                 () -> service().rejectForeignFields(src)),
                    "tabularSource.lookup");
   }

   /**
    * The two flags only mean anything alongside a lookup chain, so they are refused on their own
    * too: accepting one silently would drop the setting the caller actually cared about.
    */
   @Test
   void aQueryRequestRefusesALookupFlagOnItsOwn() {
      WorksheetTable.TabularSource src = source();
      src.setLookupExpandArrays(Boolean.TRUE);

      assertThrows(IllegalArgumentException.class, () -> service().rejectForeignFields(src));
   }

   @Test
   void aQueryRequestRefusesATargetItWouldNotRead() {
      WorksheetTable.TabularSource src = source();
      src.setTarget("Charges");

      assertMessage(assertThrows(IllegalArgumentException.class,
                                 () -> service().rejectForeignFields(src)),
                    "tabularSource.target");
   }

   @Test
   void aQueryRequestCarryingOnlyItsOwnFieldsIsAccepted() {
      assertDoesNotThrow(() -> service().rejectForeignFields(source()));
   }

   /**
    * The other direction, and the one the new field points at: nothing on the endpoint or file path
    * reads {@code queryParams}, so it would be dropped whole.
    */
   @Test
   void anEndpointRequestRefusesQueryParams() {
      WorksheetTable.TabularSource src = new WorksheetTable.TabularSource();
      src.setQueryParams(Map.of("suffix", "/v1/charges"));

      IllegalArgumentException ex = assertThrows(
         IllegalArgumentException.class,
         () -> service().rejectQueryParams(src, "endpoint", "tabularSource.parameters"));

      assertMessage(ex, "tabularSource.queryParams");
      assertMessage(ex, "tabularSource.parameters");
   }

   @Test
   void aFileRequestRefusesQueryParams() {
      WorksheetTable.TabularSource src = new WorksheetTable.TabularSource();
      src.setQueryParams(Map.of("excelSheet", "Q1"));

      assertMessage(assertThrows(
                       IllegalArgumentException.class,
                       () -> service().rejectQueryParams(src, "file", "tabularSource.params")),
                    "tabularSource.params");
   }

   @Test
   void anEmptyQueryParamsMapIsNotARequestToUseIt() {
      WorksheetTable.TabularSource src = new WorksheetTable.TabularSource();
      src.setQueryParams(Map.of());

      assertDoesNotThrow(() -> service().rejectQueryParams(src, "endpoint", "tabularSource.parameters"));
   }

   /** A query-kind source carrying nothing but what its own contract reads. */
   private static WorksheetTable.TabularSource source() {
      WorksheetTable.TabularSource src = new WorksheetTable.TabularSource();
      src.setDatasourcePath("SaaS/Example");
      src.setTargetKind("query");
      src.setQueryParams(Map.of("suffix", "/v1/charges"));

      return src;
   }

   /**
    * The message has to name the field, since it is the whole of what tells the caller which of the
    * several things they sent has to move.
    */
   private static void assertMessage(Exception ex, String expected) {
      assertTrue(ex.getMessage() != null && ex.getMessage().contains(expected),
                 "expected the message to name '" + expected + "', got: " + ex.getMessage());
   }

   private static WorksheetTableService service() {
      // Both rejections read only their arguments, never instance state, so null wiring is safe --
      // the same shape WorksheetTableServiceShouldProbeTest uses.
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }
}
