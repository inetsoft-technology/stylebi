/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.script.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ConfirmEventScriptableTest {
   @Test
   void trueTextAndBooleanTrueReadAsTrue() {
      ConfirmEventScriptable s = new ConfirmEventScriptable();
      s.putMember("confirmed", "true");
      assertEquals(true, s.getMember("confirmed"));
      s.putMember("confirmed", false);
      s.putMember("confirmed", true);
      assertEquals(true, s.getMember("confirmed"));
   }

   @Test
   void otherValuesReadAsFalse() {
      ConfirmEventScriptable s = new ConfirmEventScriptable();
      s.putMember("confirmed", true);
      s.putMember("confirmed", 1);
      assertEquals(false, s.getMember("confirmed"));
      s.putMember("confirmed", true);
      s.putMember("confirmed", "false");
      assertEquals(false, s.getMember("confirmed"));
   }

   /**
    * Bug #81666: null (and a script undefined that arrives as null) must read as false,
    * not throw a NullPointerException.
    */
   @Test
   void nullReadsAsFalse() {
      ConfirmEventScriptable s = new ConfirmEventScriptable();
      s.putMember("confirmed", true);
      assertDoesNotThrow(() -> s.putMember("confirmed", null));
      assertEquals(false, s.getMember("confirmed"));
   }

   @Test
   void unknownMemberIgnored() {
      ConfirmEventScriptable s = new ConfirmEventScriptable();
      s.putMember("other", "true");
      assertNull(s.getMember("other"));
      assertFalse(s.hasMember("other"));
   }
}
