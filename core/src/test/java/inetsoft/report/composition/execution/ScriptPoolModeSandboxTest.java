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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.PoolOffConditionFilterLockingTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec gate G8 (bug #76960): the context pool mode is fixed per sandbox when it is built,
 * every env the sandbox creates has the matching type, a condition filter's locking follows
 * its sandbox's mode, and a scriptless query never creates an env.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ScriptPoolModeSandboxTest {
   @AfterEach
   public void tearDown() {
      SreeEnv.remove(PoolConfig.ENABLED);
      PoolConfig.setPoolOffForTests(false);
   }

   /**
    * Feature #78459: the pool is always on; script.ws.contextPool no longer turns it off,
    * whatever its value.
    */
   @ParameterizedTest
   @ValueSource(strings = { "false", "FALSE", " false ", "true", "ture", " " })
   public void propertyNoLongerTurnsThePoolOff(String value) throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, value);
      assertTrue(PoolConfig.isPoolMode());
      AssetQuerySandbox box = new AssetQuerySandbox(null);
      assertTrue(box.isScriptPoolMode());
      assertTrue(box.getScriptEnv() instanceof WorksheetScriptEnv);
   }

   /**
    * Feature #78459: a leftover value is logged once per JVM as a WARN saying it is ignored;
    * an unset property logs nothing.
    */
   @Test
   public void leftoverPropertyIsWarnedOnce() {
      Logger logger = (Logger) LoggerFactory.getLogger(PoolConfig.class);
      Level oldLevel = logger.getLevel();
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      logger.setLevel(Level.WARN);

      try {
         PoolConfig.resetSwitchWarningForTests();
         SreeEnv.remove(PoolConfig.ENABLED);
         new AssetQuerySandbox(null);
         assertEquals(0, appender.list.size());

         SreeEnv.setProperty(PoolConfig.ENABLED, "false");
         new AssetQuerySandbox(null);
         new AssetQuerySandbox(null);
         assertEquals(1, appender.list.size());
         ILoggingEvent event = appender.list.get(0);
         assertEquals(Level.WARN, event.getLevel());
         assertTrue(event.getFormattedMessage().contains(PoolConfig.ENABLED));
         assertTrue(event.getFormattedMessage().contains("no longer supported"));
      }
      finally {
         logger.detachAppender(appender);
         logger.setLevel(oldLevel);
      }
   }

   /**
    * The pool mode is fixed per sandbox when it is built; the test seam turns it off for the
    * pool-off code paths that remain until they are removed.
    */
   @Test
   public void poolModeIsReadOncePerSandbox() throws Exception {
      PoolConfig.setPoolOffForTests(true);
      AssetQuerySandbox before = new AssetQuerySandbox(null);
      PoolConfig.setPoolOffForTests(false);
      AssetQuerySandbox after = new AssetQuerySandbox(null);

      assertFalse(before.isScriptPoolMode());
      assertTrue(after.isScriptPoolMode());

      ScriptEnv plain = before.getScriptEnv();
      plain.compile("1");
      assertFalse(plain instanceof WorksheetScriptEnv);
      assertNotNull(plain.getExecutionLock());
      assertTrue(after.getScriptEnv() instanceof WorksheetScriptEnv);
      assertNull(after.getScriptEnv().getExecutionLock());

      // flip the seam, then null and recreate each sandbox's env: the types stay (N3)
      PoolConfig.setPoolOffForTests(true);
      after.reset();
      assertTrue(after.getScriptEnv() instanceof WorksheetScriptEnv);
      after.dispose();
      assertTrue(after.getScriptEnv() instanceof WorksheetScriptEnv);

      PoolConfig.setPoolOffForTests(false);
      before.reset();
      assertFalse(before.getScriptEnv() instanceof WorksheetScriptEnv);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void conditionFilterLockFollowsSandboxMode(boolean pool) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      ScriptEnv env = box.getScriptEnv();
      env.compile("1");
      ProbeTable probe = new ProbeTable(ROWS);
      TableLens formula = new FormulaTableLens(probe, new String[] {"f"}, new String[] {"1"},
                                               env, null);
      TableLens filter = PostProcessor.filter(formula, allRows(), box);

      assertFalse(filter.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, filter.getRowCount());
      assertEquals(!pool, probe.sawScriptLock);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void scriptlessQueryCreatesNoEnv(boolean pool) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      TableLens filter = PostProcessor.filter(new ProbeTable(ROWS), allRows(), box);

      assertFalse(filter.moreRows(Integer.MAX_VALUE));
      assertNull(box.peekScriptEnv());
   }
}
