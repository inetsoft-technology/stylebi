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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80054 (problem 1): add_table without a datasource builds an embedded table, and that branch
 * dropped the supplied maxRows. Asserts the stored cap on the created assembly directly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  EmbeddedAddTableMaxRows80054Test.CacheConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class EmbeddedAddTableMaxRows80054Test {
   @Configuration
   static class CacheConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private Worksheet ws;
   private WorksheetEditService svc;

   private void init() throws Exception {
      ws = new Worksheet();
      svc = WorksheetUnpivotDownstream78790Test.service(ws);
   }

   @Test
   void positiveMaxRowsIsStoredOnEmbeddedTable() throws Exception {
      init();
      svc.apply("TOK", agent, ed -> ed.addTable("T", 5, new String[0]));
      assertEquals(5, ((TableAssembly) ws.getAssembly("T")).getMaxRows());
   }

   @Test
   void zeroMeansNoLimit() throws Exception {
      init();
      svc.apply("TOK", agent, ed -> ed.addTable("T", 0, new String[0]));
      assertEquals(-1, ((TableAssembly) ws.getAssembly("T")).getMaxRows());
   }

   @Test
   void negativeMaxRowsIsRefusedBeforeAnythingIsCreated() throws Exception {
      init();
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.apply("TOK", agent, ed -> ed.addTable("T", -1, new String[0])));
      assertTrue(ex.getMessage().contains("maxRows cannot be negative"), ex.getMessage());
      assertNull(ws.getAssembly("T"));
   }

   @Test
   void omittedMaxRowsLeavesDefault() throws Exception {
      init();
      svc.apply("TOK", agent, ed -> ed.addTable("T", new String[0]));
      assertTrue(((TableAssembly) ws.getAssembly("T")).getMaxRows() <= 0);
   }
}
