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

import java.util.*;

/**
 * Seeded generator of global-state polluters and read-only probes for the clean fuzz
 * (bug #77123, reliability task 3). A polluter is 1-6 building blocks; each block knows its
 * category, the probe entries it may legitimately change (B7/B8), the non-configurable
 * leftovers it declares, and how many configurable foreign keys it leaves.
 */
final class RelScriptGenerator {
   enum Category { CLEANABLE, B7_PROTOTYPE, B8_BASELINE_MUTATION, DISCARDS_SLOT }

   record Gen(String source, Category category) {}

   /**
    * One building block of a polluter.
    *
    * @param kind       the generator's name for the block, for reports.
    * @param patched    probe entries a B7/B8 block may change.
    * @param leftovers  names left as non-configurable globals (spec §14.1 leftovers).
    * @param foreign    configurable foreign keys it leaves (the clean deletes up to 32).
    * @param stage      0 ordinary, 1 locks the global, 2 ends the script (throw/loop).
    */
   record Block(String kind, String source, Category category, Set<String> patched,
                Set<String> leftovers, int foreign, int stage)
   {
   }

   RelScriptGenerator(long seed) {
      this.random = new Random(seed);
   }

   /**
    * Names never used, e.g. the leftovers an earlier polluter left on a reused slot: a block
    * writing one would not add the foreign key it counts.
    */
   RelScriptGenerator avoid(Set<String> names) {
      this.avoided = names;
      return this;
   }

   /**
    * 1-6 random blocks with distinct names; a global lock comes after the ordinary blocks and
    * a throw or loop comes last, so every block before it runs.
    */
   List<Block> blocks() {
      List<String> names = new ArrayList<>(Arrays.asList(NAMES));
      Collections.shuffle(names, random);
      names.removeAll(avoided);
      Deque<String> free = new ArrayDeque<>(names);
      int n = 1 + random.nextInt(6);
      List<Block> list = new ArrayList<>();

      for(int i = 0; i < n; i++) {
         list.add(block(free));
      }

      // keep at most one block of each later stage, placed in stage order
      List<Block> result = new ArrayList<>();
      Block lock = null, end = null;

      for(Block b : list) {
         if(b.stage() == 0) {
            result.add(b);
         }
         else if(b.stage() == 1 && lock == null) {
            lock = b;
         }
         else if(b.stage() == 2 && end == null) {
            end = b;
         }
      }

      if(lock != null) {
         result.add(lock);
      }

      if(end != null) {
         result.add(end);
      }

      return result;
   }

   /**
    * The polluter of these blocks, with its category: DISCARDS_SLOT if any block makes the
    * clean fail, or more than 32 configurable foreign keys or 256 leftovers are left;
    * otherwise B7 or B8 if any block patches a prototype or a baseline object.
    */
   static Gen combine(List<Block> blocks) {
      StringBuilder js = new StringBuilder();
      Set<String> leftovers = new HashSet<>();
      int foreign = 0;
      boolean discards = false, b7 = false, b8 = false;

      for(Block b : blocks) {
         js.append(b.source()).append('\n');
         leftovers.addAll(b.leftovers());
         foreign += b.foreign();
         discards |= b.category() == Category.DISCARDS_SLOT;
         b7 |= b.category() == Category.B7_PROTOTYPE;
         b8 |= b.category() == Category.B8_BASELINE_MUTATION;
      }

      js.append("'done'");
      Category category = discards || foreign > PoolConfig.MAX_FOREIGN_DELETES ||
         leftovers.size() > PoolConfig.defaults().cleanThreshold() ? Category.DISCARDS_SLOT :
         b7 ? Category.B7_PROTOTYPE : b8 ? Category.B8_BASELINE_MUTATION : Category.CLEANABLE;
      return new Gen(js.toString(), category);
   }

   static Set<String> patched(List<Block> blocks) {
      Set<String> set = new HashSet<>();
      blocks.forEach(b -> set.addAll(b.patched()));
      return set;
   }

