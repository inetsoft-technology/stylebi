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
package inetsoft.uql.tabular;

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.impl.TabularHandler;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.DataCacheVisitor;
import inetsoft.util.ThreadContext;
import inetsoft.util.credential.CredentialType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78494: a scheduled run carries {@code __is_scheduler__} as {@code Boolean.TRUE} at export
 * time and as the String {@code "true"} at open time. A tabular connector's query error must fail
 * the scheduled run in both forms instead of becoming an empty (and cached) table, and must still
 * fall back to the connector's value in an interactive run. Runs the real {@link XSessionManager}
 * and {@link TabularHandler}; the fast check of {@code handleError} alone is
 * {@link TabularRuntimeHandleErrorTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  TabularRuntimeSchedulerFlagTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class TabularRuntimeSchedulerFlagTest {
   @Configuration
   static class TestConfig {
      @Bean
      Drivers drivers() {
         return mock(Drivers.class);
      }

      @Bean
      XRepository testDataService() {
         return mock(XRepository.class);
      }

      @Bean
      XSessionManager xSessionManager(XRepository testDataService, XSessionService sessionService)
         throws Exception
      {
         return new XSessionManager(testDataService, sessionService,
                                    mock(DataSourceRegistry.class));
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      queryRuns.set(0);
      manager.setCacheData(true);
      manager.clearCache();

      // the real handler, with a runtime in the connectors' catch shape for the test type
      TabularHandler handler = new TabularHandler();
      Field field = TabularHandler.class.getDeclaredField("runtimes");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, TabularRuntime> runtimes = (Map<String, TabularRuntime>) field.get(handler);
      runtimes.put(TYPE, new FailingRuntime());

      reset(dataService);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> handler.execute(inv.getArgument(1), inv.getArgument(2),
                                            inv.getArgument(3),
                                            (DataCacheVisitor) inv.getArgument(5)));
   }

   @AfterEach
   void tearDown() {
      manager.clearCache();
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   @Test
   void exportTimeBooleanFlagFailsTheQueryAndCachesNothing() {
      assertQueryFails(Boolean.TRUE);
      assertFailureNotCached();
   }

   @Test
   void openTimeStringFlagFailsTheQueryAndCachesNothing() {
      assertQueryFails("true");
      assertFailureNotCached();
   }

   @Test
   void interactiveRunStillReturnsTheConnectorValue() throws Exception {
      TableLens lens = manager.getXNodeTableLens(newQuery(), new VariableTable(), null);

      assertEquals(1, queryRuns.get());
      assertNotNull(lens);
      assertFalse(lens.moreRows(1), "the connector's null becomes an empty table, as before");
   }

   private void assertQueryFails(Object flag) {
      VariableTable vars = new VariableTable();
      vars.put("__is_scheduler__", flag);

      Exception ex = assertThrows(Exception.class,
         () -> manager.getXNodeTableLens(newQuery(), vars, null),
         "a scheduled run must fail on the connector error, flag " + flag + " (" +
         flag.getClass().getSimpleName() + ")");
      assertTrue(hasCause(ex, "connector failure"), "the connector error is the cause: " + ex);
      assertEquals(1, queryRuns.get());
   }

   // the next reader of the same query must run it again, not get a cached empty table from
   // the handler's query cache
   private void assertFailureNotCached() {
      assertDoesNotThrow(() -> manager.getXNodeTableLens(newQuery(), new VariableTable(), null));
      assertEquals(2, queryRuns.get(), "the failed scheduled result must not be cached");
   }

   private static boolean hasCause(Throwable ex, String message) {
      for(Throwable t = ex; t != null; t = t.getCause()) {
         if(message.equals(t.getMessage())) {
            return true;
         }
      }

      return false;
   }

   private static TestQuery newQuery() {
      TestDataSource ds = new TestDataSource();
      ds.setName("SchedulerFlagDS");
      TestQuery query = new TestQuery();
      query.setName("schedulerFlagQuery");
      query.setDataSource(ds);
      query.setOutputColumns(new XTypeNode[] { XSchema.createPrimitiveType("c1", String.class) });
      return query;
   }

   // the call shape of the connectors: catch(Exception) { handleError(params, ex, () -> null); }
   private static final class FailingRuntime extends TabularRuntime {
      @Override
      public XTableNode runQuery(TabularQuery query, VariableTable params) {
         queryRuns.incrementAndGet();

         try {
            throw new IOException("connector failure");
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

   private static final class TestDataSource extends TabularDataSource<TestDataSource> {
      TestDataSource() {
         super(TYPE, TestDataSource.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }
   }

   private static final class TestQuery extends TabularQuery {
      TestQuery() {
         super(TYPE);
      }
   }

   private static final String TYPE = "SchedulerFlagTest";
   private static final AtomicInteger queryRuns = new AtomicInteger();

   @Autowired
   private XDataService dataService;
   @Autowired
   private XSessionManager manager;
   private Principal savedPrincipal;
}
