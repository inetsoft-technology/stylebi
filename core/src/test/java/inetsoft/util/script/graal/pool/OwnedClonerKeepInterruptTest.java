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

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123: keepInterrupt in owned-cloner.js tells the thread's interrupt from an error
 * that only means a value could not be read. Graal clears the interrupt flag when it raises
 * the interrupt, so an interrupt that keepInterrupt swallows is a lost cancel. Its own reads
 * can be interrupted too, and a fallback read that fails otherwise must not throw out of the
 * catch that called it.
 *
 * <p>An interrupt cannot be made to land on one particular read, so the functions are taken
 * from the cloner's source and run over a descriptor read that throws on cue.
 */
@Tag("core")
class OwnedClonerKeepInterruptTest {
   private static final String INTERRUPTED = "Thread was interrupted.";
   private static Context context;

   @BeforeAll
   static void load() throws Exception {
      String js;

      try(InputStream in = WsEngine.class.getResourceAsStream("owned-cloner.js")) {
         assertNotNull(in, "owned-cloner.js");
         js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      String keep = function(js, "keepInterrupt");
      assertNotNull(keep, "keepInterrupt in owned-cloner.js");
      String rethrow = function(js, "rethrowInterrupt");

      context = Context.create("js");
      context.eval("js", """
         var STOP = { stop: true };
         var cue = { on: null, error: null };
         // the descriptor read: throws cue.error for the object cue.on, else Reflect's
         var gOPD = function(o, k) {
            if(o === cue.on) { throw cue.error; }
            return Reflect.getOwnPropertyDescriptor(o, k);
         };
         var INTERRUPTED = '%s';
         var keepInterrupt = (function() { %s %s return keepInterrupt; })();
         // runs keepInterrupt(e): 'returned', or 'threw:' + what it threw
         function check(e) {
            try { keepInterrupt(e); return 'returned'; }
            catch(t) {
               return 'threw:' + (t === e ? 'e' : t === cue.error ? 'cue' : String(t && t.message));
            }
         }
         function interrupt() { return new Error(INTERRUPTED); }
         """.formatted(INTERRUPTED, keep, rethrow == null ? "" : rethrow));
   }

   @AfterAll
   static void close() {
      if(context != null) {
         context.close();
      }
   }

   private static String check(String setup) {
      return context.eval("js", "(function() { cue.on = null; cue.error = null; " + setup +
         " })()").asString();
   }

   @Test
   void anInterruptOfTheDescriptorReadIsRethrown() {
      // the read of an error that is no interrupt is itself interrupted
      assertEquals("threw:cue", check(
         "var e = new Error('no value'); cue.on = e; cue.error = interrupt(); return check(e);"));
   }

   @Test
   void aFailedFallbackReadLeavesTheErrorAValueThatCouldNotBeRead() {
      // the descriptor read refuses e, and so does the read of its message
      assertEquals("returned", check(
         "var e = { get message() { throw new Error('unreadable'); } };" +
         "cue.on = e; cue.error = new TypeError('refused'); return check(e);"));
   }

   @Test
   void anInterruptOfTheFallbackReadIsRethrown() {
      assertEquals("threw:interrupted", check(
         "var e = { get message() { throw interrupt(); } };" +
         "cue.on = e; cue.error = new TypeError('refused'); return check(e);")
         .replace(INTERRUPTED, "interrupted"));
   }

   @Test
   void theInterruptItselfIsRethrownByEitherRead() {
      // an error the descriptor read refuses, told by its message
      assertEquals("threw:e", check(
         "var e = { message: INTERRUPTED }; cue.on = e; cue.error = new TypeError('refused');" +
         "return check(e);"));
      // a script error with the interrupt's message
      assertEquals("threw:e", check("return check(interrupt());"));
   }

   @Test
   void anythingElseIsNoInterrupt() {
      assertEquals("returned", check("return check(new Error('no value'));"));
      assertEquals("returned", check("return check(STOP);"));
      assertEquals("returned", check("return check('x');"));
      assertEquals("returned", check("return check(null);"));
      // the descriptor read refuses e, whose message is something else
      assertEquals("returned", check(
         "var e = { message: 'no value' }; cue.on = e; cue.error = new TypeError('refused');" +
         "return check(e);"));
   }

   /** The source of {@code function name(...) { ... }} in js, or null, by brace matching. */
   private static String function(String js, String name) {
      int start = js.indexOf("function " + name + "(");

      if(start < 0) {
         return null;
      }

      int depth = 0;

      for(int i = js.indexOf('{', start); i < js.length(); i++) {
         char c = js.charAt(i);

         if(c == '{') {
            depth++;
         }
         else if(c == '}' && --depth == 0) {
            return js.substring(start, i + 1);
         }
      }

      return null;
   }
}