   /**
    * Whether the block's leftovers are declared (var, let/const rewritten to var, function,
    * in a block or not), not made at runtime by defineProperty.
    */
   static boolean declares(Block b) {
      return DECLARING.contains(b.kind()) || b.kind().startsWith("leftovers ");
   }

   /**
    * Whether the block declares names holding a value that is not undefined when the script
    * ends (the leftovers x<n> var list has no initializers).
    */
   static boolean declaresDefined(Block b) {
      return DECLARING.contains(b.kind());
   }

   static Set<String> leftovers(List<Block> blocks) {
      Set<String> set = new HashSet<>();
      blocks.forEach(b -> set.addAll(b.leftovers()));
      return set;
   }

   private Block block(Deque<String> free) {
      String n = free.isEmpty() ? "zqy" + random.nextInt(1_000_000) : free.pop();
      String v = value();
      int pick = random.nextInt(1000);

      // ordinary declarations and writes (the bulk)
      if(pick < 60) {
         return clean("var", "var " + n + " = " + v + ";", Set.of(n), 0);
      }
      if(pick < 100) {
         return clean("var-in-block", "{ var " + n + " = " + v + "; }", Set.of(n), 0);
      }
      if(pick < 140) {
         return clean("let", "let " + n + " = " + v + ";", Set.of(n), 0);
      }
      if(pick < 170) {
         return clean("const", "const " + n + " = " + v + ";", Set.of(n), 0);
      }
      if(pick < 200) {
         return clean("let-in-block", "{ let " + n + " = " + v + "; const " + n + "2 = 1; }",
                      Set.of(), 0);
      }
      if(pick < 240) {
         return clean("function", "function " + n + "() { return " + v + "; }", Set.of(n), 0);
      }
      if(pick < 260) {
         return clean("function-in-block", "{ function " + n + "() { return 1; } }",
                      Set.of(n), 0);
      }
      if(pick < 330) {
         return clean("implicit", n + " = " + v + ";", Set.of(), 1);
      }
      if(pick < 380) {
         return clean("globalThis[k]", "globalThis['" + n + "'] = " + v + ";", Set.of(), 1);
      }
      if(pick < 400) {
         // a direct eval's var is local to the engine's wrapper function, so it is not a
         // global; an indirect eval's var and a direct eval's assignment are
         return random.nextBoolean() ?
            clean("eval-var", "(0, eval)('var " + n + " = 1');", Set.of(), 1) :
            clean("eval-assign", "eval('" + n + " = 1');", Set.of(), 1);
      }
      if(pick < 420) {
         return clean("Function", "Function('" + n + " = 1')();", Set.of(), 1);
      }
      if(pick < 500) {
         boolean w = random.nextBoolean(), e = random.nextBoolean(), c = random.nextBoolean();
         boolean undef = random.nextInt(4) == 0;
         String src = "Object.defineProperty(globalThis, '" + n + "', {value: " +
            (undef ? "undefined" : v) + ", writable: " + w + ", enumerable: " + e +
            ", configurable: " + c + "});";
         String kind = "defineProperty w" + (w ? 1 : 0) + "e" + (e ? 1 : 0) + "c" + (c ? 1 : 0) +
            (undef ? " undefined" : "");

         if(c) {
            return clean(kind, src, Set.of(), 1);
         }

         // a non-configurable non-writable value other than undefined cannot be cleaned
         return !w && !undef ? discard(kind, src) : clean(kind, src, Set.of(n), 0);
      }
      if(pick < 540) {
         boolean c = random.nextInt(4) != 0;
         String src = "Object.defineProperty(globalThis, '" + n + "', {get() { throw 1; }, " +
            "set(x) { throw 2; }, enumerable: " + random.nextBoolean() + ", configurable: " + c +
            "});";
         return c ? clean("accessor", src, Set.of(), 1) : discard("accessor c0", src);
      }
      if(pick < 560) {
         return clean("symbols", "globalThis[Symbol.for('" + n + "')] = 1; globalThis[Symbol('" + n +
                      "')] = 2;",
                      Set.of(), 2);
      }
      if(pick < 600) {
         String k = BASELINE[random.nextInt(BASELINE.length)];
         return clean("delete " + k, "delete globalThis." + k + ";", Set.of(), 0);
      }
      if(pick < 650) {
         String k = BASELINE[random.nextInt(BASELINE.length)];
         return clean("overwrite " + k, k + " = " + v + ";", Set.of(), 0);
      }
      if(pick < 665) {
         String k = BASELINE[random.nextInt(BASELINE.length)];
         return discard("lock " + k, "Object.defineProperty(globalThis, '" + k +
            "', {value: 1, writable: false, configurable: false});");
      }
      if(pick < 675) {
         return clean("overwrite host var", "zhost = " + v + ";", Set.of(), 0);
      }
      if(pick < 685) {
         return clean("delete host var", "delete globalThis.zhost;", Set.of(), 0);
      }
      if(pick < 700) {
         int[] counts = {31, 32, 33, 40};
         int count = counts[random.nextInt(counts.length)];
         return clean("foreign x" + count, "(function() { for(let i = 0; i < " + count +
            "; i++) { globalThis['" + n + "_' + i] = i; } })();", Set.of(), count);
      }
      if(pick < 710) {
         int[] counts = {200, 256, 257, 300};
         int count = counts[random.nextInt(counts.length)];
         Set<String> left = new HashSet<>();
         StringBuilder src = new StringBuilder("var ");

         for(int i = 0; i < count; i++) {
            src.append(i == 0 ? "" : ", ").append("zv").append(i);
            left.add("zv" + i);
         }

         return clean("leftovers x" + count, src.append(';').toString(), left, 0);
      }
      if(pick < 740) {
         return setPrototype(n, v);
      }
      if(pick < 780) {
         return prototypePatch(n, v);
      }
      if(pick < 810) {
         return baselineMutation(n, v);
      }
      if(pick < 830) {
         String[] ops = {"freeze", "seal", "preventExtensions"};
         String op = ops[random.nextInt(ops.length)];
         return new Block(op, "Object." + op + "(globalThis);", Category.DISCARDS_SLOT,
                          Set.of(), Set.of(), 0, 1);
      }
      if(pick < 900) {
         return new Block("throw", n + " = " + v + "; throw new Error('zq partial');",
                          Category.CLEANABLE, Set.of(), Set.of(), 1, 2);
      }
      if(pick < 930) {
         return sharedHostWrite(n, v);
      }
      if(random.nextInt(40) != 0) {
         return clean("Reflect.set", "Reflect.set(globalThis, '" + n + "', " + v + ");",
                      Set.of(), 1);
      }

      return new Block("loop", n + " = 1; while(true) {}", Category.CLEANABLE, Set.of(),
                       Set.of(), 1, 2);
   }

