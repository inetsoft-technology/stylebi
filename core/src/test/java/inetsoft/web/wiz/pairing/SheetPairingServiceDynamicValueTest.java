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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mint-time validation of the {@code dynamicValue} editorContext (Bug #80459, design 1.3). */
@WizAgentTestSupport
class SheetPairingServiceDynamicValueTest {
   private static final long NOW = 1_000_000L;

   private static SheetPairingService service() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      vs.addAssembly(new OvalVSAssembly(vs, "Oval1"));
      vs.addAssembly(new GaugeVSAssembly(vs, "Gauge1"));
      vs.addAssembly(new SelectionListVSAssembly(vs, "List1"));
      vs.addAssembly(new SelectionTreeVSAssembly(vs, "Tree1"));
      vs.addAssembly(new EmbeddedTableVSAssembly(vs, "Embedded1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return new SheetPairingService(() -> NOW, id -> null, id -> "vs-1".equals(id) ? rvs : null);
   }

   private static String mint(SheetPairingService svc, String assembly, String name)
      throws PairingException
   {
      return svc.mint("vs-1", "user", "sock", "user", SheetType.VIEWSHEET,
                      new EditorContext("dynamicValue", assembly, name, null));
   }

   private static PairingException refused(SheetPairingService svc, String assembly, String name,
                                           String fragment)
   {
      PairingException ex = assertThrows(PairingException.class, () -> mint(svc, assembly, name),
                                         assembly + "/" + name);
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
      return ex;
   }

   @Test
   void theKindIsRecognizedAndListedInTheUnknownKindMessage() {
      assertTrue(SheetPairingService.RECOGNIZED_KINDS.contains("dynamicValue"));
      assertEquals("dynamicValue", EditorContext.DYNAMIC_VALUE);

      SheetPairingService svc = service();
      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "user", "sock", "user", SheetType.VIEWSHEET,
                        new EditorContext("dynamicValues", "Text1", "visible", null)));
      assertTrue(ex.getMessage().contains("dynamicValue"), ex.getMessage());
   }

   @Test
   void mintsAValidNameOnTheConcreteTypeAndKeepsTheTripleVerbatim() throws Exception {
      SheetPairingService svc = service();

      EditorContext text = svc.peek(mint(svc, "Text1", "visible")).editorContext();
      assertEquals("dynamicValue", text.kind());
      assertEquals("Text1", text.assembly());
      assertEquals("visible", text.name());

      assertNotNull(mint(svc, "Chart1", "enabled"));
      assertNotNull(mint(svc, "Chart1", "dateComparison:periodsBack"));
      assertNotNull(mint(svc, "Chart1", "chartTarget:line.value"));
      assertNotNull(mint(svc, "Chart1", "chartRegion:legend/0/title"));
      assertNotNull(mint(svc, "Chart1", "binding:x[0].column"));
      assertNotNull(mint(svc, "Gauge1", "rangeValues[1]"));
      assertNotNull(mint(svc, "List1", "source:measure"));
      assertNotNull(mint(svc, "Tree1", "source:parentIdColumn"));
      // An embedded table shares the Table vocabulary (PropertyAliases.typeKeyOf).
      assertNotNull(mint(svc, "Embedded1", "title"));
      assertNotNull(mint(svc, "Embedded1", "binding:x[0].column"));
   }

   @Test
   void refusesAWorksheetMintWithTheDesignMessage() {
      SheetPairingService svc = service();

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock", "user", SheetType.WORKSHEET,
                        new EditorContext("dynamicValue", "Text1", "visible", null)));

      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      assertEquals("kind 'dynamicValue' is not available on a worksheet yet", ex.getMessage());
   }

   @Test
   void refusesABlankMissingAndUnknownAssembly() {
      SheetPairingService svc = service();

      refused(svc, "", "visible", "requires 'assembly'");
      refused(svc, null, "visible", "requires 'assembly'");
      refused(svc, "Nope", "visible", "Assembly not found: Nope");
   }

   @Test
   void refusesAMalformedNameWithTheGrammarReason() {
      SheetPairingService svc = service();

      refused(svc, "Text1", null, "name is required");
      refused(svc, "Text1", "prop:visible", "drop the 'prop:' prefix");
      refused(svc, "Text1", "a b", "whitespace");
      refused(svc, "Text1", "format:color", "not available yet");
      refused(svc, "Text1", "bogus:x", "namespace");
   }

   @Test
   void refusesAKeyThatIsNotInTheVocabularyOfTheConcreteType() {
      SheetPairingService svc = service();

      refused(svc, "Oval1", "enabled", "'enabled' is not a property of oval");
      refused(svc, "Text1", "chartTarget:line.value", "chartTarget:line.value");
      refused(svc, "Text1", "rangeValues[0]", "rangeValues[0]");
      refused(svc, "List1", "source:idColumn", "source:idColumn");
   }

   /** One spelling of "no such runtime": a wrong owner must not tell more than a missing runtime. */
   @Test
   void aWrongOwnerGivesTheSameMessageAsAMissingRuntime() {
      RuntimeViewsheet other = mock(RuntimeViewsheet.class);
      when(other.getUser()).thenReturn(() -> "someone~;~host-org");
      SheetPairingService wrongOwner = new SheetPairingService(() -> NOW, id -> null, id -> other);
      SheetPairingService missing = new SheetPairingService(() -> NOW, id -> null, id -> null);

      for(String assembly : new String[] { "Nope", "Text1", "" }) {
         EditorContext ctx = new EditorContext("dynamicValue", assembly, "visible", null);
         PairingException mismatch = assertThrows(PairingException.class,
            () -> wrongOwner.mint("vs-1", "alice~;~host-org", "s", "alice", SheetType.VIEWSHEET,
                                  ctx));
         PairingException gone = assertThrows(PairingException.class,
            () -> missing.mint("vs-1", "alice~;~host-org", "s", "alice", SheetType.VIEWSHEET,
                               ctx));

         assertEquals(PairingException.Kind.SESSION_EXPIRED, mismatch.getKind(), assembly);
         assertEquals(gone.getKind(), mismatch.getKind(), assembly);
         assertEquals(gone.getMessage(), mismatch.getMessage(), assembly);
      }
   }

   /** Follow Focus's {@code retarget} reuses exactly this check, so it must accept and refuse alike. */
   @Test
   void theRetargetCheckAcceptsAndRefusesTheSameWay() throws Exception {
      SheetPairingService svc = service();

      svc.validateEditorContext(SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("dynamicValue", "Chart1", "title", null));
      assertThrows(PairingException.class, () -> svc.validateEditorContext(
         SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("dynamicValue", "Chart1", "prop:title", null)));
      assertThrows(PairingException.class, () -> svc.validateEditorContext(
         SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("dynamicValue", "Nope", "title", null)));
   }
}
