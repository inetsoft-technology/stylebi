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
package inetsoft.web.wiz.script;

import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptTargetTest {

   @Test
   void parsesVsInit() throws PairingException {
      ScriptTarget t = ScriptTarget.parse("vs-init");
      assertEquals(ScriptTarget.Location.VS_INIT, t.location());
      assertNull(t.assemblyName());
      assertEquals("vs-init", t.toString());
   }

   @Test
   void parsesVsLoad() throws PairingException {
      ScriptTarget t = ScriptTarget.parse("vs-load");
      assertEquals(ScriptTarget.Location.VS_LOAD, t.location());
      assertEquals("vs-load", t.toString());
   }

   @Test
   void parsesAssemblyScript() throws PairingException {
      ScriptTarget t = ScriptTarget.parse("assembly:Chart1");
      assertEquals(ScriptTarget.Location.ASSEMBLY, t.location());
      assertEquals("Chart1", t.assemblyName());
      assertEquals("assembly:Chart1", t.toString());
   }

   @Test
   void parsesAssemblyOnClick() throws PairingException {
      ScriptTarget t = ScriptTarget.parse("assembly:Table1:onClick");
      assertEquals(ScriptTarget.Location.ASSEMBLY_ONCLICK, t.location());
      assertEquals("Table1", t.assemblyName());
      assertEquals("assembly:Table1:onClick", t.toString());
   }

   @Test
   void rejectsNullOrBlank() {
      assertThrows(PairingException.class, () -> ScriptTarget.parse(null));
      assertThrows(PairingException.class, () -> ScriptTarget.parse(""));
      assertThrows(PairingException.class, () -> ScriptTarget.parse("   "));
   }

   @Test
   void rejectsMissingAssemblyName() {
      assertThrows(PairingException.class, () -> ScriptTarget.parse("assembly:"));
      assertThrows(PairingException.class, () -> ScriptTarget.parse("assembly::onClick"));
   }

   @Test
   void rejectsUnknownFormat() {
      assertThrows(PairingException.class, () -> ScriptTarget.parse("onInit"));
      assertThrows(PairingException.class, () -> ScriptTarget.parse("Chart1"));
   }
}
