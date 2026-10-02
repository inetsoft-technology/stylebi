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
   }

   /**
    * Feature #77123: the pool is on unless script.ws.contextPool is set to something other
    * than true; false (any case) still turns it off.
    */
   @Test
   public void poolIsOnByDefaultAndFalseTurnsItOff() throws Exception {
      SreeEnv.remove(PoolConfig.ENABLED);
      assertTrue(PoolConfig.DEFAULT_ENABLED);
      assertTrue(PoolConfig.isEnabled());
      AssetQuerySandbox unset = new AssetQuerySandbox(null);
      assertTrue(unset.isScriptPoolMode());
      assertTrue(unset.getScriptEnv() instanceof WorksheetScriptEnv);

      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      assertFalse(PoolConfig.isEnabled());
      AssetQuerySandbox off = new AssetQuerySandbox(null);
      assertFalse(off.isScriptPoolMode());
      off.getScriptEnv().compile("1");
      assertFalse(off.getScriptEnv() instanceof WorksheetScriptEnv);
      assertNotNull(off.getScriptEnv().getExecutionLock());

      SreeEnv.setProperty(PoolConfig.ENABLED, "FALSE");
      assertFalse(PoolConfig.isEnabled());
      SreeEnv.setProperty(PoolConfig.ENABLED, "TRUE");
      assertTrue(PoolConfig.isEnabled());
      SreeEnv.setProperty(PoolConfig.ENABLED, " ");
      assertTrue(PoolConfig.isEnabled());
   }

   /**
    * Feature #77123 review m1: a value that is neither true nor false turns the pool off, and
    * says so in one WARN per distinct value; true, false and blank log nothing.
    */
   @Test
   public void unrecognizedValueTurnsPoolOffWithOneWarnPerValue() {
      Logger logger = (Logger) LoggerFactory.getLogger(PoolConfig.class);
      Level oldLevel = logger.getLevel();
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      logger.setLevel(Level.WARN);

      try {
         // unique values: the once-per-value memory is JVM-wide
         String typo = "ture" + System.nanoTime();
         String other = "yes" + System.nanoTime();

         SreeEnv.setProperty(PoolConfig.ENABLED, " " + typo + " ");
         assertFalse(PoolConfig.isEnabled());
         assertFalse(PoolConfig.isEnabled());
         assertEquals(1, warns(appender, typo));

         SreeEnv.setProperty(PoolConfig.ENABLED, other);
         assertFalse(PoolConfig.isEnabled());
         assertEquals(1, warns(appender, other));

         SreeEnv.setProperty(PoolConfig.ENABLED, typo);
         assertFalse(PoolConfig.isEnabled());
         assertEquals(1, warns(appender, typo));

         int before = appender.list.size();

         for(String ok : new String[] { "false", "FALSE", "true", " True ", " " }) {
            SreeEnv.setProperty(PoolConfig.ENABLED, ok);
            PoolConfig.isEnabled();
         }

         SreeEnv.remove(PoolConfig.ENABLED);
         assertTrue(PoolConfig.isEnabled());
         assertEquals(before, appender.list.size());

         ILoggingEvent event = appender.list.get(0);
         assertEquals(Level.WARN, event.getLevel());
         assertTrue(event.getFormattedMessage().contains(PoolConfig.ENABLED));
         assertTrue(event.getFormattedMessage().contains("OFF"));
      }
      finally {
         logger.detachAppender(appender);
         logger.setLevel(oldLevel);
      }
   }

   private static long warns(ListAppender<ILoggingEvent> appender, String value) {
      return appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("'" + value + "'"))
         .count();
   }

   @Test
   public void poolModeIsReadOncePerSandbox() throws Exception {
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      AssetQuerySandbox before = new AssetQuerySandbox(null);
      SreeEnv.setProperty(PoolConfig.ENABLED, "true");
      AssetQuerySandbox after = new AssetQuerySandbox(null);

      assertFalse(before.isScriptPoolMode());
      assertTrue(after.isScriptPoolMode());

      ScriptEnv plain = before.getScriptEnv();
      plain.compile("1");
      assertFalse(plain instanceof WorksheetScriptEnv);
      assertNotNull(plain.getExecutionLock());
      assertTrue(after.getScriptEnv() instanceof WorksheetScriptEnv);
      assertNull(after.getScriptEnv().getExecutionLock());

      // flip the property, then null and recreate each sandbox's env: the types stay (N3)
      SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      after.reset();
      assertTrue(after.getScriptEnv() instanceof WorksheetScriptEnv);
      after.dispose();
      assertTrue(after.getScriptEnv() instanceof WorksheetScriptEnv);

      SreeEnv.setProperty(PoolConfig.ENABLED, "true");
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
