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
package inetsoft.analytic.composition.event;

import inetsoft.uql.viewsheet.TextInputVSAssembly;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("core")
class InputScriptEventTest {
   /**
    * The script "event" object is reachable from a viewsheet tooltip, so its
    * string form must describe the event rather than expose Java's default
    * "class@identityHash", which differs on every JVM run.
    */
   @Test
   void toStringDescribesTheEvent() {
      InputScriptEvent event =
         new InputScriptEvent("Input1", mock(TextInputVSAssembly.class));

      assertEquals("Event[name=Input1, type=textinput]", event.toString());
   }

   @Test
   void toStringIsStableAcrossInstances() {
      String a = new InputScriptEvent("Input1", mock(TextInputVSAssembly.class)).toString();
      String b = new InputScriptEvent("Input1", mock(TextInputVSAssembly.class)).toString();

      assertEquals(a, b);
      assertFalse(a.contains("@"), "must not expose an identity hash: " + a);
   }
}
