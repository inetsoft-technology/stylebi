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
package inetsoft.uql.util;

/*
 * Bug #79822: XUtil.runQuery logged every failure at ERROR with a stack, including the expected
 * permission denial. A denial must log one WARN line without a throwable (stack at DEBUG); any
 * other failure, including a non-denial ScriptException from the worksheet processor, must keep
 * ERROR with the stack. Reuses the harness of RunQueryUserScopeWorksheetTest.
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  RunQueryDenialLoggingTest.HookConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RunQueryDenialLoggingTest {
   private static final String ORG = "org79822_id";
   private static final String NO_READ = "noRead79822";
   private static final String GLOBAL_WS = "ws:global:globalWs79822";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal noRead;
   private static final AtomicReference<Boolean> ALLOW = new AtomicReference<>(false);
   private static final AtomicReference<Supplier<RuntimeException>> FAILURE =
      new AtomicReference<>();

   private MockedStatic<VpmProcessor> vpmProcessor;
   private MockedConstruction<ReportWorksheetProcessor> processors;
   private String oldProvider;
   private Logger xutilLogger;
   private Level oldLevel;
   private ListAppender<ILoggingEvent> appender;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("org79822", ORG)
         .addUser(NO_READ, ORG, "password")
         .setup();
      noRead = builder.principalOf(NO_READ, ORG);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null);
      oldProvider = SreeEnv.getProperty("security.provider");
      SreeEnv.setProperty("security.provider", "file");
      ALLOW.set(false);
      FAILURE.set(null);

      vpmProcessor = Mockito.mockStatic(VpmProcessor.class, Mockito.CALLS_REAL_METHODS);
      vpmProcessor.when(VpmProcessor::useVpmSecurity).thenReturn(false);

      processors = Mockito.mockConstruction(ReportWorksheetProcessor.class, (mock, ctx) ->
         when(mock.execute(any(AssetEntry.class), any(VariableTable.class), any()))
            .thenAnswer(inv -> {
               throw FAILURE.get().get();
            }));

      xutilLogger = (Logger) LoggerFactory.getLogger(XUtil.class);
      oldLevel = xutilLogger.getLevel();
      xutilLogger.setLevel(Level.DEBUG);
      appender = new ListAppender<>();
      appender.start();
      xutilLogger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      xutilLogger.detachAppender(appender);
      xutilLogger.setLevel(oldLevel);
      ThreadContext.setContextPrincipal(null);
      ALLOW.set(false);
      SreeEnv.setProperty("security.provider", oldProvider == null ? "" : oldProvider);

      for(AutoCloseable c : new AutoCloseable[] { processors, vpmProcessor }) {
         try {
            if(c != null) {
               c.close();
            }
         }
         catch(Exception ignore) {
         }
      }
   }

   @Test
   void permissionDenial_warnWithoutThrowable_stackAtDebug() {
      assertNull(XUtil.runQuery(GLOBAL_WS, null, noRead, null));

      List<ILoggingEvent> failed = failedEvents();
      ILoggingEvent warn = only(failed, Level.WARN);
      assertNull(warn.getThrowableProxy(), "denial WARN must not carry a stack");
      assertTrue(warn.getFormattedMessage().contains(GLOBAL_WS));
      assertEquals(0, count(failed, Level.ERROR), "a denial must not log at ERROR");
      assertNotNull(only(failed, Level.DEBUG).getThrowableProxy(),
                    "the stack is kept at DEBUG");
   }

   @Test
   void runtimeException_staysErrorWithThrowable() {
      ALLOW.set(true);
      FAILURE.set(() -> new RuntimeException("db down"));

      assertNull(XUtil.runQuery(GLOBAL_WS, null, noRead, null));

      List<ILoggingEvent> failed = failedEvents();
      assertNotNull(only(failed, Level.ERROR).getThrowableProxy());
      assertEquals(0, count(failed, Level.WARN));
   }

   @Test
   void nonDenialScriptException_staysErrorWithThrowable() {
      ALLOW.set(true);
      FAILURE.set(() -> new ScriptException("worksheet script failed"));

      assertNull(XUtil.runQuery(GLOBAL_WS, null, noRead, null));

      List<ILoggingEvent> failed = failedEvents();
      assertNotNull(only(failed, Level.ERROR).getThrowableProxy());
      assertEquals(0, count(failed, Level.WARN));
   }

   private List<ILoggingEvent> failedEvents() {
      return appender.list.stream()
         .filter(e -> e.getFormattedMessage().startsWith("Failed to run query"))
         .collect(Collectors.toList());
   }

   private static long count(List<ILoggingEvent> events, Level level) {
      return events.stream().filter(e -> e.getLevel() == level).count();
   }

   private static ILoggingEvent only(List<ILoggingEvent> events, Level level) {
      List<ILoggingEvent> list = events.stream().filter(e -> e.getLevel() == level)
         .collect(Collectors.toList());
      assertEquals(1, list.size(), "expected exactly one " + level + " in " + events);
      return list.get(0);
   }

   /** SecurityEngine spy whose ASSET READ check follows ALLOW. */
   @Configuration
   static class HookConfig {
      @Bean
      @DependsOn("propertiesEngine")
      public SecurityEngine securityEngine(Cluster cluster, LicenseManager licenseManager)
         throws Exception
      {
         SecurityEngine engine = spy(new SecurityEngine(licenseManager, cluster));
         doAnswer(inv -> ALLOW.get()).when(engine).checkPermission(
            any(Principal.class), eq(ResourceType.ASSET), anyString(), eq(ResourceAction.READ));
         return engine;
      }
   }
}
