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

import inetsoft.uql.rest.datasource.airtable.AirtableQuery;
import inetsoft.uql.tabular.TabularCatalogProvider;
import inetsoft.uql.tabular.TabularService;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81472: how Airtable is classified for annotation, and that the runtime the connector
 * registry hands out is the one that carries the catalog.
 *
 * <p>The classification stays {@code ENDPOINT_CATALOG} on purpose: {@code classifyQueryClass}
 * checks {@code endpoints.json} before the runtime's SPI (see {@code
 * WizDatasourceAnnotationClassTest.aCatalogedQueryThatAlsoImplementsTheSpiIsStillACatalog}), and
 * Airtable ships one. That verdict does not decide whether the catalog is reachable:
 * {@code GET /datasource/tables} -- the call behind {@code list_tabular_targets} -- resolves the
 * runtime from the connector registry and asks it, whatever the classification says.
 */
class AirtableAnnotationClassTest {
   @Test
   void airtableStaysAnEndpointCatalogWhetherOrNotItsRuntimeImplementsTheSpi() {
      assertEquals("ENDPOINT_CATALOG",
                   WizDatabaseController.classifyQueryClass(AirtableQuery.class));
      assertEquals("ENDPOINT_CATALOG", WizDatabaseController.classifyQueryClass(
         AirtableQuery.class, airtableRuntimeClass()));
   }

   @Test
   void theRuntimeTheRegistryDeclaresForAirtableImplementsTheCatalogSpi() {
      assertTrue(TabularCatalogProvider.class.isAssignableFrom(airtableRuntimeClass()),
                 airtableRuntimeClass() + " must implement TabularCatalogProvider, or " +
                 "TabularCatalogService.resolveProvider answers UnsupportedDatasourceException");
   }

   private static Class<?> airtableRuntimeClass() {
      for(TabularService service : ServiceLoader.load(TabularService.class)) {
         if("Rest.Airtable".equals(service.getDataSourceType())) {
            try {
               return Class.forName(service.getRuntimeClass());
            }
            catch(ClassNotFoundException e) {
               throw new AssertionError(e);
            }
         }
      }

      throw new AssertionError("no TabularService is registered for Rest.Airtable");
   }
}
