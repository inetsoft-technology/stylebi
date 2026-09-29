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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the Task 0 audit decisions (audit-task0.md A1-A6, spec §14.12) that the E_ws host
 * boundary is built on, so a later change of one of them is a visible, reviewed edit.
 */
@Tag("core")
class WsBoundaryPolicyTest {
   @Test
   void auditDecisionsAreRecorded() {
      assertTrue(WsBoundaryPolicy.TO_HOST_KEEPS_MAIN_SHAPES, "A1");
      assertTrue(WsBoundaryPolicy.REJECT_AT_STORE_ONLY, "A2");
      assertTrue(WsBoundaryPolicy.PROXY_BACKED_COPIES, "A3");
      assertTrue(WsBoundaryPolicy.REJECT_UNSTORABLE_EXEC_RESULT, "A4");
      assertTrue(WsBoundaryPolicy.REJECT_ARRAY_FOR_MAP_PARAMETER, "A5");
   }

   @Test
   void releaseNoteCoversEveryBehaviourChange() {
      String note = String.join("\n", WsBoundaryPolicy.RELEASE_NOTE);

      for(String topic : new String[] { "var", "Collections", "identity", "{a=1}",
                                        "function", "callback", "Date.equals", "out",
                                        "typed Object, Map or List", "cleaned globals",
                                        "quietly undefined", "Known limitation",
                                        "logged at WARN once per script", "copyMutations",
                                        "viewsheet object", "Map, Set, RegExp, Promise",
                                        "Object[]", "map copy", "parameter.",
                                        "variable table", "lock", ".maxBatchRows (8192)",
                                        "double", ".batchRows (256)",
                                        // final review I2
                                        "past the one requested", "script.max.errors",
                                        "per worksheet script environment", "prototype",
                                        "crosstab aggregation", "AssetEventUtil",
                                        "-Dscript.ws.contextpool=false", "lowercase",
                                        "insertion order", "IndexOutOfBoundsException",
                                        "3 s longer", "logged at INFO",
                                        // Testing #77123, the table-owned formula var
                                        "belongs to its table", "var r; if(c)",
                                        "try/catch", "Use let", "logs one warning",
                                        "count the row twice",
                                        // Testing #77123 B1 residual, a Date var is kept
                                        "rebuilt from its time value",
                                        // B1 residual part 2, arrays and objects are kept,
                                        // functions and Intl are not
                                        "Date, array or plain object",
                                        "function- or class-valued var", "hand-off",
                                        "an Intl formatter", ".handOffMillis (5000)",
                                        ".handOffEntries (200000)", ".maxHomes (4)",
                                        // round 2: the two concurrent losses, A3 aliases
                                        "never waits for another thread's",
                                        "a context the two", "reads an older value",
                                        "var holding an object it shares with such a value",
                                        ".maxHomesPerNode (128)",
                                        // context-pool regression D1, the pool-off first batch
                                        "evaluates what pool off would", "2N + 10",
                                        "undeclared", "class-valued var",
                                        "turns script batching off",
                                        // Feature #77123, the pool is on by default
                                        "on by default", "Turning it off",
                                        "script.ws.contextPool=false in sree.properties",
                                        "unset or blank value keeps", "can occur",
                                        // Bug #77016, pool-off hangs + stall FAIL pairing
                                        "#77016", "stall.watchdog.mode=fail",
                                        // Feature #77123, fail is the watchdog default
                                        "defaults to fail", "stall.watchdog.failOnTimeout=true",
                                        "stall.watchdog.mode=alert", "only logged and goes on",
                                        "the next query of the cycle",
                                        "tryLock(timeout), is never part of a cycle",
                                        "while(!tryLock(t))" })
      {
         assertTrue(note.contains(topic), "release note misses: " + topic);
      }
   }

   @Test
   void releaseNoteIsTheWholePrText() {
      assertEquals("Worksheet script context pool (script.ws.contextPool, on by default " +
                      "from this release)", WsBoundaryPolicy.RELEASE_NOTE.get(0));
      assertTrue(WsBoundaryPolicy.RELEASE_NOTE.get(WsBoundaryPolicy.RELEASE_NOTE.size() - 1)
                    .endsWith("reused."));

      for(String line : WsBoundaryPolicy.RELEASE_NOTE) {
         assertTrue(line.length() <= 88, "line too long: " + line);
      }

      String note = String.join("\n", WsBoundaryPolicy.RELEASE_NOTE);
      assertEquals(note.indexOf("cannot be stored"), note.lastIndexOf("cannot be stored"),
                   "the store rejection is stated once");
   }
}
