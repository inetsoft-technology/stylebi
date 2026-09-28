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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77123 end to end on a worksheet the product builds itself, with a real data cache and
 * nothing on the query path stubbed: table A has the pre-condition "id one of (S.id)", and
 * S's data fails while the sub table is built (as a query timeout would). The query of A
 * must fail instead of returning A unfiltered, and nothing may cache the failure, so once
 * S recovers the same sandbox returns the filtered rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, SubQueryConditionFailureCacheTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SubQueryConditionFailureCacheTest {
   /**
    * A real data cache, so the worksheet's tables are cached as in the product.
    */
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   /**
    * The data keys are content based, so without this a test could read rows another test
    * cached (kept for 15 s) instead of running its own query.
    */
   @BeforeEach
   void setUp() {
      AssetDataCache.getCache().clearCache();
   }

   @AfterEach
   void tearDown() {
      FAIL.set(false);

      if(box != null) {
         box.dispose();
      }
   }

   @Test
   void healthySubQueryFilters() throws Exception {
      box = new AssetQuerySandbox(worksheet(false));
      assertEquals(List.of(2), ids(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)));
   }

   @Test
   void failingSubQueryFailsTheQueryAndIsNotCached() throws Exception {
      box = new AssetQuerySandbox(worksheet(false));
      assertFailsNotUnfiltered();

      // the sub query recovers: the same sandbox and data cache return the filtered rows
      FAIL.set(false);
      assertEquals(List.of(2), ids(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)));
   }

   @Test
   void failingCorrelatedSubQueryFailsTheQueryAndIsNotCached() throws Exception {
      box = new AssetQuerySandbox(worksheet(true));
      assertFailsNotUnfiltered();

      FAIL.set(false);
      assertEquals(List.of(2), ids(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE)));
   }

   private void assertFailsNotUnfiltered() {
      FAIL.set(true);
      List<Integer> rows = new ArrayList<>();
      MessageException ex = assertThrows(MessageException.class, () ->
         rows.addAll(ids(box.getTableLens("A", AssetQuerySandbox.RUNTIME_MODE))),
         () -> "the sub-query condition was dropped, A returned " + rows);
      assertEquals(TIMEOUT, ex.getMessage());
   }

   private static List<Integer> ids(TableLens table) {
      assertNotNull(table);
      List<Integer> ids = new ArrayList<>();

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         ids.add(((Number) table.getObject(r, 0)).intValue());
      }

      return ids;
   }

   /**
    * A (id 1..3, grp 0) with the pre-condition {@code A.id one of (S.id)}, S being
    * {@code (id 2, grp 0)}, correlated on {@code grp} if asked.
    */
   private static Worksheet worksheet(boolean correlated) {
      Worksheet ws = new Worksheet();
      String[] types = { XSchema.INTEGER, XSchema.INTEGER };
      EmbeddedTableAssembly a = new EmbeddedTableAssembly(ws, "A");
      a.setEmbeddedData(new XEmbeddedTable(types, new Object[][] {
         { "id", "grp" }, { 1, 0 }, { 2, 0 }, { 3, 0 } }));
      ws.addAssembly(a);
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new FlakyData(types, new Object[][] { { "id", "grp" }, { 2, 0 } }));
      ws.addAssembly(s);

      SubQueryValue sub = new SubQueryValue();
      sub.setQuery("S");
      sub.setAttribute(s.getColumnSelection(false).getAttribute("id"));

      if(correlated) {
         sub.setSubAttribute(s.getColumnSelection(false).getAttribute("grp"));
         sub.setMainAttribute(a.getColumnSelection(false).getAttribute("grp"));
      }

      assertTrue(sub.update(ws));
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.ONE_OF);
      cond.setType(XSchema.INTEGER);
      cond.addValue(sub);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(a.getColumnSelection(false).getAttribute("id"), cond, 0));
      a.setPreConditionList(list);
      return ws;
   }

   /**
    * Embedded data whose rows can't be read while {@link #FAIL} is set, as a query that
    * timed out.
    */
   private static final class FlakyData extends XEmbeddedTable {
      FlakyData(String[] types, Object[][] data) {
         super(types, data);
      }

      @Override
      public boolean moreRows(int row) {
         check();
         return super.moreRows(row);
      }

      @Override
      public Object getObject(int r, int c) {
         check();
         return super.getObject(r, c);
      }

      private static void check() {
         if(FAIL.get()) {
            throw new MessageException(TIMEOUT);
         }
      }
   }

   private static final String TIMEOUT = "Query timeout";
   private static final AtomicBoolean FAIL = new AtomicBoolean();
   private AssetQuerySandbox box;
}
