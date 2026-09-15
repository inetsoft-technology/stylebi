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

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.RestRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for Redmine #76690 / WBT-005: a Rest.XML {@code xpath} using the response
 * document's own declared namespace prefix (e.g. {@code /wb:countries/wb:country} against a
 * document declaring {@code xmlns:wb="..."}) previously failed to compile (unresolvable prefix,
 * since the generated stylesheet only ever bound {@code xsl}/{@code is}), while an unprefixed
 * xpath silently matched nothing and was reported as one fabricated null-column row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class RestXmlNamespaceXpathTest {
   @Test
   void prefixedXpathMatchesRealDataAgainstDocumentNamespace() throws Exception {
      final XTableNode table = runQuery("/wb:countries/wb:country");

      assertNotNull(table, "a correctly-prefixed xpath must resolve against the response " +
         "document's own namespace binding and load real rows");
      // XNodeTableLens.getRowCount() counts the header row, so 2 real wb:country rows -> 3.
      assertEquals(3, rowCount(table));
   }

   @Test
   void unprefixedXpathStillMatchesNothingInNamespacedDocument() throws Exception {
      // Spec-correct XSLT/XPath 1.0 behavior, not a defect: an unprefixed name test only
      // matches elements in no namespace, and every real element here is in the wb: namespace.
      // This is the pre-existing "one fabricated null-column row" quirk (EditableNode's
      // empty-to-ValueNode(null) fallback) - out of scope to fix here, just guarded against
      // regressing into something worse (e.g. an exception, or more than one row).
      final XTableNode table = runQuery("/countries/country");

      assertNotNull(table);
      // 1 fabricated null-column row + the header row XNodeTableLens.getRowCount() counts.
      assertEquals(2, rowCount(table));
   }

   @Test
   void localNameWildcardIdiomStillWorks() throws Exception {
      final XTableNode table = runQuery("/*[local-name()='countries']/*[local-name()='country']");

      assertNotNull(table);
      assertEquals(3, rowCount(table));
   }

   @Test
   void unresolvablePrefixSurfacesAsWizLoadColumnsError() throws Exception {
      // "typo" is not a prefix the response document declares (only "wb" is), so injecting the
      // document's real namespace bindings does not resolve it - the stylesheet still fails to
      // compile. Before the Part 2 fix, this exception was swallowed with wizLoadColumnsError
      // left null; it must now be captured with a named cause.
      final RestXMLQuery query = createQuery("/typo:countries/typo:country");
      createRunner(query).run();

      final Object loadError = query.getProperty("wizLoadColumnsError");
      assertNotNull(loadError, "an unresolvable namespace prefix must surface a named " +
         "wizLoadColumnsError instead of leaving it null");
      assertFalse(loadError.toString().isBlank());
   }

   private static int rowCount(XTableNode table) {
      final XNodeTableLens lens = new XNodeTableLens(table);
      lens.moreRows(Integer.MAX_VALUE);
      return lens.getRowCount();
   }

   private XTableNode runQuery(String xpath) throws Exception {
      final RestXMLQuery query = createQuery(xpath);
      return createRunner(query).run();
   }

   private RestXMLQuery createQuery(String xpath) {
      final RestXMLQuery query = new RestXMLQuery();
      final RestXMLDataSource dataSource = new RestXMLDataSource();
      dataSource.setURL("http://worldbank.example/v2/country");
      query.setDataSource(dataSource);
      query.setXpath(xpath);
      return query;
   }

   private RestXMLQueryRunner createRunner(RestXMLQuery query) throws Exception {
      final List<RequestResponse> requestResponses = new ArrayList<>();
      requestResponses.add(new RequestResponse(RestRequest.fromQuery(query),
         new TestHttpResponse(WORLD_BANK_FIXTURE)));

      final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
      final XMLRestDataIteratorStrategyFactory factory =
         new XMLRestDataIteratorStrategyFactory(httpHandler);

      return new RestXMLQueryRunner(query, factory);
   }

   private static final String WORLD_BANK_FIXTURE =
      "<wb:countries xmlns:wb=\"http://www.worldbank.org\">" +
         "<wb:country id=\"ABW\"><wb:name>Aruba</wb:name></wb:country>" +
         "<wb:country id=\"AFG\"><wb:name>Afghanistan</wb:name></wb:country>" +
      "</wb:countries>";
}