   private Block setPrototype(String n, String v) {
      int kind = random.nextInt(3);

      if(kind == 0) {
         return new Block("setPrototypeOf null", "Object.setPrototypeOf(globalThis, null);",
                          Category.CLEANABLE, Set.of(), Set.of(), 0, 0);
      }

      String proto = kind == 1 ? "{" + n + ": " + v + "}" : "Object.create(Object.prototype, " +
         "{" + n + ": {value: " + v + "}})";
      return new Block("setPrototypeOf", "Object.setPrototypeOf(globalThis, " + proto + ");",
                       Category.CLEANABLE, Set.of(), Set.of(), 0, 0);
   }

   /**
    * Writes to the host objects every context of the JVM shares (#5885: CALC and its
    * functions, the ScriptFunction globals, the StyleConstant/Chart constant scopes). The host
    * boundary ignores (or refuses) them, so they are cleanable with no drift: a write that
    * reached the shared Java object would show on every context, the reference env included,
    * which the probe's host entries check against their pristine values.
    */
   private Block sharedHostWrite(String n, String v) {
      String[] targets = { "CALC", "StyleConstant", "Chart", "sum", "CALC.sum", "isNull" };
      StringBuilder src = new StringBuilder();
      int count = 1 + random.nextInt(3);

      for(int i = 0; i < count; i++) {
         src.append("try { ").append(targets[random.nextInt(targets.length)]).append('.')
            .append(n).append(" = ").append(v).append("; } catch(e) {} ");
      }

      if(random.nextInt(3) == 0) {
         src.append("try { ").append(HOST_OVERWRITES[random.nextInt(HOST_OVERWRITES.length)])
            .append("; } catch(e) {}");
      }

      return clean("shared host write", src.toString().trim(), Set.of(), 0);
   }

