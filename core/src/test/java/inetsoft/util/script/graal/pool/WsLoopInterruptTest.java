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
package inetsoft.util.script.graal.pool;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (reliability review of the clean fuzz, M1): the timeout interrupt of a looping
 * exec never lands on the next exec of the same pooled env. The next exec runs at once on the
 * cleaned context, is not interrupted, and does not see the looping script's global; a new
 * context is created only when an interrupt could not stop its exec (which dooms the slot).
 *
 * <p>Each round waits out a 1 s timeout: 10 rounds by default, 50 under {@code -Drel.long=true}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class WsLoopInterruptTest {
   @BeforeEach
   void shortTimeout() throws Exception {
      previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refresh();
   }

   @AfterEach
   void restore() throws Exception {
      SreeEnv.setProperty("script.execution.timeout", previous);
      refresh();
   }

   @Test
   void loopInterruptNeverHitsTheNextExec() throws Exception {
      WorksheetScriptEnv env = env();
      run(env, "1");
      long creations = env.getMetrics().getCreations();
      long timeouts = PoolMetrics.nodeInterruptTimeouts();

      for(int i = 0; i < ROUNDS; i++) {
         long start = System.nanoTime();
         ScriptException ex =
            assertThrows(ScriptException.class, () -> run(env, "zq = 1; while(true) {}"));
         long looped = (System.nanoTime() - start) / 1_000_000L;
         assertTrue(looped >= 900, "the loop ran its timeout, " + looped + " ms");
         // only the timeout's interrupt (Graal words it either way), not another failure
         assertTrue(INTERRUPTED.matcher(String.valueOf(ex.getMessage())).matches(),
                    "not the timeout interrupt: " + ex.getMessage());

         // right after: never interrupted, on a context without the loop's global
         assertEquals(1.0, run(env, "typeof zq === 'undefined' ? 1 : 0"), "round " + i);
      }

      long closed = env.getMetrics().getCreations() - creations;
      long timedOut = PoolMetrics.nodeInterruptTimeouts() - timeouts;
      assertTrue(closed <= timedOut,
                 "contexts replaced " + closed + ", interrupts that timed out " + timedOut);
   }

   private static void refresh() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private static final Pattern INTERRUPTED =
      Pattern.compile("(Thread was interrupted|Execution got interrupted)\\. \\(line 1\\)");
   private static final int ROUNDS = Boolean.getBoolean("rel.long") ? 50 : 10;
   private String previous;
}
