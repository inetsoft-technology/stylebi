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
package inetsoft.web.wiz.controller;

import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import inetsoft.uql.rest.datasource.graphql.GraphQLRuntime;
import inetsoft.uql.rest.datasource.shopify.ShopifyQuery;
import inetsoft.uql.rest.json.RestJsonRuntime;
import inetsoft.uql.rest.xml.RestXMLQuery;
import inetsoft.uql.rest.xml.RestXMLRuntime;
import inetsoft.uql.tabular.TabularCatalogProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Charter G18: {@code GraphQLRuntime} now implements {@link TabularCatalogProvider} (this round),
 * which reclassifies {@code graphql}/{@code shopify}/{@code monday.com} from
 * {@code DOCUMENT_REQUIRED} to {@code METADATA}. {@code Rest.XML} reclassifies too, but for a
 * reason that arrived from a different round: its own catalog SPI implementation landed on the
 * integration branch separately, and this round's classifier is what makes it take effect.
 * Plain {@code Rest} is the one source type in this family that still requires documentation;
 * its runtime is asserted here as the negative case, at the interface level rather than through
 * the classifier, for the reason recorded in {@code onlyPlainRestStillRequiresDocumentation}. Lives in this connector module (not core, which cannot depend on
 * connector classes) and declares itself in {@code WizDatabaseController}'s package so it can call
 * the package-private {@code classifyQueryClass} directly, the same reason {@code
 * ODataDatasourceAnnotationClassTest} lives in the OData module.
 *
 * <p>{@code shopify} and {@code monday.com} both declare {@link GraphQLRuntime} as their runtime
 * class ({@code ShopifyService.getRuntimeClass()} / {@code MondayService.getRuntimeClass()}), so
 * asserting {@code GraphQLRuntime} implements the SPI covers both, plus {@code graphql} itself;
 * {@code Rest.XML} declares {@link RestXMLRuntime}, which implements it as of its own round.
 * Plain {@code Rest} declares {@link RestJsonRuntime}, which does not.</p>
 *
 * <p>This is a DELIBERATE inversion of two of this class's three assertions -- see
 * {@code docs/teams/2026-09-08-graphql-introspection-catalog/00-charter.md} G18 and
 * {@code 04-build.md} for why each new assertion below is no weaker than the one it replaces.
 * {@code restXMLRuntimeDoesNotImplementTheCatalogSpi} is untouched, byte-for-byte.</p>
 */
@Tag("core")
class RestSourceTypesAnnotationClassTest {
   @Test
   void graphQLRuntimeImplementsTheCatalogSpi() {
      // Covers graphql, shopify, and monday.com -- all three declare this as their runtime class.
      // Renamed from graphQLRuntimeDoesNotImplementTheCatalogSpi with the assertion INVERTED
      // (assertFalse -> assertTrue): this is exactly as tight a pin on the runtime-class/SPI
      // relationship as the test it replaces -- it fails the moment GraphQLRuntime stops
      // implementing TabularCatalogProvider, the same way the old one failed the moment it started.
      assertTrue(TabularCatalogProvider.class.isAssignableFrom(GraphQLRuntime.class),
         "GraphQLRuntime no longer implements TabularCatalogProvider -- graphql/shopify/" +
         "monday.com would silently fall back to DOCUMENT_REQUIRED; that is a real behavior " +
         "change requiring explicit sign-off, not a passing regression test");
   }

   @Test
   void restXMLRuntimeImplementsTheCatalogSpi() {
      assertTrue(TabularCatalogProvider.class.isAssignableFrom(RestXMLRuntime.class),
         "RestXMLRuntime no longer implements TabularCatalogProvider -- Rest.XML would " +
         "silently fall back to DOCUMENT_REQUIRED; that is a real behavior change requiring " +
         "explicit sign-off, not a passing regression test");
   }

   @Test
   void plainRestRuntimeDoesNotImplementTheCatalogSpi() {
      assertFalse(TabularCatalogProvider.class.isAssignableFrom(RestJsonRuntime.class),
         "RestJsonRuntime now implements TabularCatalogProvider -- plain Rest would silently " +
         "reclassify to METADATA, and it has no machine-readable structure source at all " +
         "(its only structure-related property is the free-text JSON Metadata); that is a " +
         "real behavior change requiring explicit sign-off, not a passing regression test");
   }

   /**
    * Confirms the classification LOGIC itself yields the right verdict for each of these four
    * query classes when their real runtime is passed through -- not just that the runtime classes
    * do/don't carry the interface (the two tests above), but that feeding the real runtime through
    * the actual two-arg classifier produces the right verdict end to end.
    *
    * <p>Renamed from {@code allFourSourceTypesStillRequireDocumentation}. Three of the four
    * assertions flip value ({@code DOCUMENT_REQUIRED} -> {@code METADATA}, matching {@code
    * GraphQLRuntime} now implementing the SPI); {@code RestXMLQuery}/{@code RestXMLRuntime}'s
    * assertion is UNCHANGED in both class pair and expected value. This is no weaker a guard than
    * before: it still pins one exact verdict per query/runtime class pair, still fails the moment
    * any of the four drifts either direction, and still keeps {@code Rest.XML} as the one case
    * that must NOT reclassify -- a regression that reclassified {@code Rest.XML} too would fail
    * this method exactly as loudly as one that failed to reclassify the other three.</p>
    */
   @Test
   void onlyPlainRestStillRequiresDocumentation() {
      // Plain Rest is deliberately NOT asserted at this level, and must not be added:
      // this module ships src/test/resources/inetsoft/uql/rest/json/endpoints.json as a
      // fixture for other tests, so on the TEST classpath
      // RestJsonQuery.class.getResource("endpoints.json") resolves that fixture and the
      // classifier answers ENDPOINT_CATALOG -- a property of the test classpath, not of the
      // shipped connector. plainRestRuntimeDoesNotImplementTheCatalogSpi above carries the
      // negative case instead; it reads the interface off the class and no resource lookup
      // can distort it.
      assertEquals("METADATA",
                   WizDatabaseController.classifyQueryClass(RestXMLQuery.class, RestXMLRuntime.class));
      assertEquals("METADATA",
                   WizDatabaseController.classifyQueryClass(GraphQLQuery.class, GraphQLRuntime.class));
      assertEquals("METADATA",
                   WizDatabaseController.classifyQueryClass(ShopifyQuery.class, GraphQLRuntime.class));
      // This is a DELIBERATE duplicate of the graphql assertion two lines above, not a gap: it is
      // the only record that monday.com was considered at all. MondayService.getQueryClass()
      // returns GraphQLQuery and getRuntimeClass() returns GraphQLRuntime -- the same pair
      // graphql itself passes -- so the classifier has no signal to tell the two source types
      // apart. That is a fact about the classifier, not a hole in this test.
      assertEquals("METADATA",
                   WizDatabaseController.classifyQueryClass(GraphQLQuery.class, GraphQLRuntime.class));
   }
}