   /**
    * @return the value a probe's host entry has on any context while no write reached a
    *         shared host object, or {@code null} for an entry that is not a host entry.
    */
   static String hostPristine(String key) {
      if(!key.startsWith("host:")) {
         return null;
      }

      String expected = HOST_PRISTINE.get(key.substring(5));
      return expected != null ? expected : "undefined"; // a zq* member none of them has
   }

   private Block prototypePatch(String n, String v) {
      int kind = random.nextInt(6);

      return switch(kind) {
         case 0 -> b7("Array.prototype", "Array.prototype." + n + " = " + v + ";", "arr:" + n);
         case 1 -> b7("String.prototype", "String.prototype." + n + " = " + v + ";", "str:" + n);
         // a host function (a CALC or ScriptFunction global) has Function.prototype, and every
         // host object Object.prototype, as its prototype in the guest
         case 2 -> b7("Function.prototype", "Function.prototype." + n + " = " + v + ";",
                      "fn:" + n, "host:CALC.sum." + n, "host:sum." + n, "host:isNull." + n);
         case 3 -> b7("Object.prototype", "Object.prototype." + n + " = " + v + ";",
                      "name:" + n, "arr:" + n, "str:" + n, "fn:" + n, "obj:" + n, "math:" + n,
                      "json:" + n, "host:CALC." + n, "host:StyleConstant." + n,
                      "host:Chart." + n, "host:sum." + n, "host:CALC.sum." + n,
                      "host:isNull." + n);
         default -> {
            // a descriptor field name on %Object.prototype% (the clean's slow path)
            String f = DESC_FIELDS[random.nextInt(DESC_FIELDS.length)];
            yield b7("Object.prototype." + f, "Object.prototype." + f + " = " + v + ";",
                     "name:" + f, "arr:" + f, "str:" + f, "fn:" + f, "obj:" + f);
         }
      };
   }

   private Block baselineMutation(String n, String v) {
      return switch(random.nextInt(4)) {
         case 0 -> b8("Math.new", "Math." + n + " = " + v + ";", "math:" + n, "own:Math");
         case 1 -> b8("Math.max", "Math.max = function() { return 42; };", "call:Math.max");
         case 2 -> b8("JSON.new", "JSON." + n + " = " + v + ";", "json:" + n, "own:JSON");
         default -> b8("JSON.stringify", "JSON.stringify = function() { return 'x'; };",
                       "call:JSON.stringify");
      };
   }

   private static Block clean(String kind, String src, Set<String> leftovers, int foreign) {
      return new Block(kind, src, Category.CLEANABLE, Set.of(), leftovers, foreign, 0);
   }

   private static Block discard(String kind, String src) {
      return new Block(kind, src, Category.DISCARDS_SLOT, Set.of(), Set.of(), 0, 0);
   }

   private static Block b7(String kind, String src, String... patched) {
      return new Block(kind, src, Category.B7_PROTOTYPE, Set.of(patched), Set.of(), 0, 0);
   }

   private static Block b8(String kind, String src, String... patched) {
      return new Block(kind, src, Category.B8_BASELINE_MUTATION, Set.of(patched), Set.of(), 0,
                       0);
   }

   private String value() {
      return VALUES[random.nextInt(VALUES.length)];
   }

