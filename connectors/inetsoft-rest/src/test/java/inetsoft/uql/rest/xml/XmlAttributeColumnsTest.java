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
package inetsoft.uql.rest.xml;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.*;
import inetsoft.uql.rest.xml.parse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for Bug #79489: Rest.XML attributes become columns and an xpath ending in
 * {@code @attr} selects that attribute, for every iteration strategy (unpaged, PAGE_COUNT,
 * ITERATION).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class XmlAttributeColumnsTest {
   // ---- unpaged ----

   @Test
   void unpagedAttributeXpathYieldsAttributeColumn() throws Exception {
      final XTableNode table = runUnpaged("/wb:countries/@total");

      assertNotNull(table);
      assertEquals(Collections.singletonList("@total"), columns(table));
      assertTrue(table.next());
      assertEquals(296, ((Number) table.getObject(0)).intValue());
   }

   @Test
   void unpagedElementXpathAddsAttributeColumnsAfterExistingOnes() throws Exception {
      final XTableNode table = runUnpaged("/wb:countries/wb:country");

      assertNotNull(table);
      assertEquals(Arrays.asList("wb:country.wb:iso2Code", "wb:country.wb:name",
         "wb:country.wb:region", "wb:country.wb:region@id", "wb:country.wb:region@iso2code",
         "wb:country.wb:incomeLevel", "wb:country.wb:incomeLevel@id", "wb:country.@id"),
         columns(table));

      // the pre-existing columns keep their names (no text-column rename) and relative order
      final List<String> existing = new ArrayList<>();

      for(final String column : columns(table)) {
         if(!column.contains("@")) {
            existing.add(column);
         }
      }

      assertEquals(Arrays.asList("wb:country.wb:iso2Code", "wb:country.wb:name",
         "wb:country.wb:region", "wb:country.wb:incomeLevel"), existing);

      // rows stay aligned: row 1 is Aruba, row 2 is Afghanistan
      final List<String> cols = columns(table);
      final int id = cols.indexOf("wb:country.@id");
      final int name = cols.indexOf("wb:country.wb:name");
      final int regionId = cols.indexOf("wb:country.wb:region@id");
      assertTrue(table.next());
      assertEquals("ABW", table.getObject(id));
      assertEquals("Aruba", table.getObject(name));
      assertEquals("LCN", table.getObject(regionId));
      assertTrue(table.next());
      assertEquals("AFG", table.getObject(id));
      assertEquals("Afghanistan", table.getObject(name));
      assertEquals("SAS", table.getObject(regionId));
   }

   @Test
   void unpagedNamespaceDeclarationsAreNotColumns() throws Exception {
      final XTableNode table = runUnpaged("/wb:countries/wb:country");

      assertNotNull(table);

      for(final String column : columns(table)) {
         assertFalse(column.contains("xmlns"), column);
      }
   }

   @Test
   void unpagedBareWildcardXpathStillBindsToUserTemplate() throws Exception {
      final XTableNode table = runUnpaged("/wb:countries/*");

      assertNotNull(table);
      assertTrue(columns(table).contains("wb:country.wb:name"), columns(table).toString());
   }

   @Test
   void unpagedOptionalAttributesOnRepeatedElementsStayRowAligned() throws Exception {
      final XTableNode table = runUnpaged("/r/c", "<r><c><n>1</n></c>" +
         "<c b=\"B\"><n>2</n><t k=\"K\">x</t></c><c><n>3</n></c></r>");

      assertNotNull(table);
      final List<String> cols = columns(table);
      assertTrue(cols.contains("c.@b"), cols.toString());
      final int n = cols.indexOf("c.n");
      final int b = cols.indexOf("c.@b");
      final int tk = cols.indexOf("c.t@k");
      assertTrue(tk >= 0, cols.toString());
      final Object[][] expected = { { 1, null, null }, { 2, "B", "K" }, { 3, null, null } };

      for(final Object[] row : expected) {
         assertTrue(table.next());
         assertEquals(row[0], ((Number) table.getObject(n)).intValue());
         assertEquals(row[1], table.getObject(b));
         assertEquals(row[2], table.getObject(tk));
      }
   }

   // ---- PAGE_COUNT ----

   @Test
   void pageCountAttributeXpath() throws Exception {
      final MapNode page = pageCountPage("/wb:countries/@total");

      assertEquals(Collections.singleton("@total"), page.keySet());
      assertEquals(new ValueNode(296), page.get("@total"));
   }

   @Test
   void pageCountElementXpathHasAttributeEntries() throws Exception {
      assertCountryEntries(pageCountPage("/wb:countries/wb:country"));
   }

   // ---- ITERATION ----

   @Test
   void iterationAttributeXpath() throws Exception {
      final MapNode page = iterationPage("/wb:countries/@total");

      assertEquals(Collections.singleton("@total"), page.keySet());
      assertEquals(new ValueNode(296), page.get("@total"));
   }

   @Test
   void iterationElementXpathHasAttributeEntries() throws Exception {
      assertCountryEntries(iterationPage("/wb:countries/wb:country"));
   }

   // ---- helpers ----

   @SuppressWarnings("unchecked")
   private static void assertCountryEntries(MapNode page) {
      final List<MapNode> countries = (List<MapNode>) page.get("wb:country");
      assertEquals(2, countries.size());
      assertEquals(new ValueNode("ABW"), countries.get(0).get("@id"));
      assertEquals(new ValueNode("AFG"), countries.get(1).get("@id"));
      assertEquals(new ValueNode("LCN"), countries.get(0).get("wb:region@id"));
      assertEquals(new ValueNode("Aruba"), countries.get(0).get("wb:name"));
      assertEquals(new ValueNode("Latin America & Caribbean"),
         countries.get(0).get("wb:region"));
      assertFalse(countries.get(0).containsKey("@xmlns:wb"));
   }

   private MapNode pageCountPage(String xpath) throws Exception {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://host/{{pageNumber}}");
      query.setDataSource(dataSource);
      query.setXpath(xpath);
      final PaginationSpec spec = query.getPaginationSpec();
      spec.setType(PaginationType.PAGE_COUNT);
      spec.setPageNumberUrlVariable(
         new PaginationParameter("pageNumber", PaginationParamType.URL_VARIABLE));
      spec.setPageCountXpath(new PaginationParameter("/wb:countries/@pages",
         PaginationParamType.XPATH));
      spec.setZeroBasedPageIndex(false);

      final List<RequestResponse> rr = new ArrayList<>();
      rr.add(new RequestResponse(RestRequest.builder().query(query)
         .urlVariables(Collections.singletonMap("pageNumber", "1")).build(),
         new TestHttpResponse(fixture(""))));
      final XMLPagedPageCountIteratorStrategy strategy = new XMLPagedPageCountIteratorStrategy(
         query, new XMLPagedStreamTransformer(query), new TestHttpHandler(rr),
         new RestErrorHandler());
      return (MapNode) strategy.next();
   }

   private MapNode iterationPage(String xpath) throws Exception {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://host");
      query.setDataSource(dataSource);
      query.setXpath(xpath);
      final PaginationSpec spec = query.getPaginationSpec();
      spec.setType(PaginationType.ITERATION);
      spec.setHasNextParam(new PaginationParameter("/wb:countries/@next",
         PaginationParamType.XPATH));
      spec.setPageOffsetParamToRead(new PaginationParameter("/wb:countries/@next",
         PaginationParamType.XPATH));
      spec.setPageOffsetParamToWrite(new PaginationParameter("offset", PaginationParamType.QUERY));

      final List<RequestResponse> rr = new ArrayList<>();
      rr.add(new RequestResponse(RestRequest.builder().query(query).build(),
         new TestHttpResponse(fixture(""))));
      final XMLPagedIterationIteratorStrategy strategy = new XMLPagedIterationIteratorStrategy(
         query, new XMLIterationStreamTransformer(query), new TestHttpHandler(rr),
         new RestErrorHandler());
      return (MapNode) strategy.next();
   }

   private XTableNode runUnpaged(String xpath) throws Exception {
      return runUnpaged(xpath, fixture(""));
   }

   private XTableNode runUnpaged(String xpath, String xml) throws Exception {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://worldbank.example/v2/country");
      query.setDataSource(dataSource);
      query.setXpath(xpath);
      final List<RequestResponse> rr = new ArrayList<>();
      rr.add(new RequestResponse(RestRequest.fromQuery(query),
         new TestHttpResponse(xml)));
      return new RestXMLQueryRunner(query,
         new XMLRestDataIteratorStrategyFactory(new TestHttpHandler(rr))).run();
   }

   private static List<String> columns(XTableNode table) {
      final List<String> names = new ArrayList<>();

      for(int i = 0; i < table.getColCount(); i++) {
         names.add(table.getName(i));
      }

      return names;
   }

   private static String fixture(String next) {
      return "<wb:countries page=\"1\" pages=\"1\" per_page=\"50\" total=\"296\"" +
         (next.isEmpty() ? "" : " next=\"" + next + "\"") +
         " xmlns:wb=\"http://www.worldbank.org\">" +
         "<wb:country id=\"ABW\"><wb:iso2Code>AW</wb:iso2Code><wb:name>Aruba</wb:name>" +
         "<wb:region id=\"LCN\" iso2code=\"ZJ\">Latin America &amp; Caribbean</wb:region>" +
         "<wb:incomeLevel id=\"HIC\">High income</wb:incomeLevel></wb:country>" +
         "<wb:country id=\"AFG\"><wb:iso2Code>AF</wb:iso2Code><wb:name>Afghanistan</wb:name>" +
         "<wb:region id=\"SAS\" iso2code=\"8S\">South Asia</wb:region>" +
         "<wb:incomeLevel id=\"LIC\">Low income</wb:incomeLevel></wb:country>" +
         "</wb:countries>";
   }
}
