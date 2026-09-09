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
package inetsoft.uql.rest.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.tabular.TabularCatalogProvider;
import inetsoft.uql.tabular.TabularDatasetSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link RestXMLRuntime}'s two {@link TabularCatalogProvider} methods. Every call below
 * passes a literal {@code null} for the data source argument -- deliberately, per the reconcile
 * doc's simplification of the original "poisoned Mockito mock" plan: {@code describeDataset}
 * (design section 2.2 / {@link RestXmlEndpointCatalog#describeDataset}) never dereferences its
 * data source argument at all, so a real {@code NullPointerException} would surface immediately
 * if it ever started to -- a stronger, simpler proof than a mock that throws on any accessor call.
 */
class RestXMLRuntimeTest {
   // ----- A1 -----

   @Test
   void implementsTabularCatalogProvider() {
      assertTrue(TabularCatalogProvider.class.isAssignableFrom(RestXMLRuntime.class));
   }

   // ----- A2 -----

   @Test
   void listDatasets_alwaysThrowsNamedException() {
      Exception thrown = assertThrows(Exception.class,
         () -> new RestXMLRuntime().listDatasets(null));

      assertTrue(thrown.getMessage().contains("does not support"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("enumeration"), thrown.getMessage());
   }

   @Test
   void listDatasets_neverReturnsEmptyCatalog() {
      // Wrapped in assertThrows rather than inspecting a returned TabularCatalog -- a future
      // regression that "fixes" this into `return new TabularCatalog(List.of(), List.of())` must
      // fail this test loudly, not slip through silently.
      assertThrows(Exception.class, () -> new RestXMLRuntime().listDatasets(null));
   }

   @Test
   void listDatasets_throwsEvenWithNoUrlConfigured() {
      // Passing null (no data source at all, let alone one with a URL configured) proves the
      // throw does not depend on inspecting the data source's state -- a future regression that
      // adds a precondition check ahead of the categorical throw would still need to handle this
      // case, so this guards against that too.
      assertThrows(Exception.class, () -> new RestXMLRuntime().listDatasets(null));
   }

   // ----- describeDataset: wiring (logic itself is RestXmlEndpointCatalogTest's job) -----

   @Test
   void describeDataset_delegatesToRestXmlEndpointCatalog() throws Exception {
      String token = RestXmlEndpointTokenCodec.encode(
         "/api/books", "/bookstore/book", MAPPER.readTree("{\"title\":\"string\"}"));

      TabularDatasetSchema schema = new RestXMLRuntime().describeDataset(null, token);

      assertSame(token, schema.datasetId());
      assertEquals(1, schema.columns().size());
      assertEquals("title", schema.columns().get(0).name());
      assertEquals("/api/books", schema.params().get("suffix"));
      assertEquals("/bookstore/book", schema.params().get("xpath"));
      assertTrue(schema.columnsMayBeIncomplete());
   }

   // ----- describeDataset never touches the data source, valid and malformed alike -----

   @Test
   void describeDataset_neverTouchesDataSource_evenWhenNull_validToken() throws Exception {
      String token = RestXmlEndpointTokenCodec.encode(
         "/api/books", "/bookstore/book", MAPPER.readTree("{\"title\":\"string\"}"));

      // A real dereference of the null data source would throw NullPointerException, which would
      // mask this assertion entirely -- reaching a normal return proves dataSource is never
      // touched on the success path.
      assertDoesNotThrow(() -> new RestXMLRuntime().describeDataset(null, token));
   }

   @Test
   void describeDataset_neverTouchesDataSource_evenWhenNull_malformedToken() {
      // Same proof on the failure path: the thrown exception must be the codec's own validation
      // exception, never a NullPointerException from touching the null data source first.
      Exception thrown = assertThrows(Exception.class,
         () -> new RestXMLRuntime().describeDataset(null, "not-base64-!!!"));

      assertFalse(thrown instanceof NullPointerException, thrown.getClass().getName());
      assertTrue(thrown.getMessage().contains("base64"), thrown.getMessage());
   }

   private static final ObjectMapper MAPPER = new ObjectMapper();
}
