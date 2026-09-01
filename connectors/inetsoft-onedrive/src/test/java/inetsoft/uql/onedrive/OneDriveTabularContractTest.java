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
package inetsoft.uql.onedrive;

import inetsoft.uql.tabular.PropertyMeta;
import inetsoft.uql.tabular.TabularQuerySchema;
import inetsoft.uql.tabular.TabularSchemaExtractor;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.wiz.service.TabularQueryContractSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Confirms that {@code create_worksheet_table} builds a real OneDrive-backed table through the
 * already-generic {@code TabularQueryContractSupport.applyQueryContract} -- OneDrive needs no
 * StyleBI-side change for this, because {@code OneDriveQuery.path} is a plain {@code String}
 * property, so {@code applyQueryContract} routes it through its ordinary bean-property-write
 * branch rather than the {@code java.io.File}-typed one. Lives beside
 * {@code OneDriveRuntimeTests} rather than in core's own
 * {@code TabularQueryContractSupportTest} (which tests the mechanism itself against its
 * own fake fixtures) because this test's whole point is that OneDrive's REAL property names round-
 * trip through it -- something only a real (non-mock) {@code OneDriveQuery} can prove.
 */
@Tag("core")
class OneDriveTabularContractTest {
   /**
    * Mirrors {@code TabularQueryContractSupportTest}'s identical setup: {@code LayoutCreator}
    * resolves a view's label through {@code Config.getConfig()}, a Spring bean this plain unit
    * test has no context for.
    */
   @BeforeAll
   static void installContext() {
      previous = ConfigurationContext.getContext();
      Config config = mock(Config.class);
      when(config.getResourceBundle(any())).thenReturn(null);

      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(Config.class)).thenReturn(config);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @AfterAll
   static void clearContext() {
      if(previous != null) {
         previous.setApplicationContext(null);
      }
   }

   private static ConfigurationContext previous;

   private static String apply(OneDriveQuery query, Map<String, Object> queryParams)
      throws Exception
   {
      Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(query.getClass());
      TabularQuerySchema schema = new TabularSchemaExtractor().extract(query, query.getType());
      return TabularQueryContractSupport.applyQueryContract(
         query, pmap, schema, queryParams, "myds");
   }

   @Test
   void pathRoundTripsThroughTheOrdinaryStringBranch() throws Exception {
      OneDriveQuery query = new OneDriveQuery();

      apply(query, Map.of("path", "Test/Sales.csv"));

      assertEquals("Test/Sales.csv", query.getPath());
   }

   @Test
   void unknownPropertyNameIsRejectedWithOneDrivesOwnRealPropertyList() {
      OneDriveQuery query = new OneDriveQuery();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> apply(query, Map.of("bogusParam", "x")));
      assertTrue(ex.getMessage().contains("bogusParam"), ex.getMessage());
      assertTrue(ex.getMessage().contains("path"), ex.getMessage());
   }
}
