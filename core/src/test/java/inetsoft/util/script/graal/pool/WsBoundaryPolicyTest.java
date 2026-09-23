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
                                        "function", "callback", "Date.equals", "out" })
      {
         assertTrue(note.contains(topic), "release note misses: " + topic);
      }
   }
}
