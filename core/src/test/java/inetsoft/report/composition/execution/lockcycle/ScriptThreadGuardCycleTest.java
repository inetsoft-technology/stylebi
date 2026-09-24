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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.test.*;
import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.KNOWN_CAP;

/**
 * Guards that exempt only {@code isScriptThread()} from the viewsheet sandbox lock S (bug
 * #76960 track C). A condition-filter lock holder holds the engine lock E outside
 * {@code exec}, so it is not a script thread and blocks on S. Cycle: W holds S for write and
 * waits for E; H holds E (as {@code ConditionFilter2.moreRows} does) and waits for S for read.
 *
 * <p>Track C found no production path that takes S under a condition filter, so this is
 * hardening, not a live bug. The lock is the real {@link UpgradableReadWriteLock} with the
 * predicate {@code ViewsheetSandbox} passes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@DisabledIfSystemProperty(named = "lockcycle.pool", matches = "true",
   disabledReason = "pool-off only: H holds the raw engine lock E of a plain GraalJavaScriptEnv " +
      "outside exec; PoolModeCycleTest.scriptHolderVsSandboxWriter is the pool-on equivalent")
public class ScriptThreadGuardCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void filterLockHolderVsSandboxWriter() throws Exception {
      UpgradableReadWriteLock s = new UpgradableReadWriteLock(JavaScriptEngine::isScriptThread);
      Sandbox sandbox = harness.sandbox();
      CountDownLatch holderHasE = new CountDownLatch(1);
      CountDownLatch writerHasS = new CountDownLatch(1);

      Future<Void> holder = harness.submit(() -> {
         sandbox.lock.lock();
         JavaScriptEngine.pushHeldScriptLock(sandbox.lock);

         try {
            holderHasE.countDown();
            writerHasS.await();
            s.lockRead();
            s.unlockRead();
            return null;
         }
         finally {
            JavaScriptEngine.popHeldScriptLock();
            sandbox.lock.unlock();
         }
      });
      Future<Void> writer = harness.submit(() -> {
         holderHasE.await();
         s.lockWrite();

         try {
            writerHasS.countDown();
            sandbox.lock.lock();
            sandbox.lock.unlock();
            return null;
         }
         finally {
            s.unlockWrite();
         }
      });

      harness.await(holder, KNOWN_CAP, "H, the filter lock holder reading S");
      harness.await(writer, KNOWN_CAP, "W, the S writer");
   }

   private LockCycleHarness harness;
}
