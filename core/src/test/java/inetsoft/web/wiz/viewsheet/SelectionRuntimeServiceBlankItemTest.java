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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.CompositeSelectionValue;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.web.viewsheet.event.ApplySelectionListEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * bug-79195: a selection list's blank item has value {@code null} (label {@code ""}). The viewer
 * addresses it as null, so {@code set_selection} must too, a {@code ""} spelling must reach it,
 * and the readback behind the replace diff and {@code deselect} must keep it as null or the item
 * can be selected but never un-selected.
 *
 * <p>A real {@code SelectionList} cannot be built without a Spring context (see
 * {@link SelectionRuntimeServiceSubtreeDomainTest}), so this class boots the same minimal one and
 * reuses {@link SelectionRuntimeServiceTest}'s fixtures.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionRuntimeServiceBlankItemTest {
   /** A list of {@code East} and the blank item (label "", value null). */
   private static SelectionValue[] blankList(SelectionValue blank) {
      return new SelectionValue[]{ new SelectionValue("East", "East"), blank };
   }

   private static SelectionValue blank() {
      return new SelectionValue("", null);
   }

   private static SelectionList listOf(SelectionValue... values) {
      SelectionList list = new SelectionList();

      for(SelectionValue value : values) {
         list.addSelectionValue(value);
      }

      return list;
   }

   /** A tree whose {@code East} node has the given children (level 1). */
   private static CompositeSelectionValue eastWith(SelectionValue... children) {
      CompositeSelectionValue east = new CompositeSelectionValue("East", "East");
      SelectionList childList = new SelectionList();

      for(SelectionValue child : children) {
         child.setLevel(1);
         childList.addSelectionValue(child);
      }

      east.setSelectionList(childList);
      return east;
   }

   private static List<String> path(String... segments) {
      return Arrays.asList(segments);
   }

   @SafeVarargs
   private static List<List<String>> paths(List<String>... paths) {
      return Arrays.asList(paths);
   }

   // ── canonicalizeBlank ─────────────────────────────────────────────────────

   @Test
   void aListNeverHoldsAnEmptyStringItemSoItAliasesToTheBlank() {
      List<List<String>> out = SelectionRuntimeService.canonicalizeBlank(
         blankList(blank()), paths(path("")));

      assertEquals(paths(path((String) null)), out);
   }

   @Test
   void aRealValueAndAnAlreadyNullSegmentAreLeftAlone() {
      List<List<String>> in = paths(path("East"), path((String) null));

      assertSame(in, SelectionRuntimeService.canonicalizeBlank(blankList(blank()), in),
                 "nothing to rewrite returns the input itself");
   }

   @Test
   void theInputListsAreNeverModified() {
      List<List<String>> in = paths(path(""));
      SelectionRuntimeService.canonicalizeBlank(blankList(blank()), in);

      assertEquals(paths(path("")), in);
   }

   @Test
   void anEmptyStringIsNotRewrittenWhenThereIsNoBlankItem() {
      List<List<String>> in = paths(path(""));
      SelectionValue[] domain = { new SelectionValue("East", "East") };

      assertSame(in, SelectionRuntimeService.canonicalizeBlank(domain, in));
   }

   @Test
   void anExactEmptyStringSiblingWinsOverTheBlank() {
      // A tree level can hold both "" and null; both stay reachable, so "" must stay "".
      SelectionValue[] domain = { new SelectionValue("", ""), blank() };
      List<List<String>> in = paths(path(""), path((String) null));

      assertSame(in, SelectionRuntimeService.canonicalizeBlank(domain, in));
   }

   @Test
   void aTreeBlankIsAliasedAtEveryDepthLevelByLevel() {
      SelectionValue[] domain = { eastWith(new SelectionValue("NY", "NY"), blank()), blank() };

      assertEquals(paths(path("East", null), path(null, "")),
                   SelectionRuntimeService.canonicalizeBlank(
                      domain, paths(path("East", ""), path("", ""))),
                   "the second path's top-level blank is a leaf, so its extra segment is left as " +
                   "typed for the resolvability check to refuse");
   }

   @Test
   void aNestedLevelWithBothSpellingsKeepsTheExactOne() {
      SelectionValue[] domain = {
         eastWith(new SelectionValue("", ""), blank(), new SelectionValue("NY", "NY"))
      };
      List<List<String>> in = paths(path("East", ""), path("East", null));

      assertSame(in, SelectionRuntimeService.canonicalizeBlank(domain, in));
   }

   @Test
   void nullPathsAndANullDomainPassThrough() {
      assertEquals(null, SelectionRuntimeService.canonicalizeBlank(blankList(blank()), null));

      List<List<String>> in = paths(path(""));
      assertSame(in, SelectionRuntimeService.canonicalizeBlank(null, in));
   }

   // ── selectedPaths readback ────────────────────────────────────────────────

   @Test
   void aSelectedBlankIsReportedAsNullNotAsAnEmptyString() {
      SelectionValue blank = blank();
      blank.setSelected(true);

      assertEquals(paths(path((String) null)),
                   SelectionRuntimeService.selectedPaths(blankList(blank)));
   }

   @Test
   void aSelectedLeafUnderABlankAncestorKeepsTheNullAncestor() {
      SelectionValue leaf = new SelectionValue("NY", "NY");
      leaf.setSelected(true);
      CompositeSelectionValue blankParent = new CompositeSelectionValue("", null);
      SelectionList children = new SelectionList();
      leaf.setLevel(1);
      children.addSelectionValue(leaf);
      blankParent.setSelectionList(children);
      blankParent.setSelected(true);

      assertEquals(paths(path(null, "NY")),
                   SelectionRuntimeService.selectedPaths(new SelectionValue[]{ blankParent }));
   }

   @Test
   void theSelectedTotalIsUnchangedByTheNullReadback() {
      SelectionValue blank = blank();
      blank.setSelected(true);

      assertEquals(1, SelectionRuntimeService.selectedTotal(blankList(blank), false));
   }

   // ── setSelection, end to end ──────────────────────────────────────────────

   private static SelectionListVSAssembly listAssembly(boolean single, SelectionList domain) {
      SelectionListVSAssembly assembly =
         SelectionRuntimeServiceTest.list(XConstants.SORT_ASC, single, null);
      when(assembly.getSelectionList()).thenReturn(domain);
      return assembly;
   }

   private static List<ApplySelectionListEvent> applied(SelectionRuntimeServiceTest.Harness h,
                                                       int expected)
      throws Exception
   {
      ArgumentCaptor<ApplySelectionListEvent> sent =
         ArgumentCaptor.forClass(ApplySelectionListEvent.class);
      verify(h.selections(), times(expected)).applySelection(
         anyString(), anyString(), sent.capture(), any(Principal.class), any(), anyString());
      return sent.getAllValues();
   }

   @Test
   void selectsTheBlankItemByNull() throws Exception {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank()))));

      h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Filter1",
                               paths(path((String) null)), null, null, null, null, "");

      ApplySelectionListEvent event = applied(h, 1).get(0);
      assertEquals(1, event.getValues().size());
      assertArrayEquals(new String[]{ null }, event.getValues().get(0).getValue());
   }

   @Test
   void selectsTheBlankItemByItsEmptyLabelToo() throws Exception {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank()))));

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", paths(path("")), null, null,
         null, null, "");

      assertEquals(1, result.get("valuesSelected"));
      assertArrayEquals(new String[]{ null }, applied(h, 1).get(0).getValues().get(0).getValue());
   }

   @Test
   void bothSpellingsOfTheBlankAreOneValueAfterCanonicalizing() throws Exception {
      // single-select would refuse two values, so this also shows the guard counts the canonical set
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(true, listOf(blankList(blank()))));

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1",
         paths(path(""), path((String) null)), null, null, null, null, "");

      assertEquals(1, result.get("valuesSelected"));
      assertEquals(1, applied(h, 1).get(0).getValues().size());
   }

   @Test
   void anEmptyStringStillRefusedWhenTheListHasNoBlankItem() {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(new SelectionValue("East", "East"))));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Filter1",
                                        paths(path("")), null, null, null, null, ""));

      assertTrue(e.getMessage().contains("not found: ''"), e.getMessage());
      assertTrue(e.getMessage().contains("browse_condition_values"), e.getMessage());
      verifyNoInteractions(h.selections());
   }

   @Test
   void aTypoOnAListWithABlankItemNamesTheBlankSpellingInsteadOfBrowse() {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank()))));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Filter1",
                                        paths(path("Eas")), null, null, null, null, ""));

      assertTrue(e.getMessage().contains("not found: 'Eas'"), e.getMessage());
      assertTrue(e.getMessage().contains("addressed as null"), e.getMessage());
      assertTrue(!e.getMessage().contains("browse_condition_values"), e.getMessage());
   }

   @Test
   void valuesAndDeselectNamingTheBlankBothWaysIsTheContradictionItIs() {
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank()))));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Filter1",
                                        paths(path("")), paths(path((String) null)), null, null,
                                        null, ""));

      assertTrue(e.getMessage().contains("contradiction"), e.getMessage());
      verifyNoInteractions(h.selections());
   }

   /** Un-selects the matching value when a deselect event arrives, as the real apply does. */
   private static void unselectOnDeselect(SelectionRuntimeServiceTest.Harness h,
                                          SelectionValue blank)
      throws Exception
   {
      doAnswer(invocation -> {
         ApplySelectionListEvent event = invocation.getArgument(2);

         for(ApplySelectionListEvent.Value v : event.getValues()) {
            if(!v.isSelected() && v.getValue().length == 1 && v.getValue()[0] == null) {
               blank.setSelected(false);
            }
         }

         return null;
      }).when(h.selections()).applySelection(anyString(), anyString(), any(),
                                             any(Principal.class), any(), anyString());
   }

   @Test
   void deselectNullDeselectsASelectedBlank() throws Exception {
      SelectionValue blank = blank();
      blank.setSelected(true);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank))));
      unselectOnDeselect(h, blank);

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", null,
         paths(path((String) null)), null, null, null, "");

      assertEquals(1, result.get("deselected"));
      assertEquals(0, result.get("selectedTotal"));
      assertArrayEquals(new String[]{ null }, applied(h, 1).get(0).getValues().get(0).getValue());
   }

   @Test
   void deselectEmptyStringDeselectsASelectedBlankToo() throws Exception {
      SelectionValue blank = blank();
      blank.setSelected(true);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank))));
      unselectOnDeselect(h, blank);

      Map<String, Object> result = h.service().setSelection(
         "tok", SelectionRuntimeServiceTest.principal(), "Filter1", null, paths(path("")), null,
         null, null, "");

      assertEquals(1, result.get("deselected"));
   }

   @Test
   void replacingWithEastDeselectsTheSelectedBlank() throws Exception {
      SelectionValue blank = blank();
      blank.setSelected(true);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank))));

      h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Filter1",
                               paths(path("East")), null, null, null, null, "");

      // the replace diff deselects the blank by null first, then applies East
      List<ApplySelectionListEvent> events = applied(h, 2);
      ApplySelectionListEvent deselect = events.get(0);
      assertEquals(1, deselect.getValues().size());
      assertTrue(!deselect.getValues().get(0).isSelected());
      assertArrayEquals(new String[]{ null }, deselect.getValues().get(0).getValue());
      assertArrayEquals(new String[]{ "East" }, events.get(1).getValues().get(0).getValue());
   }

   @Test
   void reselectingTheBlankDoesNotSendASpuriousDeselectOfIt() throws Exception {
      SelectionValue blank = blank();
      blank.setSelected(true);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(
         listAssembly(false, listOf(blankList(blank))));

      h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Filter1",
                               paths(path("")), null, null, null, null, "");

      List<ApplySelectionListEvent> events = applied(h, 1);
      assertTrue(events.get(0).getValues().get(0).isSelected());
   }

   @Test
   void aTreeBlankLeafIsReachedByEitherSpelling() throws Exception {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(eastWith(new SelectionValue("NY", "NY"), blank()));
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                               paths(path("East", "")), null, null, null, true, "");

      assertArrayEquals(new String[]{ "East", null },
                        applied(h, 1).get(0).getValues().get(0).getValue());
   }

   @Test
   void anIdModeTreeRefusesANullIdByNameWithoutTouchingAnything() {
      SelectionTreeVSAssembly assembly = SelectionRuntimeServiceTest.tree(0, false);
      when(assembly.isIDMode()).thenReturn(true);
      SelectionList domain = new SelectionList();
      domain.addSelectionValue(new SelectionValue("A", "A"));
      when(assembly.getSelectionList()).thenReturn(domain);
      SelectionRuntimeServiceTest.Harness h = SelectionRuntimeServiceTest.harness(assembly);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> h.service().setSelection("tok", SelectionRuntimeServiceTest.principal(), "Tree1",
                                        paths(path("A", null)), null, null, null, null, ""));

      assertTrue(e.getMessage().contains("ID-mode tree id cannot be blank"), e.getMessage());
      verifyNoInteractions(h.selections());
   }

   @Test
   void anIdModeTreeStillAcceptsRealIds() {
      assertEquals(new ArrayList<>(), SelectionRuntimeService.findUnmatchedPaths(
         new SelectionValue[]{ new SelectionValue("A", "A") }, paths(path("A")), true));
   }
}
