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

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hand-off budget kinds (Testing #77123, cond-home round 3, post-merge review M1): a batch
 * end on a span that outlives the batch keeps a table's objects live on its context when a
 * value is lost to the budget ({@link OwnedValueCodec.Lost#overBudget}), which the codec tells
 * from the kind string owned-cloner.js reports (isBudgetKind). If a cloner string drifts from
 * the codec's prefixes, keep-on-budget silently turns back into a loss. Each hard stop of the
 * real cloner (the entry cap at its two sites, the time bound, the marking budget at its two
 * sites) must be a budget loss, a loss for what the value is must not, and every hard stop of
 * the cloner's source must be matched.
 */
@Tag("core")
class OwnedValueCodecBudgetKindTest {
   @AfterEach
   void closeSlot() {
      if(slot != null && !slot.isClosed()) {
         slot.close();
         slot.unlock();
      }
   }

   /**
    * Each hard stop of the cloner loses the root with a budget kind, the exact string the
    * cloner builds.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "entryCapKeys", "entryCapTick", "time", "marksArray", "marksTick" })
   void eachHardStopOfTheClonerIsABudgetLoss(String stop) throws Exception {
      int entries = stop.equals("time") ? PoolConfig.DEFAULT_HAND_OFF_ENTRIES : 100;
      long millis = stop.equals("time") ? 1 : PoolConfig.DEFAULT_HAND_OFF_MILLIS;
      OwnedValueCodec codec = codec(millis, entries);
      // the marking budget is checked while a root lost for what it is (a function) is marked
      // for objects shared with a kept one, which then loses the kept one with MARKS
      String marked = "{f: function(x) { return x; }, a: ";
      List<Value> roots = switch(stop) {
         // one object with more keys than the cap: refused before its keys are listed
         case "entryCapKeys" -> List.of(eval("fill([], 500, i => i)"));
         // two arrays under the cap each, over it together: refused as they are walked
         case "entryCapTick" ->
            List.of(eval("[fill([], 60, i => i), fill([], 60, i => i)]"));
         case "time" -> List.of(eval("fill({}, 50000, i => ({n: i}))"));
         // a lost root's array too long for the marking budget: refused before its keys
         case "marksArray" ->
            List.of(eval(marked + "fill([], 500, i => i)}"), eval("({n: 0})"));
         // a lost root's many small objects: refused as they are marked
         case "marksTick" ->
            List.of(eval(marked + "fill([], 150, i => ({v: i, w: i}))}"), eval("({n: 0})"));
         default -> throw new IllegalArgumentException(stop);
      };
      Object[] nodes = codec.snapshotTree(roots);
      int budgetRoot = stop.startsWith("marks") ? 1 : 0;
      String kind = switch(stop) {
         case "entryCapKeys", "entryCapTick" -> "a value with more than 100 entries";
         case "time" -> "a value that took longer than 1 ms to save";
         default -> "a value that could not be checked for an object shared with a variable " +
            "whose value is not kept, which has more than " +
            OwnedValueCodec.markBudget(100) + " entries";
      };
      OwnedValueCodec.Lost lost =
         assertInstanceOf(OwnedValueCodec.Lost.class, nodes[budgetRoot], stop);
      assertEquals(kind, lost.kind(), stop);
      assertTrue(lost.overBudget(), stop + ": " + lost.kind());
      assertTrue(OwnedValueCodec.overBudget(nodes[budgetRoot]), stop);

      if(budgetRoot == 1) {
         // the marked root itself is lost for its function, not for the budget
         OwnedValueCodec.Lost function =
            assertInstanceOf(OwnedValueCodec.Lost.class, nodes[0], stop);
         assertEquals("a function", function.kind(), stop);
         assertFalse(function.overBudget(), stop);
      }
   }

   /**
    * A loss for what the value is, a loss for sharing an object with it, and a kept value
    * next to it are not budget losses.
    */
   @Test
   void aLossForWhatTheValueIsIsNotABudgetLoss() throws Exception {
      OwnedValueCodec codec = codec(PoolConfig.DEFAULT_HAND_OFF_MILLIS, 100);
      Value shared = eval("({s: 1})");
      Value fn = eval("({f: function() {}, s: null})");
      fn.putMember("s", shared);
      Value ref = eval("({ref: null})");
      ref.putMember("ref", shared);
      // the last root shares an object with the first, lost for its function
      Object[] nodes = codec.snapshotTree(List.of(
         fn, eval("({[Symbol('x')]: 1})"),
         eval("Object.defineProperty({}, 'p', {get: function() { return 1; }, " +
              "enumerable: true})"),
         eval("({t: 0})"), ref));
      String[] kinds = { "a function", "an object with a symbol key",
                         "an object with a getter or setter", null,
                         "an object that it shares with a variable whose value is not kept" };

      for(int i = 0; i < kinds.length; i++) {
         if(kinds[i] == null) {
            assertInstanceOf(OwnedValueCodec.TreeRef.class, nodes[i], "root " + i + " kept");
            assertFalse(OwnedValueCodec.overBudget(nodes[i]), "root " + i);
            continue;
         }

         OwnedValueCodec.Lost lost =
            assertInstanceOf(OwnedValueCodec.Lost.class, nodes[i], "root " + i);
         assertEquals(kinds[i], lost.kind(), "root " + i);
         assertFalse(lost.overBudget(), "root " + i + ": " + lost.kind());
      }

      for(OwnedValueCodec.Lost lost : new OwnedValueCodec.Lost[] {
         OwnedValueCodec.HOME_BUSY, OwnedValueCodec.UNREADABLE, OwnedValueCodec.NOT_A_DATE })
      {
         assertFalse(lost.overBudget(), lost.kind());
      }
   }

   /**
    * Every hard stop in the cloner's source (stopHard: TIME, MARKS, the entry cap) builds a
    * kind isBudgetKind matches, and no other kind the cloner reports (a stop, SHARED, UNREAD)
    * does: a new hard stop, or a changed string, fails here before it reaches a lens.
    */
   @Test
   void everyHardStopKindOfTheClonerSourceIsABudgetKind() throws Exception {
      String js;

      try(InputStream in = WsEngine.class.getResourceAsStream("owned-cloner.js")) {
         assertNotNull(in, "owned-cloner.js");
         js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      Map<String, String> consts = new HashMap<>();
      Matcher c = Pattern.compile("const ([A-Z]+) = ('[^;]*?);").matcher(js);

      while(c.find()) {
         consts.put(c.group(1), c.group(2));
      }

      Set<String> hard = new LinkedHashSet<>();
      Matcher h = Pattern.compile("stopHard\\(([^;]*)\\);").matcher(js);

      while(h.find()) {
         if(!h.group(1).equals("k")) {
            hard.add(sample(consts.getOrDefault(h.group(1), h.group(1))));
         }
      }

      assertEquals(3, hard.size(), () -> "TIME, MARKS and the entry cap: " + hard);

      for(String kind : hard) {
         assertTrue(isBudgetKind(kind), kind);
      }

      Set<String> soft = new LinkedHashSet<>();
      Matcher s = Pattern.compile("[^d]stop\\(('[^']*')\\)").matcher(js);

      while(s.find()) {
         soft.add(sample(s.group(1)));
      }

      soft.add(sample(consts.get("SHARED")));
      soft.add(sample(consts.get("UNREAD")));
      assertTrue(soft.size() > 5, () -> "the cloner's other kinds: " + soft);

      for(String kind : soft) {
         assertFalse(isBudgetKind(kind), kind);
      }
   }

   // a JS string expression of literals and names ('a ' + max + ' b') with each name as 7
   private static String sample(String expr) {
      StringBuilder str = new StringBuilder();
      Matcher m = Pattern.compile("'([^']*)'|([A-Za-z_]\\w*)").matcher(expr);

      while(m.find()) {
         str.append(m.group(1) != null ? m.group(1) : "7");
      }

      assertFalse(str.isEmpty(), expr);
      return str.toString();
   }

   private static boolean isBudgetKind(String kind) throws Exception {
      Method method = OwnedValueCodec.class.getDeclaredMethod("isBudgetKind", String.class);
      method.setAccessible(true);
      return (Boolean) method.invoke(null, kind);
   }

   private OwnedValueCodec codec(long millis, int entries) throws Exception {
      slot = Slot.create(new InitSnapshot("org0", Map.of()), new EnvState().snapshot(), 0L,
                         false, Collections.synchronizedMap(new WeakHashMap<>()),
                         new PoolMetrics());
      PoolConfig d = PoolConfig.defaults();
      slot.setConfig(new PoolConfig(d.idleMillis(), d.cleanThreshold(), d.warnSlotsPerSandbox(),
                                    d.warnSlotsPerNode(), d.batchRows(), d.maxBatchRows(),
                                    d.maxHomes(), d.maxHomesPerNode(), millis, entries));
      OwnedValueCodec codec = OwnedValueCodec.of(slot);
      assertNotNull(codec, "a codec of the new slot");
      return codec;
   }

   // a value of the slot's context; fill(o, n, f) sets o[k<i>] (o[i] for an array) to f(i)
   private Value eval(String js) {
      return slot.engine().context().eval("js", "(function() { " +
         "function fill(o, n, f) { var arr = Array.isArray(o); " +
         "for(var i = 0; i < n; i++) o[arr ? i : 'k' + i] = f(i); return o; } " +
         "return (" + js + "); })()");
   }

   private Slot slot;
}
