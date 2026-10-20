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
import inetsoft.report.XSessionManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.tabular.impl.TabularHandler;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78494: a scheduled export over a tabular source whose connector query fails saved an
 * empty table and the task succeeded, because the export sandbox carries
 * {@code __is_scheduler__} as {@code Boolean.TRUE} and the connector error handling only
 * accepted the String {@code "true"}. Each case runs the real ViewsheetAction /
 * ViewsheetSandbox / AssetQuerySandbox / XSessionManager (data cache on) / TabularHandler path;
 * only the connector, which fails as the connectors do (handleError with a null fallback), is
 * stood in for.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  TabularScheduledExportFailureTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class TabularScheduledExportFailureTest {
   private static final String MESSAGE = "connector failure 78494";
   private static final AtomicInteger RUNS = new AtomicInteger();
   private static volatile boolean failing;
   private static int seq;

   @Configuration
   static class TestConfig {
      @Bean
      Drivers drivers() {
         return mock(Drivers.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      inetsoft.mv.MVManager mvManager() {
         return mock(inetsoft.mv.MVManager.class);
      }

      @Bean
      XDataService testDataService() {
         return mock(XDataService.class);
      }

      @Bean
      XSessionManager xSessionManager(XDataService testDataService, XSessionService ss)
         throws Exception
      {
         XSessionManager manager =
            new XSessionManager(testDataService, ss, mock(DataSourceRegistry.class));
         // the shared result cache is on, as on a server
         manager.setCacheData(true);
         return manager;
      }
   }

   @Autowired
   private XDataService dataService;
   private Principal principal;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() throws Exception {
      principal = ThreadContext.getContextPrincipal();
      TabularHandler handler = new TabularHandler();
      Field runtimes = TabularHandler.class.getDeclaredField("runtimes");
      runtimes.setAccessible(true);
      ((Map<String, TabularRuntime>) runtimes.get(handler)).put("Test78494", new TestRuntime());
      reset(dataService);
      // the data service stands in for XEngine.execute and runs the real tabular handler
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> handler.execute(inv.getArgument(1), inv.getArgument(2),
                                            inv.getArgument(3),
                                            (DataCacheVisitor) inv.getArgument(5)));
      RUNS.set(0);
      failing = true;
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(principal);
   }

   // the flag as the real scheduler sets it: ScheduleTask copy, the open-time parameters and
   // then the export-time parameters; the export-time query fails, and a later interactive read
   // runs the connector again instead of getting a cached empty table
   @Test
   void exportTimeQueryFailsAndIsNotCached() throws Exception {
      ScheduleTask task = new ScheduleTask("task78494");
      ViewsheetAction source = new ViewsheetAction();
      source.setViewsheet("1^128^__NULL__^vs78494");
      task.addAction(source);
      Method copy = ScheduleTask.class.getDeclaredMethod("copyScheduleTask", ScheduleTask.class);
      copy.setAccessible(true);
      ViewsheetAction action =
         (ViewsheetAction) ((ScheduleTask) copy.invoke(null, task)).getAction(0);
      Method build = ScheduleViewsheetService.class.getDeclaredMethod(
         "buildParameters", RepletRequest.class);
      build.setAccessible(true);
      VariableTable vars = (VariableTable) build.invoke(
         mock(ScheduleViewsheetService.class, CALLS_REAL_METHODS),
         action.getViewsheetRequest());
      Method schedule = ViewsheetAction.class.getDeclaredMethod(
         "setScheduleParameters", VariableTable.class);
      schedule.setAccessible(true);
      schedule.invoke(action, vars);
      assertEquals(Boolean.TRUE, vars.get("__is_scheduler__"));

      Throwable ex = assertThrows(Throwable.class, () -> query(vars));
      assertNotNull(TableLoadException.find(ex), String.valueOf(ex));
      assertTrue(String.valueOf(ex.getMessage()).contains(MESSAGE), String.valueOf(ex));

      TableLens lens = query(new VariableTable());
      assertFalse(lens.moreRows(1));
      assertEquals(2, RUNS.get(), "the failed scheduled result must not be cached");
   }

   // a real ViewsheetAction that saves the home bookmark as CSV fails on the connector error
   // and saves nothing; with the connector working the same task saves the file
   @Test
   void scheduledSaveToDiskFailsOnConnectorError() throws Exception {
      Path dir = Files.createTempDirectory("vsaction78494");

      try {
         List<String> saved = new ArrayList<>();
         Throwable ex = runSaveToDisk(dir, saved);
         assertNotNull(ex, "the scheduled task must fail");
         assertNotNull(TableLoadException.find(ex), String.valueOf(ex));
         assertTrue(saved.isEmpty(), String.valueOf(saved));
         assertEquals(0, dir.toFile().list().length);

         failing = false;
         assertNull(runSaveToDisk(dir, saved));
         assertEquals(1, saved.size(), String.valueOf(saved));
      }
      finally {
         Tool.deleteFile(dir.toFile());
      }
   }

   private static TableLens query(VariableTable vars) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(worksheet());
      box.refreshVariableTable(vars);
      return box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE);
   }

   // a worksheet with the tabular table T1 (column c1); each call uses a new data source name,
   // so cached results of earlier calls are not reused
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      TabularTableAssembly table = new TabularTableAssembly(ws, "T1");
      TestQuery query = new TestQuery();
      TestDataSource ds = new TestDataSource();
      ds.setName("ds78494-" + (++seq));
      query.setDataSource(ds);
      query.setOutputColumns(
         new XTypeNode[] { XSchema.createPrimitiveType("c1", String.class) });
      ((TabularTableAssemblyInfo) table.getTableInfo()).setQuery(query);
      ColumnSelection cols = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "c1"));
      ref.setDataType(XSchema.STRING);
      cols.addAttribute(ref);
      table.setColumnSelection(cols, false);
      table.setColumnSelection(cols.clone(), true);
      ws.addAssembly(table);
      return ws;
   }

   // runs a real ViewsheetAction that saves the home bookmark of a viewsheet with a table over
   // T1 as CSV to dir, with the scheduler flag set as ScheduleTask sets it, and only the
   // viewsheet open/close, the bookmark list and the external storage stood in for; returns
   // what the task threw, and adds each saved file to saved
   private static Throwable runSaveToDisk(Path dir, List<String> saved) throws Exception {
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, worksheet());
      TableVSAssembly tableV = new TableVSAssembly(vs, "TableV");
      tableV.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "T1"));
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(new ColumnRef(new AttributeRef(null, "c1")));
      tableV.setColumnSelection(cols);
      vs.addAssembly(tableV);
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "test/TabularScheduledExportFailureTest", null, orgId);
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);

      IdentityID pid = new IdentityID("admin", orgId);
      Principal user = new SRPrincipal(pid);
      Principal oldPrincipal = ThreadContext.getContextPrincipal();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("rvs-78494");
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getOriginalBookmark(any(), any())).thenReturn(vs);
      when(rvs.getVariableTable()).thenReturn(box.getVariableTable());

      ViewsheetAction action = new ViewsheetAction(entry.toIdentifier(), null) {
         @Override
         public RuntimeViewsheet getRuntimeViewsheet(Principal user,
                                                     ScheduleViewsheetService service)
         {
            return rvs;
         }

         @Override
         public void closeViewsheet(String id, Principal user,
                                    ScheduleViewsheetService service)
         {
         }
      };
      // as ScheduleTask.copyScheduleTask sets it
      action.getViewsheetRequest().setParameter("__is_scheduler__", true);
      action.setBookmarks(new String[] { VSBookmark.HOME_BOOKMARK });
      action.setBookmarkTypes(new int[] { 0 });
      action.setBookmarkUsers(new IdentityID[] { pid });
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_CSV, dir.resolve("out78494").toString());

      VSBookmarkInfo home = new VSBookmarkInfo(VSBookmark.HOME_BOOKMARK, 0, pid, false, 0L);
      ExternalStorageService storage = mock(ExternalStorageService.class);
      doAnswer(inv -> saved.add(inv.getArgument(0)))
         .when(storage).write(any(), any(Path.class), any());

      try(MockedStatic<VSUtil> vsUtil = mockStatic(VSUtil.class, CALLS_REAL_METHODS);
          MockedStatic<ScheduleViewsheetService> service =
             mockStatic(ScheduleViewsheetService.class);
          MockedStatic<ExternalStorageService> external =
             mockStatic(ExternalStorageService.class))
      {
         vsUtil.when(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()))
            .thenReturn(new VSBookmarkInfo[] { home });
         service.when(ScheduleViewsheetService::getInstance)
            .thenReturn(mock(ScheduleViewsheetService.class));
         external.when(ExternalStorageService::getInstance).thenReturn(storage);
         action.run(user);
         return null;
      }
      catch(Throwable ex) {
         return ex;
      }
      finally {
         ThreadContext.setContextPrincipal(oldPrincipal);
      }
   }

   // fails the way the connectors do: catch the error, handleError with a null fallback
   public static class TestRuntime extends TabularRuntime {
      @Override
      public XTableNode runQuery(TabularQuery query, VariableTable params) {
         RUNS.incrementAndGet();

         try {
            if(failing) {
               throw new IOException(MESSAGE);
            }
         }
         catch(Exception ex) {
            handleError(params, ex, () -> null);
         }

         return null;
      }

      @Override
      public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
      }
   }

   static final class TestDataSource extends TabularDataSource<TestDataSource> {
      TestDataSource() {
         super("Test78494", TestDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
   }

   static final class TestQuery extends TabularQuery {
      TestQuery() {
         super("Test78494");
      }
   }
}
