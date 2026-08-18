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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

   /**
    * G2 Task 8 gave both kinds a real {@link ScriptTarget.Location} — they are servable (through
    * {@code WorksheetScriptService}, not the viewsheet-scoped read/edit services), addressed by
    * (table, field) exactly like {@link ScriptTarget.Kind#CALC_FIELD}. See
    * {@code aCalcFieldCarriesATableAndAFieldName} for the analogous calc-field test.
    */
   @Test
   void worksheetKindsAreServableWithTheirOwnLocations() throws PairingException {
      assertEquals(ScriptTarget.Location.WORKSHEET_EXPRESSION,
                   ScriptTarget.Kind.WORKSHEET_EXPRESSION.location());
      assertEquals(ScriptTarget.Location.WORKSHEET_CONDITION,
                   ScriptTarget.Kind.WORKSHEET_CONDITION.location());

      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", "Margin");
      assertEquals("Query1", t.assemblyName(), "assembly carries the TABLE name for this kind");
      assertEquals("Margin", t.name());
   }

   @Test
   void aWorksheetExpressionWithoutAFieldNameIsRefused() {
      PairingException ex = assertThrows(
         PairingException.class,
         () -> ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_EXPRESSION, "Query1", null));
      assertTrue(ex.getMessage().contains("name"),
                 "the refusal must name the missing field: " + ex.getMessage());
   }

   @Test
   void aWorksheetConditionCarriesATableAndAFieldName() throws PairingException {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION, "Query1", "Price");

      assertEquals("Query1", t.assemblyName());
      assertEquals("Price", t.name());
   }

   /**
    * The narrower sibling of {@code worksheetCondition} (finding 1 on stylebi#4654's second
    * review): same (table, field) addressing, but never a dialog sibling of
    * {@code worksheetCondition} -- see {@link ScriptTarget.Kind#WORKSHEET_CONDITION_VALUE}'s
    * javadoc for why the two must not be interchangeable.
    */
   @Test
   void worksheetConditionValueIsServableWithItsOwnLocation() throws PairingException {
      assertEquals(ScriptTarget.Location.WORKSHEET_CONDITION_VALUE,
                   ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE.location());
      assertEquals("worksheetConditionValue", ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE.wireName());

      ScriptTarget t =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE, "Query1", "Price");
      assertEquals("Query1", t.assemblyName());
      assertEquals("Price", t.name());
   }

   @Test
   void aWorksheetConditionValueIdRoundTrips() throws PairingException {
      ScriptTarget t =
         ScriptTarget.of(ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE, "Query1", "Price");
      ScriptTarget back = ScriptTarget.fromId(t.id());

      assertEquals(ScriptTarget.Kind.WORKSHEET_CONDITION_VALUE, back.kind());
      assertEquals("Query1", back.assemblyName());
      assertEquals("Price", back.name());
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

   @Test
   void resolvePrefersIdThenKindThenLegacyString() throws PairingException {
      Viewsheet vs = mock(Viewsheet.class);
      String id = ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Chart1").id();

      ScriptTarget byId = ScriptTarget.resolve(vs, id, "viewsheetOnInit", null, null, "vs-load");
      assertEquals("Chart1", byId.assemblyName(), "id must win over every other dialect");

      ScriptTarget byKind = ScriptTarget.resolve(vs, null, "assemblyOnClick", "Text1", null, "vs-load");
      assertEquals(ScriptTarget.Location.ASSEMBLY_ONCLICK, byKind.location());
      assertEquals("Text1", byKind.assemblyName());

      ScriptTarget byLegacy = ScriptTarget.resolve(vs, null, null, null, null, "vs-load");
      assertEquals(ScriptTarget.Location.VS_LOAD, byLegacy.location());
   }

   @Test
   void resolveNamesEveryAcceptedDialectWhenNoneIsGiven() {
      PairingException ex = assertThrows(
         PairingException.class, () -> ScriptTarget.resolve(null, null, null, null, null, null));

      assertTrue(ex.getMessage().contains("id"), ex.getMessage());
      assertTrue(ex.getMessage().contains("kind"), ex.getMessage());
      assertTrue(ex.getMessage().contains("target"), ex.getMessage());
   }

   @Test
   void resolveAppliesThePrecedenceFixToTheLegacyDialect() throws PairingException {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Foo:onClick")).thenReturn(mock(TextVSAssembly.class));

      ScriptTarget t = ScriptTarget.resolve(vs, null, null, null, null, "assembly:Foo:onClick");

      assertEquals(ScriptTarget.Location.ASSEMBLY, t.location());
      assertEquals("Foo:onClick", t.assemblyName());
   }

   @Test
   void calcFieldIsAServableKindWithItsOwnLocation() throws PairingException {
      assertEquals(ScriptTarget.Location.CALC_FIELD,
                   ScriptTarget.Kind.CALC_FIELD.location());
      assertEquals("calcField", ScriptTarget.Kind.CALC_FIELD.wireName());
      assertEquals(ScriptTarget.Kind.CALC_FIELD, ScriptTarget.Kind.fromWire("calcField"));
   }

   @Test
   void aCalcFieldCarriesATableAndAFieldName() throws PairingException {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");

      assertEquals("Query1", t.assemblyName(), "assembly carries the TABLE name for this kind");
      assertEquals("Margin", t.name());
   }

   @Test
   void aCalcFieldWithoutAFieldNameIsRefused() {
      PairingException ex = assertThrows(
         PairingException.class,
         () -> ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", null));
      assertTrue(ex.getMessage().contains("name"),
                 "the refusal must name the missing field: " + ex.getMessage());
   }

   @Test
   void aCalcFieldWithoutATableIsRefused() {
      assertThrows(PairingException.class,
                   () -> ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, null, "Margin"));
   }

   @Test
   void everyOtherKindStillReportsANullName() throws PairingException {
      assertNull(ScriptTarget.of(ScriptTarget.Kind.VIEWSHEET_ON_INIT, null).name());
      assertNull(ScriptTarget.of(ScriptTarget.Kind.ASSEMBLY_MAIN, "Chart1").name());
   }

   @Test
   void aCalcFieldIdRoundTripsAllThreeComponents() throws PairingException {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Query1", "Margin");
      ScriptTarget back = ScriptTarget.fromId(t.id());

      assertEquals(ScriptTarget.Kind.CALC_FIELD, back.kind());
      assertEquals("Query1", back.assemblyName());
      assertEquals("Margin", back.name());
   }

   /**
    * The delimiter hazard, one component further on. The id encodes kind|assembly|name, so a table
    * or field name containing '|' must still round-trip — the same class of bug the ':'-delimited
    * grammar had, and the reason ids are decoded by position rather than by splitting on every
    * separator.
    */
   @Test
   void aCalcFieldIdSurvivesPipesInEitherName() throws PairingException {
      ScriptTarget t = ScriptTarget.of(ScriptTarget.Kind.CALC_FIELD, "Od|d", "Ma|rgin");
      ScriptTarget back = ScriptTarget.fromId(t.id());

      assertEquals("Od|d", back.assemblyName());
      assertEquals("Ma|rgin", back.name());
   }

   /**
    * A trailing unpaired '\' cannot come from escape() -- it always emits '\' paired with the
    * character it guards -- so one showing up here means the id is corrupted or hand-crafted,
    * and must be refused rather than silently kept as a literal backslash.
    */
   @Test
   void aDanglingEscapeInAnIdIsRefused() {
      String canonical = "calcField|Query1|Ma\\";
      String id = Base64.getUrlEncoder().withoutPadding()
         .encodeToString(canonical.getBytes(StandardCharsets.UTF_8));

      PairingException ex = assertThrows(PairingException.class, () -> ScriptTarget.fromId(id));
      assertTrue(ex.getMessage().contains(id),
                 "the refusal must name the offending id: " + ex.getMessage());
   }
}
