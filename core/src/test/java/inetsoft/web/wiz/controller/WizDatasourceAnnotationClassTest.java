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

import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.web.wiz.controller.cataloged.CatalogedTestQueries;
import inetsoft.uql.tabular.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a data source is classified for annotation.
 *
 * <p>The value drives what the portal asks of the user — index it silently, demand a document
 * first, or refuse — so a wrong verdict is not a cosmetic problem: classifying one of the 65
 * catalogued connectors as DOCUMENT_REQUIRED would demand documentation for a connector that ships
 * its own catalogue.</p>
 */
@Tag("core")
class WizDatasourceAnnotationClassTest {
   @Test
   void scriptedQueriesCannotBeAnnotated() {
      // Output shape is whatever the user's script returns, so there is no stable target.
      assertEquals("UNSUPPORTED", WizDatabaseController.classifyQueryClass(ScriptedTestQuery.class));
   }

   @Test
   void aQueryShippingAnEndpointCatalogNeedsNoDocumentation() {
      // endpoints.json sits beside this test class, standing in for the 65 connectors that ship one.
      assertEquals("ENDPOINT_CATALOG",
                   WizDatabaseController.classifyQueryClass(CatalogedTestQueries.CatalogedQuery.class));
   }

   @Test
   void aRestQueryWithoutACatalogRequiresDocumentation() {
      assertEquals("DOCUMENT_REQUIRED",
                   WizDatabaseController.classifyQueryClass(PlainRestTestQuery.class));
   }

   @Test
   void aBrowsableQueryIsAFileSource() {
      assertEquals("FILE", WizDatabaseController.classifyQueryClass(SelectableTestQuery.class));
   }

   @Test
   void anythingElseIsAskedForItsMetadata() {
      assertEquals("METADATA", WizDatabaseController.classifyQueryClass(PlainTestQuery.class));
   }

   /**
    * The ordering constraint, and the reason the checks cannot be reordered for tidiness.
    *
    * <p>Every catalogued connector IS a REST query — {@code EndpointJsonQuery} extends
    * {@code RestJsonQuery} — so testing REST first would classify all 65 of them as needing
    * documentation they already ship. This class is both, and must come out as the catalogue.</p>
    */
   @Test
   void aCatalogedQueryThatIsAlsoRestIsStillACatalog() {
      assertEquals("ENDPOINT_CATALOG",
                   WizDatabaseController.classifyQueryClass(CatalogedTestQueries.CatalogedRestQuery.class));
   }

   /**
    * The runtime is the authoritative "can this be annotated without a document" signal: a REST
    * query whose declared runtime implements {@link TabularCatalogProvider} is METADATA, not
    * DOCUMENT_REQUIRED, even though its query class descends from {@code AbstractRestQuery}.
    */
   @Test
   void aQueryWhoseRuntimeImplementsTheCatalogSpiIsMetadata() {
      assertEquals("METADATA", WizDatabaseController.classifyQueryClass(
         PlainRestTestQuery.class, SpiRuntimeStub.class));
   }

   /**
    * A REST query whose runtime does NOT implement the SPI is unaffected by the new check --
    * same verdict as before the runtime signal existed, this time asserted with an explicit
    * "no runtime declared" (null) second argument rather than the one-arg overload.
    */
   @Test
   void aRestQueryWithNoSpiRuntimeStillRequiresDocumentation() {
      assertEquals("DOCUMENT_REQUIRED",
                   WizDatabaseController.classifyQueryClass(PlainRestTestQuery.class, null));
   }

   /**
    * The ordering guard for a query that is BOTH catalogued (ships endpoints.json) AND has a
    * runtime implementing the SPI. Unreachable by any shipped connector today (charter: zero
    * overlap between the two sets) -- exactly why this must be asserted, not argued: the ordering
    * inside {@code classifyQueryClass} places the SPI check after the endpoints.json check, so a
    * future connector that is both must still come out as the catalogue it already ships.
    */
   @Test
   void aCatalogedQueryThatAlsoImplementsTheSpiIsStillACatalog() {
      assertEquals("ENDPOINT_CATALOG", WizDatabaseController.classifyQueryClass(
         CatalogedTestQueries.CatalogedRestQuery.class, SpiRuntimeStub.class));
   }

   /**
    * Same ordering guard for {@code ScriptedQuery}: a scripted query's output shape is arbitrary
    * user script output with no stable target, regardless of what its runtime declares.
    */
   @Test
   void aScriptedQueryIsStillUnsupportedEvenWithASpiRuntime() {
      assertEquals("UNSUPPORTED", WizDatabaseController.classifyQueryClass(
         ScriptedTestQuery.class, SpiRuntimeStub.class));
   }

   /**
    * Charter A2b: the one-arg overload must be provably the same as the two-arg form called with
    * a null runtime class -- for one query class of each of the five verdicts -- so the overload
    * cannot silently drift from "no runtime signal" behavior as this method evolves.
    */
   @Test
   void theOneArgOverloadMatchesTheTwoArgFormWithNullRuntimeForEveryVerdict() {
      assertEquals(WizDatabaseController.classifyQueryClass(ScriptedTestQuery.class),
                   WizDatabaseController.classifyQueryClass(ScriptedTestQuery.class, null));
      assertEquals(WizDatabaseController.classifyQueryClass(CatalogedTestQueries.CatalogedQuery.class),
                   WizDatabaseController.classifyQueryClass(CatalogedTestQueries.CatalogedQuery.class, null));
      assertEquals(WizDatabaseController.classifyQueryClass(PlainRestTestQuery.class),
                   WizDatabaseController.classifyQueryClass(PlainRestTestQuery.class, null));
      assertEquals(WizDatabaseController.classifyQueryClass(SelectableTestQuery.class),
                   WizDatabaseController.classifyQueryClass(SelectableTestQuery.class, null));
      assertEquals(WizDatabaseController.classifyQueryClass(PlainTestQuery.class),
                   WizDatabaseController.classifyQueryClass(PlainTestQuery.class, null));
   }

   private static class PlainTestQuery extends TabularQuery {
      PlainTestQuery() {
         super("TEST");
      }
   }

   private static class ScriptedTestQuery extends TabularQuery implements ScriptedQuery {
      ScriptedTestQuery() {
         super("TEST");
      }

      @Override
      public String getInputScript() {
         return null;
      }

      @Override
      public String getOutputScript() {
         return null;
      }
   }

   private static class SelectableTestQuery extends SelectableTabularQuery {
      SelectableTestQuery() {
         super("TEST");
      }

      @Override
      protected ColumnDefinition[] loadColumns() {
         return new ColumnDefinition[0];
      }
   }

   private static class PlainRestTestQuery extends AbstractRestQuery {
      PlainRestTestQuery() {
         super("TEST");
      }
   }

   /**
    * A minimal SPI implementer used only as a {@code .class} literal -- never instantiated or
    * called. Classification is a pure {@code Class}-level {@code isAssignableFrom} test (charter
    * A6: no runtime is ever instantiated), so every method here throws, making this class double
    * as a live tripwire: any test above that used it as a runtime signal would itself fail loudly
    * if classification ever tried to construct or call it.
    */
   private static class SpiRuntimeStub implements TabularCatalogProvider {
      @Override
      public TabularCatalog listDatasets(TabularDataSource<?> dataSource) {
         throw new UnsupportedOperationException("must never be called by classification");
      }

      @Override
      public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId) {
         throw new UnsupportedOperationException("must never be called by classification");
      }
   }

}
