/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82377: without an MV the selection lists on one table share one realtime meta data.
 * A list bound to a column the data does not produce (a stale binding in the saved column
 * selection of a rotated table) used to make that meta data throw "Column not found in
 * table", leaving every list on the table empty. Only the stale list may be empty.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  SelectionListStaleColumnMetaDataTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // starts a Spring context and a sandbox (~13 s)
class SelectionListStaleColumnMetaDataTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // no runtime MV: the realtime meta data is used
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   private static final String STALE = "Column [234]";

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void staleBoundListDoesNotEmptyTheOtherListsOfTheTable() throws Exception {
      Viewsheet vs = reset(true);
      assertNotNull(list(vs, "SelectionList1"), "list on the first column");
      assertNotNull(list(vs, "SelectionList3"), "list on the second column");
      assertTrue(list(vs, "SelectionList1").getSelectionValueCount() > 0);
      assertTrue(list(vs, "SelectionList3").getSelectionValueCount() > 0);
      // nothing to show for the column the data does not have
      assertTrue(list(vs, "SelectionList2") == null ||
                 list(vs, "SelectionList2").getSelectionValueCount() == 0);
   }

   @Test
   void controlWithoutTheStaleListHasValues() throws Exception {
      Viewsheet vs = reset(false);
      assertTrue(list(vs, "SelectionList1").getSelectionValueCount() > 0);
      assertTrue(list(vs, "SelectionList3").getSelectionValueCount() > 0);
   }

   private static SelectionList list(Viewsheet vs, String name) {
      return ((SelectionListVSAssembly) vs.getAssembly(name)).getSelectionList();
   }

   private Viewsheet reset(boolean withStaleList) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly orders = new EmbeddedTableAssembly(ws, "ORDERS1");
      orders.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.BOOLEAN },
         new Object[][] { { "DISCOUNT", "CUSTOMER_ID", "PAID" },
                          { "0_00", 23, false }, { "0_15", 7, true } }));
      ws.addAssembly(orders);
      RotatedTableAssembly query = new RotatedTableAssembly(ws, "Query1", orders);
      // the saved column selection of the rotated table still has a column the data lacks
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("DISCOUNT"));
      cols.addAttribute(column("0_00"));
      cols.addAttribute(column(STALE));
      query.setColumnSelection(cols);
      ws.addAssembly(query);

      Viewsheet vs = new Viewsheet();
      Method method = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      method.setAccessible(true);
      method.invoke(vs, ws);
      AssetEntry base = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                       "Bug82377WS", null);
      vs.setBaseEntry(base);
      selection(vs, "SelectionList1", "0_00");

      if(withStaleList) {
         selection(vs, "SelectionList2", STALE);
      }

      selection(vs, "SelectionList3", "DISCOUNT");
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "test/Bug82377", null,
                                        Organization.getDefaultOrganizationID());
      vs.setEntry(entry);
      AssetDataCache.getCache().clearCache();
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);

      try {
         VSUtil.resetRuntimeValues(vs, false);
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, false, null);
      }
      finally {
         box.dispose();
      }

      return vs;
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static void selection(Viewsheet vs, String name, String col) {
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, name);
      assembly.setTableName("Query1");
      assembly.setDataRef(column(col));
      vs.addAssembly(assembly);
   }
}
