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

import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The invariants of the live Java-argument views (bug #77123, refute amendments 1-4): a view
 * is live only while its own frame is open; the end-of-exec re-snapshot runs only on normal
 * completion and every view is dropped on any exit; a view made while its frame detaches is
 * a plain copy; a frame holds one view per guest value.
 */
@Tag("core")
class WsLiveViewInvariantTest {
   /**
    * Host helpers the scripts call.
    */
   public static final class Helper {
      public int size(Object value) {
         args.add(value);
         return ((List<?>) value).size();
      }

      public double sumV(Object value) {
         List<?> list = (List<?>) value;
         double sum = 0;

         for(int i = 0; i < list.size(); i++) {
            // the same element twice: still one view
            list.get(i);
            sum += ((Number) ((Map<?, ?>) list.get(i)).get("v")).doubleValue();
         }

         return sum;
      }

      public Object keep(Object value) {
         args.add(value);
         return value;
      }

      public void tick() {
         ticks++;
      }

      public int frameViews() {
         return WsExecContext.currentFrame().size();
      }

      final List<Object> args = new ArrayList<>();
      int ticks;
   }

   @Test
   void aFrameHoldsOneViewPerGuestValue() throws Exception {
      WorksheetScriptEnv env = withHelper();
      assertEquals(52.0, run(env,
         "var a = []; for (var i = 0; i < 50; i++) a.push({v: i}); var b = [1, 2, 3]; " +
         "for (var k = 0; k < 200; k++) { h.sumV(a); h.size(b); h.keep(a); } h.frameViews()"),
         "1 view of a, 50 of its elements, 1 of b");
      assertSame(helper.args.get(0), helper.args.get(1 + 1), "a re-pass reuses the view");
   }

   @Test
   void aViewIsACopyOnceItsExecEnded() throws Exception {
      WorksheetScriptEnv env = withHelper();
      run(env, "var a = [1, 2]; h.keep(a); var o = {x: [1]}; h.keep(o); h.keep(o.x); 1");

      for(Object arg : helper.args) {
         assertFalse(attached(arg), "a view still holds its guest value: " + arg);
      }

      assertNull(WsExecContext.currentFrame(), "the frame was popped");
      assertNull(WsExecContext.currentContext(), "the exec mark was restored");
   }

   /**
    * Amendment 2 (the refuter's leak case): a getter that runs during the end-of-exec
    * re-snapshot and passes an array to Java gets a plain copy, never a view bound to a
    * closed or foreign frame.
    */
   @Test
   void aViewMadeWhileItsFrameDetachesIsAPlainCopy() throws Exception {
      WorksheetScriptEnv env = withHelper();
      run(env, "var b = [5]; var a = [1, 2]; h.size(a); " +
         "Object.defineProperty(a, 0, {get: function() { h.keep(b); return 1; }, " +
         "enumerable: true}); 1");
      assertEquals(2, helper.args.size(), "the getter ran at the re-snapshot");
      assertFalse(attached(helper.args.get(0)));
      assertTrue(helper.args.get(1) instanceof CopyList, String.valueOf(helper.args.get(1)));
      assertEquals("[1, 2]", String.valueOf(helper.args.get(0)), "the final state, via the getter");
   }

   /**
    * Amendment 1: an exec that ends by an exception runs no script code at exit; every view
    * keeps its call-time copy and is dropped, and the thread's mark and frame are restored.
    */
   @Test
   void anExecThatFailsRunsNoScriptAtExitAndKeepsTheCallTimeCopies() {
      WorksheetScriptEnv env = withHelper();
      assertThrows(ScriptException.class, () -> run(env,
         "var a = [1, 2]; h.keep(a); a.push(3); " +
         "Object.defineProperty(a, 0, {get: function() { h.tick(); return 9; }}); " +
         "throw new Error('boom')"));
      assertEquals(0, helper.ticks, "script code ran after the exec failed");
      assertEquals("[1, 2]", String.valueOf(helper.args.get(0)));
      assertFalse(attached(helper.args.get(0)));
      assertNull(WsExecContext.currentFrame());
      assertNull(WsExecContext.currentContext());
   }

   /**
    * Amendment 3: a re-snapshot that throws keeps that view's call-time copy and does not stop
    * the other views; the exec still returns its result.
    */
   @Test
   void aFailingResnapshotKeepsItsCopyAndTheOthersStillUpdate() throws Exception {
      WorksheetScriptEnv env = withHelper();
      assertEquals(1.0, run(env,
         "var a = [1]; var b = [2]; var o = {a: 1}; h.keep(a); h.keep(b); h.keep(o); " +
         "Object.defineProperty(a, 0, {get: function() { throw new Error('x'); }}); " +
         "b.push(3); o.f = function() {}; 1"));
      assertEquals("[1]", String.valueOf(helper.args.get(0)), "call-time copy kept");
      assertEquals("[2, 3]", String.valueOf(helper.args.get(1)), "final state");
      assertEquals("{a=1}", String.valueOf(helper.args.get(2)),
                   "a function added after the call is never copied");

      for(Object arg : helper.args) {
         assertFalse(attached(arg));
      }

      assertNull(WsExecContext.currentFrame());
      assertNull(WsExecContext.currentContext());
   }

   /**
    * A nested exec of the same env shares the outer frame; the frame closes with the
    * outermost exec only.
    */
   @Test
   void aNestedExecOfTheSameContextSharesTheFrame() throws Exception {
      WorksheetScriptEnv env = withHelper();
      Callback cb = new Callback(env);
      env.put("cb", cb);
      assertEquals("1|2|true", run(env,
         "var a = [1]; h.keep(a); var n = cb.nested('h.keep(a); a.push(2); h.frameViews()'); " +
         "n + '|' + a.length + '|' + (h.frameViews() == 1)"));
      assertSame(helper.args.get(0), helper.args.get(1));
      assertEquals("[1, 2]", String.valueOf(helper.args.get(0)));
      assertFalse(attached(helper.args.get(0)));
      assertNull(WsExecContext.currentFrame());
   }

   private static boolean attached(Object view) {
      if(view instanceof LiveList list) {
         return list.attached();
      }

      if(view instanceof LiveMap map) {
         return map.attached();
      }

      return false;
   }

   private WorksheetScriptEnv withHelper() {
      WorksheetScriptEnv env = env();
      env.put("h", helper);
      return env;
   }

   private final Helper helper = new Helper();
}
