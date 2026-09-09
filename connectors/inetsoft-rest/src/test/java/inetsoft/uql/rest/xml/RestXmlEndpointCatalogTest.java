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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

   private static String token(String suffix, String xpath, String responseSchemaJson) throws Exception {
      return RestXmlEndpointTokenCodec.encode(suffix, xpath, MAPPER.readTree(responseSchemaJson));
   }

   // ----- A3: verbatim echo -----

   @Test
   void describeDataset_echoesSubmittedTokenVerbatim() throws Exception {
      String token = token("/api/books", "/bookstore/book", "\"string\"");

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertSame(token, schema.datasetId(),
         "datasetId must be the exact submitted String instance -- no re-serialization");
   }

   // ----- A3/A4: nested + repeating tree flattens to the exact dot-joined column set, in order,
   // types verbatim (no mapping table) -----

   @Test
   void describeDataset_nestedAndRepeatingTree_flattensToExactColumnsInOrder() throws Exception {
      String responseSchema = "{\"title\":\"string\",\"price\":\"double\"," +
         "\"author\":{\"name\":\"string\",\"id\":\"integer\"}," +
         "\"reviews\":[{\"rating\":\"integer\",\"comment\":\"string\"}]}";
      String token = token("/s", "/x", responseSchema);

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(
         List.of(
            new TabularColumn("title", "string", null, null, null),
            new TabularColumn("price", "double", null, null, null),
            new TabularColumn("author.name", "string", null, null, null),
            new TabularColumn("author.id", "integer", null, null, null),
            new TabularColumn("reviews.rating", "integer", null, null, null),
            new TabularColumn("reviews.comment", "string", null, null, null)
         ),
         schema.columns(),
         "column SET, per-column type, and ORDER must all match the tree's own JSON key order"
      );
   }

   // ----- §3.2: bare-string-root produces exactly one "Column"-named column -----

   @Test
   void describeDataset_bareStringRoot_producesOneColumnNamedColumn() throws Exception {
      String token = token("/s", "/x", "\"integer\"");

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(List.of(new TabularColumn("Column", "integer", null, null, null)), schema.columns());
   }

   // ----- §3.3: the "*" wildcard key -----

   @Test
   void describeDataset_mixedTreeWithWildcardSubtree_producesOnlyTheRealColumn() throws Exception {
      String responseSchema = "{\"id\":\"string\",\"attrs\":{\"*\":\"string\"}}";
      String token = token("/s", "/x", responseSchema);

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(List.of(new TabularColumn("id", "string", null, null, null)), schema.columns());
      assertTrue(schema.columnsMayBeIncomplete());
   }

   @Test
   void describeDataset_loneWildcardRoot_throwsNamingWhy() throws Exception {
      String token = token("/s", "/x", "{\"*\":\"string\"}");

      Exception thrown = assertThrows(Exception.class, () -> RestXmlEndpointCatalog.describeDataset(token));
      assertTrue(thrown.getMessage().contains("*"), thrown.getMessage());
   }

   // ----- design §3.5 / reconcile item 4: duplicate flattened column names are a deliberately
   // accepted, deferred corner case -- describeDataset emits BOTH columns rather than merging,
   // deduping, or silently dropping one. This is the half of the behavior
   // RestXmlEndpointTokenCodecTest's decode_acceptsDuplicateFlattenedColumnName alone can't
   // demonstrate (decode only proves the tree isn't rejected; this proves both columns surface). -----

   @Test
   void describeDataset_duplicateFlattenedColumnName_emitsBothColumns() throws Exception {
      String responseSchema = "{\"a\":{\"b\":\"string\"},\"a.b\":\"integer\"}";
      String token = token("/s", "/x", responseSchema);

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(2, schema.columns().size(), schema.columns().toString());
      assertEquals(
         List.of(
            new TabularColumn("a.b", "string", null, null, null),
            new TabularColumn("a.b", "integer", null, null, null)
         ),
         schema.columns(),
         "two tree paths flattening to the identical dot-joined name must both surface as " +
         "columns, not be merged/deduped/one silently dropped"
      );
   }

   // ----- A5: params contains exactly suffix and xpath -----

   @Test
   void describeDataset_paramsContainsExactlySuffixAndXpath() throws Exception {
      String token = token("/api/books", "/bookstore/book", "\"string\"");

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
      String oneColumn = token("/s", "/x", "{\"a\":\"string\"}");
      String tenColumns = token("/s", "/x", "{\"a\":\"string\",\"b\":\"string\",\"c\":\"string\"," +
         "\"d\":\"string\",\"e\":\"string\",\"f\":\"string\",\"g\":\"string\",\"h\":\"string\"," +
         "\"i\":\"string\",\"j\":\"string\"}");

      assertTrue(RestXmlEndpointCatalog.describeDataset(oneColumn).columnsMayBeIncomplete());
      assertTrue(RestXmlEndpointCatalog.describeDataset(tenColumns).columnsMayBeIncomplete());
   }

   // ----- keyColumns always empty -----

   @Test
   void describeDataset_keyColumnsAlwaysEmpty() throws Exception {
      String token = token("/s", "/x", "{\"a\":\"string\"}");

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertEquals(List.of(), schema.keyColumns());
   }

   // ----- dataset-level description always null (v2 drops description entirely -- decision #6) -----

   @Test
   void describeDataset_descriptionAlwaysNull() throws Exception {
      String token = token("/s", "/x", "{\"a\":\"string\"}");

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      assertNull(schema.description());
   }

   // ----- A4: per-column type is the tree leaf VERBATIM, no mapping table -----

   @Test
   void describeDataset_columnTypeIsLeafVerbatim_everyColumnType() throws Exception {
      for(String type : new String[]{"string", "integer", "double", "date", "timeInstant", "boolean"}) {
         String token = token("/s", "/x", "\"" + type + "\"");
         TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

         assertEquals(type, schema.columns().get(0).type(), "type=" + type);
      }
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
      String token = token("/api/books", "/bookstore/book", "\"string\"");

      TabularDatasetSchema schema = RestXmlEndpointCatalog.describeDataset(token);

      RestXMLQuery query = new RestXMLQuery();

      TabularQueryContractSupport.applyQueryContract(
         query, TabularUtil.getPropertyMap(query.getClass()),
         new TabularSchemaExtractor().extract(query, RestXMLDataSource.TYPE),
         new LinkedHashMap<>(schema.params()), "resturl-test");

      assertEquals("/api/books", query.getSuffix());
      assertEquals("/bookstore/book", query.getXpath());
   }

   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static ConfigurationContext previous;
}
