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

/** Mint-time validation of the {@code vsHyperlinkExpression} editorContext (Bug #80459). */
@WizAgentTestSupport
class SheetPairingServiceHyperlinkTest {
   private static final long NOW = 1_000_000L;

   private static SheetPairingService service() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new ImageVSAssembly(vs, "Image1"));
      vs.addAssembly(new GaugeVSAssembly(vs, "Gauge1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      vs.addAssembly(new CrosstabVSAssembly(vs, "Crosstab1"));
      vs.addAssembly(new CalcTableVSAssembly(vs, "Calc1"));
      vs.addAssembly(new OvalVSAssembly(vs, "Oval1"));
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return new SheetPairingService(() -> NOW, id -> null, id -> "vs-1".equals(id) ? rvs : null);
   }

   private static String mint(SheetPairingService svc, String assembly, String name)
      throws PairingException
   {
      return svc.mint("vs-1", "user", "sock", "user", SheetType.VIEWSHEET,
                      new EditorContext("vsHyperlinkExpression", assembly, name, null));
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
      assertTrue(SheetPairingService.RECOGNIZED_KINDS.contains("vsHyperlinkExpression"));
      assertEquals("vsHyperlinkExpression", EditorContext.VS_HYPERLINK_EXPRESSION);

      PairingException ex = assertThrows(PairingException.class,
         () -> service().mint("vs-1", "user", "sock", "user", SheetType.VIEWSHEET,
                              new EditorContext("vsHyperlinkExpressions", "Text1", "link", null)));
      assertTrue(ex.getMessage().contains("vsHyperlinkExpression"), ex.getMessage());
   }

   @Test
   void mintsEveryRegionOnItsAssemblyTypeAndKeepsTheTripleVerbatim() throws Exception {
      SheetPairingService svc = service();

      EditorContext text = svc.peek(mint(svc, "Text1", "link")).editorContext();
      assertEquals("vsHyperlinkExpression", text.kind());
      assertEquals("Text1", text.assembly());
      assertEquals("link", text.name());

      assertNotNull(mint(svc, "Image1", "link"));
      assertNotNull(mint(svc, "Gauge1", "link"));
      assertNotNull(mint(svc, "Chart1", "title"));
      assertNotNull(mint(svc, "Chart1", "emptyPlot"));
      assertNotNull(mint(svc, "Chart1", "field[Total Sales]"));
      assertNotNull(mint(svc, "Chart1", "axis[Region]"));
      assertNotNull(mint(svc, "Chart1", "text[Sales]"));
      assertNotNull(mint(svc, "Table1", "cell[2,1]"));
      assertNotNull(mint(svc, "Crosstab1", "cell[0,0]"));
      assertNotNull(mint(svc, "Calc1", "cell[10,3]"));
   }

   @Test
   void refusesARegionThatDoesNotExistOnTheAssemblyTypeNamingTheTypes() {
      SheetPairingService svc = service();

      refused(svc, "Chart1", "cell[1,2]",
              "'cell[1,2]' is not available on a chart; it applies to calctable, crosstab, " +
              "table only");
      refused(svc, "Table1", "title",
              "'title' is not available on a table; it applies to chart only");
      refused(svc, "Table1", "link",
              "'link' is not available on a table; it applies to gauge, image, text only");
      refused(svc, "Text1", "cell[0,0]", "'cell[0,0]' is not available on a text");
      refused(svc, "Image1", "field[x]", "is not available on an image");
      refused(svc, "Oval1", "link", "is not available on an oval");
   }

   @Test
   void refusesAMalformedRegionWithTheGrammarReason() {
      SheetPairingService svc = service();

      refused(svc, "Table1", null, "requires a valid 'name': name is required");
      refused(svc, "Table1", "", "requires a valid 'name'");
      refused(svc, "Table1", "cell[1]", "requires a valid 'name'");
      refused(svc, "Table1", "cell[01,2]", "without leading zeros");
      refused(svc, "Chart1", "field[]", "needs a column name");
      refused(svc, "Chart1", "Field[x]", "unknown hyperlink region");
      refused(svc, "Chart1", "field[" + "c".repeat(201) + "]", "200");
   }

   @Test
   void refusesABlankMissingAndUnknownAssembly() {
      SheetPairingService svc = service();

      refused(svc, "", "link", "requires 'assembly'");
      refused(svc, null, "link", "requires 'assembly'");
      refused(svc, "Nope", "link", "Assembly not found: Nope");
   }

   @Test
   void refusesAWorksheetMint() {
      SheetPairingService svc = new SheetPairingService(() -> NOW,
         id -> mock(inetsoft.report.composition.RuntimeWorksheet.class), id -> null);

      PairingException ex = assertThrows(PairingException.class,
         () -> svc.mint("vs-1", "user", "sock", "user", SheetType.WORKSHEET,
                        new EditorContext("vsHyperlinkExpression", "Text1", "link", null)));
      assertEquals(PairingException.Kind.INVALID_ARGUMENT, ex.getKind(), ex.getMessage());
      assertEquals("editorContext kind 'vsHyperlinkExpression' requires a viewsheet runtime",
                   ex.getMessage());
   }

   @Test
   void aMissingRuntimeIsRefusedAsExpired() {
      SheetPairingService svc =
         new SheetPairingService(() -> NOW, id -> null, id -> null);

      PairingException ex = assertThrows(PairingException.class, () -> mint(svc, "Text1", "link"));
      assertEquals(PairingException.Kind.SESSION_EXPIRED, ex.getKind(), ex.getMessage());
   }

   /** One spelling of "no such runtime": a wrong owner must not tell more than a missing runtime. */
   @Test
   void aWrongOwnerGivesTheSameMessageAsAMissingRuntime() {
      RuntimeViewsheet other = mock(RuntimeViewsheet.class);
      when(other.getUser()).thenReturn(() -> "someone~;~host-org");
      SheetPairingService wrongOwner = new SheetPairingService(() -> NOW, id -> null, id -> other);
      SheetPairingService missing = new SheetPairingService(() -> NOW, id -> null, id -> null);

      for(String assembly : new String[] { "Nope", "Text1", "" }) {
         EditorContext ctx = new EditorContext("vsHyperlinkExpression", assembly, "link", null);
         PairingException mismatch = assertThrows(PairingException.class,
            () -> wrongOwner.mint("vs-1", "alice~;~host-org", "s", "alice", SheetType.VIEWSHEET,
                                  ctx));
         PairingException gone = assertThrows(PairingException.class,
            () -> missing.mint("vs-1", "alice~;~host-org", "s", "alice", SheetType.VIEWSHEET,
                               ctx));

         assertEquals(PairingException.Kind.SESSION_EXPIRED, mismatch.getKind(), assembly);
         assertEquals(gone.getMessage(), mismatch.getMessage(), assembly);
      }
   }

   /** Follow Focus's {@code retarget} reuses exactly this check, so it must accept and refuse alike. */
   @Test
   void theRetargetCheckAcceptsAndRefusesTheSameWay() throws Exception {
      SheetPairingService svc = service();

      svc.validateEditorContext(SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("vsHyperlinkExpression", "Table1", "cell[2,1]", null));
      assertThrows(PairingException.class, () -> svc.validateEditorContext(
         SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("vsHyperlinkExpression", "Table1", "title", null)));
      assertThrows(PairingException.class, () -> svc.validateEditorContext(
         SheetType.VIEWSHEET, "vs-1", "user",
         new EditorContext("vsHyperlinkExpression", "Nope", "link", null)));
   }
}
