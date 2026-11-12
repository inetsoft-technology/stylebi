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

/** Mint-time validation of the vsConditionValue editorContext (Bug #80459). */
@WizAgentTestSupport
class SheetPairingServiceVsConditionTest {
   private static final long NOW = 1_000_000L;

   private static SheetPairingService service() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new EmbeddedTableVSAssembly(vs, "Embedded1"));
      vs.addAssembly(new SelectionListVSAssembly(vs, "List1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return new SheetPairingService(() -> NOW, id -> null, id -> "vs-1".equals(id) ? rvs : null);
   }

   private static String mint(SheetPairingService svc, String assembly, String name)
      throws PairingException
   {
      return svc.mint("vs-1", "user", "sock", "user", SheetType.VIEWSHEET,
                      new EditorContext("vsConditionValue", assembly, name, null));
   }

   private static void assertRefused(SheetPairingService svc, String assembly, String name,
                                     String fragment)
   {
      PairingException ex = assertThrows(PairingException.class, () -> mint(svc, assembly, name),
                                         assembly + "/" + name);
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      assertTrue(ex.getMessage().contains(fragment), ex.getMessage());
   }

   @Test
   void theKindIsRecognized() {
      assertTrue(SheetPairingService.RECOGNIZED_KINDS.contains("vsConditionValue"));
   }

   @Test
   void mintsAFilterOnATableAndAHighlightOnAChartOrText() throws Exception {
      SheetPairingService svc = service();

      assertEquals("filter", svc.peek(mint(svc, "Table1", "filter")).editorContext().name());
      assertEquals("highlight:Hi",
                   svc.peek(mint(svc, "Chart1", "highlight:Hi")).editorContext().name());
      assertNotNull(mint(svc, "Text1", "highlight:Hi"));
      assertNotNull(mint(svc, "Table1", "highlight:Hi"));
   }

   @Test
   void refusesABadNameAndNamesTheForms() {
      SheetPairingService svc = service();

      for(String bad : new String[] { null, "", "Filter", "highlight:", "x" }) {
         assertRefused(svc, "Table1", bad, "\"filter\" or \"highlight:<highlight name>\"");
      }
   }

   @Test
   void refusesABlankAndAnUnknownAssembly() {
      SheetPairingService svc = service();

      assertRefused(svc, "", "filter", "requires 'assembly'");
      assertRefused(svc, "Nope", "filter", "Assembly not found: Nope");
   }

   @Test
   void refusesAFilterOnAnEmbeddedTableAndAHighlightOnASelectionList() {
      SheetPairingService svc = service();

      assertRefused(svc, "Embedded1", "filter", "has no condition dialog");
      assertRefused(svc, "List1", "highlight:Hi", "has no highlight dialog");
   }

   /** Follow Focus's {@code retarget} reuses exactly this check, so it must accept and refuse alike. */
   @Test
   void theRetargetCheckAcceptsAndRefusesTheSameWay() throws Exception {
      SheetPairingService svc = service();

      svc.validateEditorContext(SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("vsConditionValue", "Chart1", "highlight:Hi", null));
      assertThrows(PairingException.class, () -> svc.validateEditorContext(
         SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("vsConditionValue", "Chart1", "highlight:", null)));
   }

   @Test
   void refusesAWorksheetRuntime() {
      SheetPairingService svc = service();

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("ws-1", "user", "sock", "user", SheetType.WORKSHEET,
                        new EditorContext("vsConditionValue", "Table1", "filter", null)));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
   }

   @Test
   void aWrongOwnerStillGetsTheSameExpiredShapeWithNoExistenceOracle() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getUser()).thenReturn(() -> "someone~;~host-org");
      SheetPairingService svc = new SheetPairingService(() -> NOW, id -> null, id -> rvs);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "alice~;~host-org", "s", "alice", SheetType.VIEWSHEET,
                        new EditorContext("vsConditionValue", "Nope", "filter", null)));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind());
   }
}
