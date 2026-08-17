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

import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.PairingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

   @Test
   void mapsEveryTierOneKindToItsLocation() throws PairingException {
      assertEquals(ScriptTarget.Location.VS_INIT,
                   ScriptTarget.Kind.VIEWSHEET_ON_INIT.location());
      assertEquals(ScriptTarget.Location.VS_LOAD,
                   ScriptTarget.Kind.VIEWSHEET_ON_LOAD.location());
      assertEquals(ScriptTarget.Location.ASSEMBLY,
                   ScriptTarget.Kind.ASSEMBLY_MAIN.location());
      assertEquals(ScriptTarget.Location.ASSEMBLY_ONCLICK,
                   ScriptTarget.Kind.ASSEMBLY_ON_CLICK.location());
   }

   @Test
   void worksheetKindsAreReservedButNotServable() {
      assertNull(ScriptTarget.Kind.WORKSHEET_EXPRESSION.location());
      assertNull(ScriptTarget.Kind.WORKSHEET_CONDITION.location());

      PairingException ex = assertThrows(
         PairingException.class,
         () -> ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, null));
      assertTrue(ex.getMessage().contains("paired from that expression's editor"),
                 "a reserved kind must explain the scope, not read as an unknown kind: " +
                 ex.getMessage());
   }

   @Test
   void wireNamesAreTheCamelCaseTaxonomy() throws PairingException {
      assertEquals("viewsheetOnInit", ScriptTarget.Kind.VIEWSHEET_ON_INIT.wireName());
      assertEquals("assemblyOnClick", ScriptTarget.Kind.ASSEMBLY_ON_CLICK.wireName());
      assertEquals(ScriptTarget.Kind.ASSEMBLY_MAIN, ScriptTarget.Kind.fromWire("assemblyMain"));
   }

   @Test
   void rejectsOnRefreshByName() {
      PairingException ex = assertThrows(
         PairingException.class, () -> ScriptTarget.Kind.fromWire("onRefresh"));
      assertTrue(ex.getMessage().contains("viewsheetOnLoad"),
                 "onRefresh must point at onLoad rather than just being unknown: " +
                 ex.getMessage());
   }

   @Test
   void idRoundTripsWithoutServerState() throws PairingException {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_ON_CLICK, "Text1");
      ScriptTarget back = ScriptTarget.fromId(t.id());

      assertEquals(ScriptTarget.Location.ASSEMBLY_ONCLICK, back.location());
      assertEquals("Text1", back.assemblyName());
      assertEquals(t.id(), back.id(), "the encoding must be deterministic, not a handle");
   }

   @Test
   void idSurvivesAnAssemblyNameContainingTheDelimiter() throws PairingException {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Foo:onClick");
      ScriptTarget back = ScriptTarget.fromId(t.id());

      assertEquals(ScriptTarget.Location.ASSEMBLY, back.location());
      assertEquals("Foo:onClick", back.assemblyName());
   }

   @Test
   void idIsUrlSafeAndUnpadded() throws PairingException {
      String id = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "a/b+c?d").id();

      assertFalse(id.contains("/"), id);
      assertFalse(id.contains("+"), id);
      assertFalse(id.contains("="), id);
   }

   @Test
   void anExactAssemblyNameBeatsTheOnClickSuffix() throws PairingException {
      Viewsheet vs = mock(Viewsheet.class);
      TextVSAssembly literal = mock(TextVSAssembly.class);
      when(vs.getAssembly("Foo:onClick")).thenReturn(literal);

      ScriptTarget t = ScriptTarget.parse(vs, "assembly:Foo:onClick");

      assertEquals(ScriptTarget.Location.ASSEMBLY, t.location());
      assertEquals("Foo:onClick", t.assemblyName());
   }

   @Test
   void withoutAnExactMatchTheSuffixStillMeansOnClick() throws PairingException {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Foo:onClick")).thenReturn(null);

      ScriptTarget t = ScriptTarget.parse(vs, "assembly:Foo:onClick");

      assertEquals(ScriptTarget.Location.ASSEMBLY_ONCLICK, t.location());
      assertEquals("Foo", t.assemblyName());
   }
}
