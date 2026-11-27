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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82474: a selection tree whose level column is missing from the runtime data has no
 * selection list. Clicking or clearing a selection list that shares the tree's first level
 * must not fail in the neighbor sync, and the selection must still be applied to the other
 * assemblies.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  VSSelectionServiceEmptyNeighborTreeTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSSelectionServiceEmptyNeighborTreeTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   // in the column selection of the rotated table, but not in its runtime data
   private static final String MISSING = "Column [234]";

   private Principal savedPrincipal;
   private ViewsheetSandbox box;
   private Viewsheet vs;
   private VSSelectionService service;
   private CoreLifecycleService lifecycleService;
   private Context context;

   @BeforeEach
   void saveContextPrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      if(box != null) {
         box.dispose();
         box = null;
      }

      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void clickInListAppliesSelectionWhenNeighborTreeIsEmpty() throws Exception {
      open(MISSING);
      SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) vs.getAssembly("T");
      assertNull(tree.getSelectionList(), "precondition: the tree has no values");

      apply("C", event("PAID"));

      assertTrue(executeCalled(), "the selection must be executed");
      assertNarrowedToPaid();
      assertNull(tree.getSelectionList());
   }

   @Test
   void clearInListAfterClickWhenNeighborTreeIsEmpty() throws Exception {
      open(MISSING);

      apply("C", event("PAID"));
      assertNarrowedToPaid();

      apply("C", null);

      assertNotNarrowed();
   }

   @Test
   void clickInListWithFullyBoundNeighborTree() throws Exception {
      open("0_10");
      SelectionTreeVSAssembly tree = (SelectionTreeVSAssembly) vs.getAssembly("T");
      assertNotNull(tree.getSelectionList(), "precondition: the control tree has values");

      apply("C", event("PAID"));

      assertTrue(executeCalled(), "the selection must be executed");
      assertNarrowedToPaid();
      SelectionValue paid = tree.getSelectionList().findValue("PAID");
      assertNotNull(paid);
      assertTrue(paid.isSelected(), "the neighbor tree's PAID node follows the list");
   }

   private void open(String level2) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly src = new EmbeddedTableAssembly(ws, "SRC");
      src.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.STRING },
         new Object[][] { { "DISCOUNT", "CUSTOMER_ID", "PAID" },
                          { "0_00", "23", "false" }, { "0_15", "7", "true" },
                          { "0_10", "23", "true" } }));
      ws.addAssembly(src);
      RotatedTableAssembly query = new RotatedTableAssembly(ws, "Query1", src);
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("DISCOUNT"));
      cols.addAttribute(column("0_00"));
      cols.addAttribute(column("0_15"));
      cols.addAttribute(column("0_10"));
      cols.addAttribute(column(MISSING));
      query.setColumnSelection(cols);
      ws.addAssembly(query);

      vs = new Viewsheet();
      Method method = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      method.setAccessible(true);
      method.invoke(vs, ws);
      vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "Bug82474WS", null));
      list("A", "0_00");
      list("C", "DISCOUNT");
      list("D", "0_10");
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "T");
      tree.setTableName("Query1");
      tree.setDataRefs(new DataRef[] { column("DISCOUNT"), column(level2) });
      vs.addAssembly(tree);

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "test/Bug82474", null,
                                        Organization.getDefaultOrganizationID());
      vs.setEntry(entry);
      AssetDataCache.getCache().clearCache();
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      VSUtil.resetRuntimeValues(vs, false);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, false, null);
      assertNotNarrowed();

      lifecycleService = mock(CoreLifecycleService.class);
      when(lifecycleService.createList(anyBoolean(), any(), any(), any()))
         .thenAnswer(inv -> new ChangedAssemblyList());
      service = new VSSelectionService(
         lifecycleService, mock(ViewsheetService.class), mock(MaxModeAssemblyService.class),
         mock(SharedFilterService.class));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getViewsheet()).thenReturn(vs);
      context = Context.builder().rvs(rvs).principal(mock(Principal.class))
         .dispatcher(mock(CommandDispatcher.class)).linkUri("").build();
   }

   private void apply(String name, ApplySelectionListEvent event) throws Exception {
      Method apply = VSSelectionService.class.getDeclaredMethod(
         "applySelection", SelectionVSAssembly.class, ApplySelectionListEvent.class,
         Context.class);
      apply.setAccessible(true);

      try {
         apply.invoke(service, vs.getAssembly(name), event, context);
      }
      catch(InvocationTargetException e) {
         throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
      }
   }

   private boolean executeCalled() {
      return mockingDetails(lifecycleService).getInvocations().stream()
         .anyMatch(i -> i.getMethod().getName().equals("execute"));
   }

   // PAID selected in C: A (0_00) keeps "false", D (0_10) keeps "true", "23" is excluded
   private void assertNarrowedToPaid() {
      assertExcluded("A", "false", false);
      assertExcluded("A", "23", true);
      assertExcluded("D", "true", false);
      assertExcluded("D", "23", true);
   }

   private void assertNotNarrowed() {
      assertExcluded("A", "false", false);
      assertExcluded("A", "23", false);
      assertExcluded("D", "true", false);
      assertExcluded("D", "23", false);
   }

   private void assertExcluded(String name, String value, boolean excluded) {
      SelectionList list = ((SelectionListVSAssembly) vs.getAssembly(name)).getSelectionList();
      assertNotNull(list, name + " has no values");
      SelectionValue svalue = list.findValue(value);
      assertNotNull(svalue, name + " has no value " + value);
      assertEquals(excluded, (svalue.getState() & SelectionValue.STATE_EXCLUDED) != 0,
                   name + "." + value + " state " + svalue.getState());
   }

   private static ApplySelectionListEvent event(String value) {
      ApplySelectionListEvent evt = new ApplySelectionListEvent();
      ApplySelectionListEvent.Value v = new ApplySelectionListEvent.Value();
      v.setValue(new String[] { value });
      v.setSelected(true);
      evt.setValues(new ArrayList<>(List.of(v)));
      return evt;
   }

   private static ColumnRef column(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private void list(String name, String col) {
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, name);
      assembly.setTableName("Query1");
      assembly.setDataRef(column(col));
      vs.addAssembly(assembly);
   }
}
