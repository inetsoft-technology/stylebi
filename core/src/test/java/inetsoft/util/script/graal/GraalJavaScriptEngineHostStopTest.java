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
package inetsoft.util.script.graal;

import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77949: Java code that a script calls, e.g. a read of a formula cell, may fail because
 * a script it ran was stopped by its timeout. The calling script's exec then fails as stopped
 * too, not as an ordinary script error, so a lens does not keep the stop as an error value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraalJavaScriptEngineHostStopTest {
   @BeforeEach
   void setUp() throws Exception {
      previousTimeout = ScriptStopTestSupport.setTimeout("1");
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      host = new Host(engine);
      engine.put("host", host);
   }

   @AfterEach
   void tearDown() throws Exception {
      Thread.interrupted();
      engine.close();
      ScriptStopTestSupport.setTimeout(previousTimeout);
   }

   /**
    * A host call that ran a script the timeout stopped and lets its failure through.
    */
   @Test
   void timedOutScriptOfAHostCallStopsTheCaller() throws Exception {
      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(engine.compile("host.timedOut() + 1"), null, null));
      assertTrue(host.inner.isStopped(), "the host call's script was stopped");
      assertTrue(ex.isStopped(), "the caller is stopped too: " + ex.getMessage());
   }

   /**
    * A host call that fails with a stopped script exception, with no interrupt pending on the
    * calling script.
    */
   @Test
   void stoppedHostCallStopsTheCaller() throws Exception {
      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(engine.compile("host.stopped() + 1"), null, null));
      assertTrue(ex.isStopped(), "the caller is stopped too: " + ex.getMessage());
      assertTrue(ScriptTimeoutGuard.isStop(ex));
   }

   /**
    * Unchanged: an ordinary failure of a host call, or a script error of the script it ran, is
    * an ordinary script error of the caller.
    */
   @Test
   void otherHostFailureIsStillNotAStop() throws Exception {
      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(engine.compile("host.failed() + 1"), null, null));
      assertFalse(ex.isStopped());
      assertFalse(ScriptTimeoutGuard.isStop(ex));

      ex = assertThrows(ScriptException.class,
         () -> engine.exec(engine.compile("host.scriptError() + 1"), null, null));
      assertFalse(ex.isStopped());
      assertFalse(ScriptTimeoutGuard.isStop(ex));
   }

   /**
    * A host object whose methods fail in each way.
    */
   public static final class Host {
      Host(GraalJavaScriptEngine engine) {
         this.engine = engine;
      }

      public Object timedOut() throws Exception {
         try {
            return engine.exec(engine.compile(ScriptStopTestSupport.LOOP), null, null);
         }
         catch(ScriptException ex) {
            inner = ex;
            throw ex;
         }
      }

      public Object stopped() {
         throw ScriptStopTestSupport.stopped();
      }

      public Object failed() {
         throw new IllegalStateException("read failed");
      }

      public Object scriptError() throws Exception {
         return engine.exec(engine.compile("throw new Error('boom')"), null, null);
      }

      private final GraalJavaScriptEngine engine;
      private volatile ScriptException inner;
   }

   private GraalJavaScriptEngine engine;
   private Host host;
   private String previousTimeout;
}
