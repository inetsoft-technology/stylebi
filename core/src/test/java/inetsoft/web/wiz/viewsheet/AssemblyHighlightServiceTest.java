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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AssemblyHighlightServiceTest {
   private static DataRefModel field(String name) {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn(name);
      return field;
   }

   private static HighlightDialogModel model(HighlightModel... existing) {
      HighlightDialogModel model = new HighlightDialogModel();
      model.setFields(new DataRefModel[]{ field("Region"), field("Revenue") });
      model.setTableName("Orders");
      model.setHighlights(existing);
      return model;
   }

   private static HighlightModel existing(String name) {
      HighlightModel highlight = new HighlightModel();
      highlight.setName(name);
      highlight.setBackground("#FF0000");
      return highlight;
   }

   private static AssemblyHighlightService.Highlight highlight(String name) {
      return new AssemblyHighlightService.Highlight(
         name, null, "#ff0000",
         List.of(new ConditionVocabulary.Clause("Revenue", ">", List.of(1000), null, false)),
         false);
   }

   @Test
   void addsAHighlightWithItsConditionBuiltByTheSharedVocabulary() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");

      HighlightDialogModel posted = capture(h.highlights);
      assertEquals(1, posted.getHighlights().length);
      HighlightModel added = posted.getHighlights()[0];
      assertEquals("HighRevenue", added.getName());
      assertEquals("#FF0000", added.getBackground(), "colours normalize to #RRGGBB");
      assertNotNull(added.getVsConditionDialogModel());
      assertEquals(1, added.getVsConditionDialogModel().getConditionList().length);
   }

   @Test
   void carriesTheDialogsTableAndFieldsIntoTheEmbeddedCondition() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");

      HighlightModel added = capture(h.highlights).getHighlights()[0];
      assertEquals("Orders", added.getVsConditionDialogModel().getTableName());
      assertEquals(2, added.getVsConditionDialogModel().getFields().length);
   }

   @Test
   void keepsExistingHighlightsWhenAddingANewOne() throws Exception {
      Harness h = harness(model(existing("Old")));

      h.service.set("tok", principal(), "Table1", null, highlight("New"), false, "");

      assertEquals(2, capture(h.highlights).getHighlights().length);
   }

   /**
    * A duplicate name silently replaces an existing highlight, which is what
    * usedHighlightNames exists to prevent. Overwriting has to be asked for.
    */
   @Test
   void refusesADuplicateNameUnlessReplaceWasAskedFor() {
      Harness h = harness(model(existing("HighRevenue")));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false,
                             ""));

      assertTrue(thrown.getMessage().contains("HighRevenue"));
      assertTrue(thrown.getMessage().contains("replace:true"));
   }

   @Test
   void replacesInPlaceWhenAsked() throws Exception {
      Harness h = harness(model(existing("HighRevenue"), existing("Other")));

      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), true, "");

      HighlightDialogModel posted = capture(h.highlights);
      assertEquals(2, posted.getHighlights().length, "replacing must not add a second entry");
      assertNotNull(posted.getHighlights()[0].getVsConditionDialogModel(),
                    "the replaced one is the rebuilt highlight, in its original position");
   }

   @Test
   void matchesAnExistingNameCaseInsensitively() {
      Harness h = harness(model(existing("HighRevenue")));

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Table1", null,
                                       highlight("highrevenue"), false, ""));
   }

   // ── refusals ──────────────────────────────────────────────────────────────

   @Test
   void refusesAHighlightWithNoName() {
      Harness h = harness(model());

      assertThrows(Exception.class,
                   () -> h.service.set("tok", principal(), "Table1", null, highlight("  "), false,
                                       ""));
   }

   /** A highlight with no colour is stored and renders nothing — indistinguishable from a
    * condition that never matched. */
   @Test
   void refusesAHighlightThatSetsNoColour() {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             new AssemblyHighlightService.Highlight("Nothing", null, null,
                                                                    List.of(), false),
                             false, ""));

      assertTrue(thrown.getMessage().contains("colour"));
   }

   @Test
   void refusesAConditionOnAFieldTheAssemblyDoesNotHave() {
      Harness h = harness(model());

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Table1", null,
                             new AssemblyHighlightService.Highlight(
                                "Bad", null, "#fff",
                                List.of(new ConditionVocabulary.Clause("Profit", ">",
                                                                       List.of(1), null, false)),
                                false),
                             false, ""));

      assertTrue(thrown.getMessage().contains("Profit"));
   }

   @Test
   void refusesAnAssemblyWithNoHighlightDialog() {
      Harness h = harness(null);

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.set("tok", principal(), "Text1", null, highlight("X"), false, ""));

      assertTrue(thrown.getMessage().contains("Text1"));
   }

   // ── delete ────────────────────────────────────────────────────────────────

   @Test
   void deletesByName() throws Exception {
      Harness h = harness(model(existing("Old"), existing("Keep")));

      h.service.delete("tok", principal(), "Table1", null, "Old", "");

      HighlightDialogModel posted = capture(h.highlights);
      assertEquals(1, posted.getHighlights().length);
      assertEquals("Keep", posted.getHighlights()[0].getName());
   }

   @Test
   void deletingSomethingAbsentIsAnErrorNotANoOp() {
      Harness h = harness(model(existing("Keep")));

      Exception thrown = assertThrows(
         Exception.class,
         () -> h.service.delete("tok", principal(), "Table1", null, "Nope", ""));

      assertTrue(thrown.getMessage().contains("Nope"));
      assertTrue(thrown.getMessage().contains("Keep"), "list what is actually there");
   }

   @Test
   void deletingNeedsAName() {
      Harness h = harness(model());

      assertThrows(Exception.class,
                   () -> h.service.delete("tok", principal(), "Table1", null, null, ""));
   }

   // ── read ──────────────────────────────────────────────────────────────────

   @Test
   void listsHighlightsWithTheirConditionsAndUsedNames() throws Exception {
      HighlightDialogModel existing = model();
      existing.setUsedHighlightNames(new String[]{ "Taken" });
      Harness h = harness(existing);
      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");
      HighlightDialogModel posted = capture(h.highlights);
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(posted);

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> highlights =
         (List<Map<String, Object>>) listed.get("highlights");
      assertEquals("HighRevenue", highlights.get(0).get("name"));
      assertEquals(1, ((List<?>) highlights.get(0).get("conditions")).size());

      // Both sources, not just the dialog's own array: the highlight that is actually present
      // has to appear, or the caller is told the name is free and then refused when it uses it.
      assertEquals(List.of("HighRevenue", "Taken"), listed.get("usedNames"));
   }

   /**
    * {@code usedNames} must report a highlight that is present even when the dialog model's own
    * {@code usedHighlightNames} array is empty — which is what it is on a plain read.
    *
    * <p>Found live: an assembly carrying "HighQuantity" reported {@code usedNames: []}, so the
    * documented way to check ("call list_highlights to see what is taken") said the name was free.
    * {@link AssemblyHighlightService#set} then refused it, because its duplicate check walks
    * {@code getHighlights()} instead. The advertised signal and the enforced rule disagreed.
    */
   @Test
   void usedNamesReportsPresentHighlightsWhenTheDialogArrayIsEmpty() throws Exception {
      Harness h = harness(model());
      h.service.set("tok", principal(), "Table1", null, highlight("HighRevenue"), false, "");
      HighlightDialogModel posted = capture(h.highlights);
      posted.setUsedHighlightNames(null);
      when(h.highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                anyBoolean(), anyBoolean(), any(Principal.class)))
         .thenReturn(posted);

      Map<String, Object> listed = h.service.list("tok", principal(), "Table1", null);

      assertEquals(List.of("HighRevenue"), listed.get("usedNames"),
                   "a highlight that set() would refuse must show as taken");
   }

   /**
    * Addressing the whole assembly means row/col <b>0</b>, not null.
    *
    * <p>For a table-type assembly {@code HighlightDialogService.getHighlightDialogModel} calls
    * {@code lens.getTableDataPath(row, col)}, which NPEs on null — so list_highlights and
    * set_highlight were unusable on every table, crosstab and calc table, while working fine on a
    * chart (which never enters that branch). Zero is what the rest of that same method already
    * assumes: a few lines later it reads {@code row == null ? 0 : row}.
    */
   @Test
   void addressesTheWholeAssemblyWithZerosBecauseTableLookupNPEsOnNull() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1", null);

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(0), eq(0),
                                                   isNull(), eq(false), eq(false),
                                                   any(Principal.class));
   }

   /**
    * The controller builds a Region straight from its nullable {@code @RequestParam}s rather than
    * calling {@link AssemblyHighlightService.Region#whole()}, so normalizing only in the factory
    * left the live path still passing nulls — and still NPEing. The record itself must normalize,
    * on every construction path.
    */
   @Test
   void aRegionBuiltDirectlyWithNullsStillAddressesRowAndColZero() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(null, null, null, false, false));

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(0), eq(0),
                                                   isNull(), eq(false), eq(false),
                                                   any(Principal.class));
   }

   @Test
   void passesARegionThrough() throws Exception {
      Harness h = harness(model());

      h.service.list("tok", principal(), "Table1",
                     new AssemblyHighlightService.Region(2, 1, "Sales", false, false));

      verify(h.highlights).getHighlightDialogModel(eq("rt1"), eq("Table1"), eq(2), eq(1),
                                                   eq("Sales"), eq(false), eq(false),
                                                   any(Principal.class));
   }

   @Test
   void eachWriteIsOneCheckpoint() throws Exception {
      Harness h = harness(model());

      h.service.set("tok", principal(), "Table1", null, highlight("X"), false, "");

      verify(h.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(AssemblyHighlightService service, ViewsheetSessionService sessions,
                          HighlightDialogService highlights) {}

   private static HighlightDialogModel capture(HighlightDialogService highlights)
      throws Exception
   {
      ArgumentCaptor<HighlightDialogModel> captor =
         ArgumentCaptor.forClass(HighlightDialogModel.class);
      verify(highlights).setHighlightDialogModel(eq("rt1"), anyString(), captor.capture(),
                                                 anyString(), any(Principal.class), any());
      return captor.getValue();
   }

   private static Harness harness(HighlightDialogModel model) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      HighlightDialogService highlights = mock(HighlightDialogService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
         when(highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                                 anyBoolean(), anyBoolean(),
                                                 any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Harness(new AssemblyHighlightService(sessions, highlights), sessions, highlights);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