   /**
    * A read-only probe: an IIFE that declares nothing global and returns sorted
    * {@code key=value} lines. The own-key listing is always included; the other sections
    * and names are a random subset in random order.
    */
   String probe() {
      List<String> parts = new ArrayList<>();
      parts.add(OWN_KEYS);
      parts.add("out.push('global:proto=' + (Object.getPrototypeOf(globalThis) === " +
                "Object.prototype ? 'OP' : String(Object.getPrototypeOf(globalThis))));");
      parts.add("out.push('global:ext=' + Object.isExtensible(globalThis) + ' frozen=' + " +
                "Object.isFrozen(globalThis) + ' sealed=' + Object.isSealed(globalThis));");

      for(String[] call : CALLS) {
         if(random.nextInt(5) != 0) {
            parts.add("try { out.push('call:" + call[0] + "=' + String(" + call[1] + ")); } " +
                      "catch(e) { out.push('call:" + call[0] + "=throw'); }");
         }
      }

      List<String> names = new ArrayList<>(Arrays.asList(NAMES));
      names.addAll(Arrays.asList(DESC_FIELDS));
      names.addAll(Arrays.asList(PROBE_EXTRA));
      names.addAll(Arrays.asList(BASELINE));

      for(String n : names) {
         if(random.nextInt(10) < 7) {
            parts.add("try { out.push('name:" + n + "=' + typeof " + n + "); } " +
                      "catch(e) { out.push('name:" + n + "=throw ' + e.name); }");
         }
      }

      for(String n : NAMES) {
         if(random.nextBoolean()) {
            parts.add(member("arr", "[]", n));
            parts.add(member("str", "''", n));
            parts.add(member("fn", "(function() {})", n));
            parts.add(member("obj", "({})", n));
            parts.add(member("math", "Math", n));
            parts.add(member("json", "JSON", n));
         }
      }

      for(String f : DESC_FIELDS) {
         if(random.nextBoolean()) {
            parts.add(member("arr", "[]", f));
            parts.add(member("str", "''", f));
            parts.add(member("fn", "(function() {})", f));
            parts.add(member("obj", "({})", f));
         }
      }

      for(String n : NAMES) {
         if(random.nextInt(4) == 0) {
            parts.add("out.push('sym:" + n + "=' + (Symbol.for('" + n + "') in globalThis));");
         }
      }

      // the shared host objects: each zq* member some context may have tried to write, and
      // the members the host overwrites target
      for(String n : NAMES) {
         if(random.nextInt(4) == 0) {
            for(String on : new String[] { "CALC", "StyleConstant", "Chart", "sum", "CALC.sum",
                                           "isNull" })
            {
               parts.add(hostEntry(on + "." + n, "typeof " + on + "." + n));
            }
         }
      }

      for(Map.Entry<String, String> e : HOST_READS.entrySet()) {
         parts.add(hostEntry(e.getKey(), e.getValue()));
      }
      Collections.shuffle(parts, random);
      return "(function() { 'use strict'; const out = [];\n" + String.join("\n", parts) +
         "\nout.sort(); return out.join(String.fromCharCode(10)); })()";
   }

   private static String hostEntry(String name, String expr) {
      return "try { out.push('host:" + name + "=' + String(" + expr + ")); } " +
         "catch(e) { out.push('host:" + name + "=throw'); }";
   }

   private static String member(String prefix, String on, String n) {
      return "try { out.push('" + prefix + ":" + n + "=' + typeof " + on + "['" + n + "']); } " +
         "catch(e) { out.push('" + prefix + ":" + n + "=throw'); }";
   }

