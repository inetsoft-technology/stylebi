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

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link RestXmlEndpointCatalog}, built off tokens {@link RestXmlEndpointTokenCodec#encode}
 * produces. No live network, no Spring context -- {@link TabularSchemaExtractor}/
 * {@link TabularQueryContractSupport} only need a {@code Config} bean reachable through
 * {@link ConfigurationContext} (for a display-label resource bundle lookup that a mock answers
 * with {@code null}), never a live data source; modeled on
 * {@code TabularQueryContractSupportRestJsonQueryReadBackTest}'s identical setup in this same
 * module, which explicitly avoids attaching a real {@code XDataSource} for the same reason.
 */
class RestXmlEndpointCatalogTest {
   @BeforeAll
   static void installConfig() {
      previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(any())).thenReturn(null);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void clearConfig() {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   private static String token(String suffix, String xpath, RestXmlEndpointTokenCodec.Column... columns) {
      return RestXmlEndpointTokenCodec.encode(
         new RestXmlEndpointTokenCodec.DecodedToken(suffix, xpath, List.of(columns)));
   }

   // ----- A3: verbatim echo -----

   @Test
   void describeDataset_echoesSubmittedTokenVerbatim() throws Exception {
      String token = token("/api/books", "/bookstore/book",
         new RestXmlEndpointTokenCodec.Column("title", XSchema.STRING, null));

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertSame(token, schema.datasetId(),
         "datasetId must be the exact submitted String instance -- no re-serialization");
   }

   // ----- A4: columns match exactly, in order -----

   @Test
   void describeDataset_columnsMatchTokenExactlyInOrder() throws Exception {
      String token = token("/s", "/x",
         new RestXmlEndpointTokenCodec.Column("title", XSchema.STRING, "Book title"),
         new RestXmlEndpointTokenCodec.Column("price", XSchema.DOUBLE, null),
         new RestXmlEndpointTokenCodec.Column("published", XSchema.DATE, "Publication date"));

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(3, schema.columns().size());
      assertEquals(new TabularColumn("title", XSchema.STRING, "Book title", null, null),
         schema.columns().get(0));
      assertEquals(new TabularColumn("price", XSchema.DOUBLE, null, null, null),
         schema.columns().get(1));
      assertEquals(new TabularColumn("published", XSchema.DATE, "Publication date", null, null),
         schema.columns().get(2));
   }

   // ----- A5: params contains exactly suffix and xpath -----

   @Test
   void describeDataset_paramsContainsExactlySuffixAndXpath() throws Exception {
      String token = token("/api/books", "/bookstore/book",
         new RestXmlEndpointTokenCodec.Column("title", XSchema.STRING, null));

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(2, schema.params().size(), schema.params().toString());

      Set<String> restXmlQueryProperties = TabularUtil.getPropertyMap(RestXMLQuery.class).keySet();
      assertTrue(restXmlQueryProperties.containsAll(schema.params().keySet()),
         schema.params().keySet() + " must all be properties of RestXMLQuery, which has " +
         restXmlQueryProperties);

      assertEquals("/api/books", schema.params().get("suffix"));
      assertEquals("/bookstore/book", schema.params().get("xpath"));
   }

   // ----- A6: columnsMayBeIncomplete always true -----

   @Test
   void describeDataset_columnsMayBeIncompleteAlwaysTrue() throws Exception {
      String oneColumn = token("/s", "/x",
         new RestXmlEndpointTokenCodec.Column("a", XSchema.STRING, null));
      String tenColumns = token("/s", "/x",
         new RestXmlEndpointTokenCodec.Column("a", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("b", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("c", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("d", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("e", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("f", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("g", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("h", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("i", XSchema.STRING, null),
         new RestXmlEndpointTokenCodec.Column("j", XSchema.STRING, null));

      assertTrue(RestXmlEndpointCatalog.describeDataset(oneColumn).columnsMayBeIncomplete());
      assertTrue(RestXmlEndpointCatalog.describeDataset(tenColumns).columnsMayBeIncomplete());
   }

   // ----- keyColumns always empty -----

   @Test
   void describeDataset_keyColumnsAlwaysEmpty() throws Exception {
      String token = token("/s", "/x",
         new RestXmlEndpointTokenCodec.Column("a", XSchema.STRING, null));

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(List.of(), schema.keyColumns());
   }

   // ----- dataset-level description always null -----

   @Test
   void describeDataset_descriptionAlwaysNull() throws Exception {
      String token = token("/s", "/x",
         new RestXmlEndpointTokenCodec.Column("a", XSchema.STRING, null));

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertNull(schema.description());
   }

   // ----- malformed token propagates from the codec -----

   @Test
   void describeDataset_malformedToken_propagatesCodecException() {
      Exception thrown = assertThrows(Exception.class,
         () -> RestXmlEndpointCatalog.describeDataset("not-base64-!!!"));
      assertTrue(thrown.getMessage().contains("base64"), thrown.getMessage());
   }

   // ----- params round-trip into a real, working RestXMLQuery -----

   /**
    * Proves the charter's central claim -- {@code params()} carries {@code suffix}/{@code xpath}
    * so the existing {@code TabularQueryContractSupport.applyQueryContract} path can build a real,
    * working {@code RestXMLQuery} from it unchanged -- rather than merely asserting the map's shape
    * looks right. No network in scope (charter's non-goals rule out live verification this round),
    * so this stops at "the params actually fill the query bean correctly."
    */
   @Test
   void describeDataset_paramsRoundTripIntoARealRestXMLQuery() throws Exception {
      String token = token("/api/books", "/bookstore/book",
         new RestXmlEndpointTokenCodec.Column("title", XSchema.STRING, null));

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      RestXMLQuery query = new RestXMLQuery();

      TabularQueryContractSupport.applyQueryContract(
         query, TabularUtil.getPropertyMap(query.getClass()),
         new TabularSchemaExtractor().extract(query, RestXMLDataSource.TYPE),
         new LinkedHashMap<>(schema.params()), "resturl-test");

      assertEquals("/api/books", query.getSuffix());
      assertEquals("/bookstore/book", query.getXpath());
   }

   private static ConfigurationContext previous;
}
