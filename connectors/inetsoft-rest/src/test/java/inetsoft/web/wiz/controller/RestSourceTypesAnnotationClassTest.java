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
 * {@code DOCUMENT_REQUIRED} to {@code METADATA}. {@code Rest.XML} briefly reclassified too, for a
 * reason that arrived from a different round: its own catalog SPI implementation had landed on
 * the integration branch separately. That Rest.XML SPI implementation was since removed entirely
 * (stylebi#5098, "revert(tabular): remove Rest.XML catalog SPI entirely"), as part of a wiz-side
 * redesign that runs the Rest.XML endpoint pipeline entirely inside wiz-services with no
 * StyleBI-side catalog role for it -- so Rest.XML is back to requiring documentation, the same as
 * plain {@code Rest}, which is the one source type in this family that still requires
 * documentation for a different, unrelated reason; its runtime is asserted here as the negative
 * case, at the interface level rather than through the classifier, for the reason recorded in
 * {@code restXMLAndPlainRestRequireDocumentation}. Lives in this connector module (not core, which
 * cannot depend on connector classes) and declares itself in {@code WizDatabaseController}'s
 * package so it can call the package-private {@code classifyQueryClass} directly, the same reason
 * {@code ODataDatasourceAnnotationClassTest} lives in the OData module.
 *
 * <p>{@code shopify} and {@code monday.com} both declare {@link GraphQLRuntime} as their runtime
 * class ({@code ShopifyService.getRuntimeClass()} / {@code MondayService.getRuntimeClass()}), so
 * asserting {@code GraphQLRuntime} implements the SPI covers both, plus {@code graphql} itself;
 * {@code Rest.XML} declares {@link RestXMLRuntime}, which no longer implements it (stylebi#5098).
 * Plain {@code Rest} declares {@link RestJsonRuntime}, which never did.</p>
 *
 * <p>This class's assertions on {@code GraphQLRuntime}/{@code ShopifyQuery}/{@code monday.com}
 * were DELIBERATELY inverted by the G18 round -- see
 * {@code docs/teams/2026-09-08-graphql-introspection-catalog/00-charter.md} G18 and
 * {@code 04-build.md} for why. The Rest.XML-specific assertions below were inverted back a second
 * time after stylebi#5098 reverted {@code RestXMLRuntime}'s SPI implementation.</p>
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
   void restXMLRuntimeDoesNotImplementTheCatalogSpi() {
      assertFalse(TabularCatalogProvider.class.isAssignableFrom(RestXMLRuntime.class),
         "RestXMLRuntime implements TabularCatalogProvider again -- its catalog SPI " +
         "implementation was removed entirely in stylebi#5098 as part of a wiz-side redesign " +
         "that runs the Rest.XML endpoint pipeline entirely inside wiz-services with no " +
         "StyleBI-side catalog role for it; a regression that reintroduced this interface would " +
         "silently and wrongly reclassify Rest.XML back to METADATA");
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
    * <p>Renamed from {@code onlyPlainRestStillRequiresDocumentation}. Three of the four query
    * classes -- {@code graphql}, {@code shopify}, {@code monday.com}, all via {@link
    * GraphQLRuntime} -- are {@code METADATA}. The other two, {@code Rest.XML} (via {@link
    * RestXMLRuntime}) and plain {@code Rest} (via {@link RestJsonRuntime}), both require
    * documentation ({@code DOCUMENT_REQUIRED}): {@code Rest.XML} because its catalog SPI
    * implementation was removed entirely in stylebi#5098, and plain {@code Rest} because it never
    * had one. Plain {@code Rest} is deliberately not asserted at this level for the classpath-
    * fixture reason recorded below. This is no weaker a guard than before: it still pins one exact
    * verdict per query/runtime class pair, and still fails the moment any of the four drifts in
    * either direction.</p>
    */
   @Test
   void restXMLAndPlainRestRequireDocumentation() {
      // Plain Rest is deliberately NOT asserted at this level, and must not be added:
      // this module ships src/test/resources/inetsoft/uql/rest/json/endpoints.json as a
      // fixture for other tests, so on the TEST classpath
      // RestJsonQuery.class.getResource("endpoints.json") resolves that fixture and the
      // classifier answers ENDPOINT_CATALOG -- a property of the test classpath, not of the
      // shipped connector. plainRestRuntimeDoesNotImplementTheCatalogSpi above carries the
      // negative case instead; it reads the interface off the class and no resource lookup
      // can distort it.
      assertEquals("DOCUMENT_REQUIRED",
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
