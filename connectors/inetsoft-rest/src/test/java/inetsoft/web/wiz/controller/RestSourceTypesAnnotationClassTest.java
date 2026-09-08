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
import inetsoft.uql.rest.xml.RestXMLQuery;
import inetsoft.uql.rest.xml.RestXMLRuntime;
import inetsoft.uql.tabular.TabularCatalogProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Charter A8 (counter-assertion): none of the four source types this round's classifier is
 * theoretically able to reclassify -- {@code Rest.XML}, {@code graphql}, {@code shopify},
 * {@code monday.com} -- may actually change classification, because none of their runtimes
 * implements {@link TabularCatalogProvider} today. Lives in this connector module (not core,
 * which cannot depend on connector classes) and declares itself in {@code
 * WizDatabaseController}'s package so it can call the package-private {@code classifyQueryClass}
 * directly, the same reason {@code ODataDatasourceAnnotationClassTest} lives in the OData module.
 *
 * <p>{@code shopify} and {@code monday.com} both declare {@link GraphQLRuntime} as their runtime
 * class ({@code ShopifyService.getRuntimeClass()} / {@code MondayService.getRuntimeClass()}), so
 * asserting {@code GraphQLRuntime} does not implement the SPI covers both, plus {@code graphql}
 * itself; {@code Rest.XML} declares {@link RestXMLRuntime}.</p>
 */
@Tag("core")
class RestSourceTypesAnnotationClassTest {
   @Test
   void graphQLRuntimeDoesNotImplementTheCatalogSpi() {
      // Covers graphql, shopify, and monday.com -- all three declare this as their runtime class.
      assertFalse(TabularCatalogProvider.class.isAssignableFrom(GraphQLRuntime.class),
         "GraphQLRuntime now implements TabularCatalogProvider -- this round's classifier would " +
         "silently reclassify graphql/shopify/monday.com to METADATA; that is a real behavior " +
         "change requiring explicit sign-off, not a passing regression test");
   }

   @Test
   void restXMLRuntimeDoesNotImplementTheCatalogSpi() {
      assertFalse(TabularCatalogProvider.class.isAssignableFrom(RestXMLRuntime.class),
         "RestXMLRuntime now implements TabularCatalogProvider -- this round's classifier would " +
         "silently reclassify Rest.XML to METADATA; that is a real behavior change requiring " +
         "explicit sign-off, not a passing regression test");
   }

   /**
    * Confirms the classification LOGIC itself still yields DOCUMENT_REQUIRED for these query
    * classes when their real runtime is passed through -- not just that the runtime classes lack
    * the interface (the two tests above), but that feeding the real runtime through the actual
    * two-arg classifier produces the unchanged verdict end to end.
    */
   @Test
   void allFourSourceTypesStillRequireDocumentation() {
      assertEquals("DOCUMENT_REQUIRED",
                   WizDatabaseController.classifyQueryClass(RestXMLQuery.class, RestXMLRuntime.class));
      assertEquals("DOCUMENT_REQUIRED",
                   WizDatabaseController.classifyQueryClass(GraphQLQuery.class, GraphQLRuntime.class));
      assertEquals("DOCUMENT_REQUIRED",
                   WizDatabaseController.classifyQueryClass(ShopifyQuery.class, GraphQLRuntime.class));
      // monday.com declares GraphQLQuery itself as its query class (no separate Monday query
      // type) -- see MondayService.getQueryClass().
      assertEquals("DOCUMENT_REQUIRED",
                   WizDatabaseController.classifyQueryClass(GraphQLQuery.class, GraphQLRuntime.class));
   }
}
