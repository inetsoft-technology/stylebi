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
import inetsoft.util.swap.LostSwapFile;
import inetsoft.util.swap.SwapFileReadException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static inetsoft.util.swap.SwapLostTestSupport.assertSwapOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77910: a lost swap file hit by host code the script calls, e.g. a read of a table,
 * reaches the caller of exec as the same SwapFileReadException, not as a ScriptException
 * without a cause, and is not counted as a script error. Other host failures are unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GraalJavaScriptEngineSwapLostTest {
   @BeforeEach
   void setUp() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      lost = new LostSwapFile();
   }

   @AfterEach
   void tearDown() {
      lost.close();
      engine.close();
   }

   @Test
   void hostLostSwapFileReachesTheCallerAsItIs() throws Exception {
      Host host = new Host(lost, false);
      engine.put("host", host);
      Object src = engine.compile("host.value()");

      // twice: the second run must not be cut short by an error count either
      for(int i = 0; i < 2; i++) {
         SwapFileReadException ex = assertThrows(SwapFileReadException.class,
            () -> engine.exec(src, null, null));
         assertSwapOf(host.swap, ex);
         assertEquals(lost.getFile(), ex.getFile());
      }

      assertFalse(errorCounts().containsKey(src), "a lost swap file is not a script error");
   }

   @Test
   void wrappedLostSwapFileReachesTheCallerAsTheSwapFailure() throws Exception {
      Host host = new Host(lost, true);
      engine.put("host", host);
      Object src = engine.compile("host.value()");

      SwapFileReadException ex = assertThrows(SwapFileReadException.class,
         () -> engine.exec(src, null, null));
      assertSwapOf(host.swap, ex);
      assertFalse(errorCounts().containsKey(src), "a lost swap file is not a script error");
   }

   @Test
   void otherHostFailureIsStillAScriptError() throws Exception {
      engine.put("host", new FailingHost());
      Object src = engine.compile("host.value()");

      ScriptException ex = assertThrows(ScriptException.class,
         () -> engine.exec(src, null, null));
      assertTrue(ex.getMessage().contains("read failed"), ex.getMessage());
      assertNull(SwapFileReadException.find(ex));
      assertEquals(1, errorCounts().get(src));
   }

   private Map<?, ?> errorCounts() throws Exception {
      Field errorCountsField = GraalJavaScriptEngine.class.getDeclaredField("errorCounts");
      errorCountsField.setAccessible(true);
      return (Map<?, ?>) errorCountsField.get(engine);
   }

   /**
    * A host object whose method reads the lost swap file, and optionally wraps the failure.
    */
   public static final class Host {
      Host(LostSwapFile lost, boolean wrap) {
         this.lost = lost;
         this.wrap = wrap;
      }

      public Object value() {
         try {
            lost.read();
         }
         catch(SwapFileReadException ex) {
            swap = ex;

            if(wrap) {
               throw new IllegalStateException("table build failed", ex);
            }

            throw ex;
         }

         return null;
      }

      private final LostSwapFile lost;
      private final boolean wrap;
      private volatile SwapFileReadException swap;
   }

   /**
    * A host object whose method fails with an ordinary failure.
    */
   public static final class FailingHost {
      public Object value() {
         throw new IllegalStateException("read failed");
      }
   }

   private GraalJavaScriptEngine engine;
   private LostSwapFile lost;
}
