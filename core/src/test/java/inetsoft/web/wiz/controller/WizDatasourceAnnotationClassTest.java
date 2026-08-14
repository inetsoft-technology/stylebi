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

}