   // own keys with typeof and descriptor flags; a data property's primitive value is shown,
   // and an object or function value gets an identity signature (native or source length and
   // own name for a function; own-key count and first sorted own key), so a same-typeof
   // overwrite shows
   private static final String OWN_KEYS = """
      { const G = globalThis; const ks = Reflect.ownKeys(G); const FTS = Function.prototype.toString;
        const sig = function(v) {
           // the global itself holds the polluters' leftovers, which the own-key lines show
           if(v === G) return ' the-global';
           let r = '';
           if(typeof v === 'function') {
              try { const src = FTS.call(v); r += src.indexOf('[native code]') >= 0 ? ' native' : ' src' + src.length; }
              catch(e) { r += ' host'; }
              try {
                 const nd = Reflect.getOwnPropertyDescriptor(v, 'name');
                 if(nd !== undefined && typeof nd.value === 'string') r += ' name:' + nd.value;
              }
              catch(e) { r += ' name?'; }
           }
           try {
              const o = Reflect.ownKeys(v).map(String).sort();
              r += ' keys' + o.length + (o.length > 0 ? ':' + o[0] : '');
           }
           catch(e) { r += ' keys?'; }
           return r;
        };
        for(let i = 0; i < ks.length; i++) {
           const k = ks[i]; const d = Reflect.getOwnPropertyDescriptor(G, k);
           let s;
           if(Object.hasOwn(d, 'value')) {
              const t = typeof d.value;
              s = t + (t === 'number' || t === 'string' || t === 'boolean' ? ':' + d.value : '') +
                 (d.value === null ? ':null' : '') +
                 ((t === 'object' && d.value !== null) || t === 'function' ? sig(d.value) : '') +
                 ' w' + (d.writable ? 1 : 0);
           }
           else {
              s = 'accessor get' + (d.get === undefined ? 0 : 1) + ' set' + (d.set === undefined ? 0 : 1);
           }
           out.push('own:' + String(k) + '=' + s + ' e' + (d.enumerable ? 1 : 0) + ' c' +
                    (d.configurable ? 1 : 0));
        } }""";

   // the blocks that declare their leftover with an initializer or as a function
   private static final Set<String> DECLARING = Set.of(
      "var", "var-in-block", "let", "const", "function", "function-in-block");
   // 16 names polluters use, plus the '2' suffix of let-in-block
   static final String[] NAMES = {
      "zqa", "zqb", "zqc", "zqd", "zqe", "zqf", "zqg", "zqh", "zqi", "zqj", "zqk", "zql", "zqm",
      "zqn", "zqo", "zqp"
   };
   private static final String[] DESC_FIELDS = {
      "value", "writable", "get", "set", "enumerable", "configurable"
   };
   private static final String[] PROBE_EXTRA = {
      "zhost", "zqz", "zv0", "zv255", "zv299", "hasOwnProperty",
      "toString", "constructor", "valueOf"
   };
   // baseline globals the polluters delete, overwrite or lock
   private static final String[] BASELINE = {
      "parseInt", "parseFloat", "isNaN", "JSON", "Math", "Array", "CALC", "formatDate",
      "StyleConstant", "encodeURIComponent"
   };
   private static final String[][] CALLS = {
      {"parseInt", "parseInt('7')"},
      {"parseFloat", "parseFloat('1.5')"},
      {"isNaN", "isNaN('x')"},
      {"JSON.stringify", "JSON.stringify({a: 1})"},
      {"Math.max", "Math.max(1, 2)"},
      {"Array.isArray", "Array.isArray([])"},
      {"encodeURIComponent", "encodeURIComponent('a b')"},
      {"typeof CALC", "typeof CALC"},
      {"typeof formatDate", "typeof formatDate"},
      {"zhost", "typeof zhost === 'undefined' ? 'none' : zhost"},
   };
   // overwrites of shared host members the host boundary ignores or refuses
   private static final String[] HOST_OVERWRITES = {
      "CALC.sum = 42", "delete CALC.sum", "CALC.sum = function() { return 42; }",
      "StyleConstant.PORTRAIT = 99", "delete StyleConstant.PORTRAIT", "Chart.PORTRAIT = 99"
   };
   // probe host entries read on every probe, and their expressions
   private static final Map<String, String> HOST_READS = new LinkedHashMap<>();
   private static final Map<String, String> HOST_PRISTINE = new HashMap<>();

   static {
      HOST_READS.put("CALC.sum", "typeof CALC.sum");
      HOST_READS.put("CALC.sum()", "CALC.sum([1, 2, 3])");
      HOST_READS.put("StyleConstant.PORTRAIT", "StyleConstant.PORTRAIT");
      HOST_PRISTINE.put("CALC.sum", "function");
      HOST_PRISTINE.put("CALC.sum()", "6");
      HOST_PRISTINE.put("StyleConstant.PORTRAIT", "1");
   }

   private static final String[] VALUES = {
      "1", "'s'", "true", "null", "{a: 1}", "[1, 2]", "function() { return 3; }", "0.5"
   };

   private final Random random;
   private Set<String> avoided = Set.of();
}
