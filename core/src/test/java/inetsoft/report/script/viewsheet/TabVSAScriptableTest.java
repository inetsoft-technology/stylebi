/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.LabelInfo;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TabVSAScriptableTest {
   private Viewsheet viewsheet;
   private ViewsheetSandbox viewsheetSandbox;
   private TabVSAScriptable tabVSAScriptable;
   private TabVSAssemblyInfo tabVSAssemblyInfo;
   private TabVSAssembly tabVSAssembly;

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      tabVSAssembly = new TabVSAssembly();
      tabVSAssemblyInfo = (TabVSAssemblyInfo) tabVSAssembly.getVSAssemblyInfo();
      tabVSAssemblyInfo.setName("Tab1");
      viewsheet.addAssembly(tabVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      tabVSAScriptable = new TabVSAScriptable(viewsheetSandbox);
      tabVSAScriptable.setAssembly("Tab1");
   }

   @Test
   void testGetClassName() {
      assertEquals("TabVSA", tabVSAScriptable.getClassName());
   }

   @Test
   void testAddProperties() {
      tabVSAScriptable.addProperties();
      assertEquals(true, tabVSAScriptable.getMember("visible"));
   }

   @Test
   void testGetSetSelectedValue() {
      tabVSAScriptable.setSelectedValue("value1");
      assertEquals("value1", tabVSAScriptable.getSelected());
      tabVSAScriptable.setSelectedValue("value2.value3");
      assertEquals("value3", tabVSAScriptable.getSelected());
   }

   @Test
   void testGetSetSelectedIndex() {
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1", "Gauge1", "Chart1"});
      assertEquals(-1, tabVSAScriptable.getSelectedIndex());
      tabVSAScriptable.setSelectedIndex(1);
      assertEquals(1, tabVSAScriptable.getSelectedIndex());
      tabVSAScriptable.setSelectedIndexValue(2);
      assertEquals(2, tabVSAScriptable.getSelectedIndex());

      //invalid index
      tabVSAScriptable.setSelectedIndex(-3);
      assertEquals(0, tabVSAScriptable.getSelectedIndex());

      RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
         tabVSAScriptable.setSelectedIndex(3);
      });
      assertEquals("Index 3 out of bounds for length 3", runtimeException.getMessage());
   }

   @Test
   void testSetSize() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1", "Gauge1"});
      Dimension size1 = new Dimension(180, 70);
      tabVSAScriptable.setSize(size1);
      assertEquals(size1, tabVSAScriptable.getSize());
   }

   @Test
   void testSetSizeTopTabsShiftsChildrenY() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 50));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      // Grow height by 20; in top-tabs mode the child should shift down by the same amount.
      tabVSAScriptable.setSize(new Dimension(180, 50));

      assertEquals(70, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetSizeBottomTabsDoesNotShiftChildrenY() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 50));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      tabVSAssemblyInfo.setBottomTabs(true);

      // Grow height by 20; in bottom-tabs mode the child Y must remain unchanged.
      tabVSAScriptable.setSize(new Dimension(180, 50));

      assertEquals(50, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetBottomTabsRepositionsChildren() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));  // flush below tab (tab Y=30 + tabHeight=30)
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      tabVSAScriptable.setBottomTabs(true);

      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      // Tab bar should have moved below the child's bottom edge (60 + 100 = 160)
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      // Child stays in place; bottom edge (60 + 100 = 160) flush with tab top
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetBottomTabsToTopRepositionsChildren() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 30));   // child above tab bar
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 130));  // tab bar below child (30 + 100)
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      tabVSAssemblyInfo.setBottomTabs(true);

      tabVSAScriptable.setBottomTabs(false);

      assertFalse(tabVSAssemblyInfo.isBottomTabs());
      // Tab bar should have moved above the child's top edge (30 - 30 = 0)
      assertEquals(0, tabVSAssemblyInfo.getPixelOffset().y);
      // Child stays in place; top edge (30) flush with tab bottom (0 + 30 = 30)
      assertEquals(30, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetBottomTabsMultiChildFlushesShortChild() {
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly tall = new TextVSAssembly();
      tall.getVSAssemblyInfo().setName("Tall");
      tall.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      tall.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(tall);

      TextVSAssembly shortChild = new TextVSAssembly();
      shortChild.getVSAssemblyInfo().setName("Short");
      shortChild.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      shortChild.getVSAssemblyInfo().setPixelSize(new Dimension(180, 50));
      viewsheet.addAssembly(shortChild);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Tall", "Short"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      tabVSAScriptable.setBottomTabs(true);

      // tab moves below the tallest child's bottom edge (60 + 100 = 160)
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      // tall child stays in place; bottom edge (60 + 100 = 160) flush with tab top
      assertEquals(60, tall.getVSAssemblyInfo().getPixelOffset().y);
      // short child moves down so bottom edge is flush (160 - 50 = 110)
      assertEquals(110, shortChild.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSetBottomTabsAfterBookmarkRestoreRepositionsStalePosition() throws Exception {
      // Bug #76923: a Tab whose own per-object Script re-asserts bottomTabs (e.g.
      // Tab1.bottomTabs = RadioButton1.selectedObject) was saved to a named/shared bookmark
      // with bottomTabs=true, then reopened. The boolean restored correctly but the tab bar's
      // pixel position stayed stuck at the pre-restore (top) layout, because the script's
      // setBottomTabs() guard treated the bookmark-restored value as "unchanged" and silently
      // skipped repositioning.
      when(viewsheetSandbox.isRuntime()).thenReturn(true);

      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      // stale top-tabs position, as if freshly loaded before the bookmark is applied
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      // Simulate the bookmark restore path: TabVSAssembly.parseStateContent() restores
      // state_bottomTabs=true. This never repositions -- pixel positions aren't bookmark state.
      String bookmarkStateXml = "<assembly class=\"inetsoft.uql.viewsheet.TabVSAssembly\">" +
         "<name><![CDATA[Tab1]]></name>" +
         "<state_bottomTabs>true</state_bottomTabs></assembly>";
      tabVSAssembly.parseState(parseXml(bookmarkStateXml));

      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      // position untouched by the bookmark restore -- still at the stale top-tabs y
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);

      // Tab1's own per-object Script re-runs later in the same refresh cycle and re-asserts
      // the same value the bookmark just restored.
      tabVSAScriptable.setBottomTabs(true);

      // The tab bar must actually move below the child's bottom edge (60 + 100 = 160), not
      // stay stuck at the stale y=30 from before the bookmark switch.
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSyncPendingBottomTabsPositionsRepositionsTabWithNoObjectScript() throws Exception {
      // Bug #76927, sibling of #76923: the Tab's bottomTabs is set only by the viewsheet-level
      // onInit script, so the Tab has no per-object script to re-assert it. onInit's run-once
      // guard isn't reset when switching to a named/shared bookmark (only the HOME bookmark
      // calls ViewsheetSandbox.clearInit()), so TabVSAScriptable.setBottomTabs() -- the only
      // other consumer of the pending-reposition flag -- never runs and the tab bar keeps its
      // stale pre-restore pixel position.
      TextVSAssembly tall = new TextVSAssembly();
      tall.getVSAssemblyInfo().setName("Tall");
      tall.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      tall.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(tall);

      TextVSAssembly shortChild = new TextVSAssembly();
      shortChild.getVSAssemblyInfo().setName("Short");
      shortChild.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      shortChild.getVSAssemblyInfo().setPixelSize(new Dimension(180, 50));
      viewsheet.addAssembly(shortChild);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Tall", "Short"});
      // stale top-tabs layout, as it stood before the bookmark was applied
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      String bookmarkStateXml = "<assembly class=\"inetsoft.uql.viewsheet.TabVSAssembly\">" +
         "<name><![CDATA[Tab1]]></name>" +
         "<state_bottomTabs>true</state_bottomTabs></assembly>";
      tabVSAssembly.parseState(parseXml(bookmarkStateXml));

      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      assertTrue(tabVSAssemblyInfo.isPositionNeedsSync());
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);

      // no per-object script runs this cycle; the refresh's sweep must settle the debt
      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      // tab bar moves below the lowest child's bottom edge (60 + 100 = 160)
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      // the tall child defines the extent and stays put; the short one follows the tab bar
      assertEquals(60, tall.getVSAssemblyInfo().getPixelOffset().y);
      assertEquals(110, shortChild.getVSAssemblyInfo().getPixelOffset().y);
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());
   }

   @Test
   void testSyncPendingBottomTabsPositionsRestoresTopTabs() throws Exception {
      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      // stale bottom-tabs layout: tab bar below the child
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 160));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      tabVSAssemblyInfo.setBottomTabsValue(true);

      String bookmarkStateXml = "<assembly class=\"inetsoft.uql.viewsheet.TabVSAssembly\">" +
         "<name><![CDATA[Tab1]]></name>" +
         "<state_bottomTabs>false</state_bottomTabs></assembly>";
      tabVSAssembly.parseState(parseXml(bookmarkStateXml));

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      // tab bar moves above the child's top edge (60 - 30 = 30); child flushes below it
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());
   }

   @Test
   void testSyncPendingBottomTabsPositionsLeavesTabWithoutPendingFlagAlone() {
      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      // rValue set the ordinary way (script/design) -- no reposition owed
      tabVSAssemblyInfo.setBottomTabs(true);

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      // the sweep is gated on the flag alone, so an ordinary refresh can't fight a tab
      // position the user or a script established
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testSyncPendingBottomTabsPositionsIsIdempotentAndNullSafe() throws Exception {
      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName("Text1");
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, 60));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, 100));
      viewsheet.addAssembly(child);

      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      String bookmarkStateXml = "<assembly class=\"inetsoft.uql.viewsheet.TabVSAssembly\">" +
         "<name><![CDATA[Tab1]]></name>" +
         "<state_bottomTabs>true</state_bottomTabs></assembly>";
      tabVSAssembly.parseState(parseXml(bookmarkStateXml));

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);

      // a second sweep in the same cycle (or a later refresh) must not drift the layout
      tabVSAssemblyInfo.restoreBottomTabs(true);
      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);
      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(null);
   }

   @ParameterizedTest
   @CsvSource({
      "labels, []",
      "visible, ''"
   })
   void testGetSuffix(String propertyName, String expectedValue) {
      assertEquals(expectedValue, tabVSAScriptable.getSuffix(propertyName));
   }

   // ---- Bug #77179: plain open must not reposition tabs; real restores still must ----

   /**
    * Simulates the RuntimeViewsheet constructor on a runtime open: write the INITIAL_STATE
    * bookmark from the live viewsheet, then parse it back in place twice (setEntry's
    * gotoDefaultBookmark and refresh()'s gotoDefaultBookmark).
    */
   private static VSBookmark simulateRuntimeOpen(Viewsheet vs) {
      VSBookmark ibookmark = new VSBookmark();
      ibookmark.addBookmark(VSBookmark.INITIAL_STATE, vs, VSBookmarkInfo.PRIVATE, false, true);
      ibookmark.getBookmark(VSBookmark.INITIAL_STATE, vs);
      ibookmark.getBookmark(VSBookmark.INITIAL_STATE, vs);
      return ibookmark;
   }

   private TextVSAssembly addChild(Viewsheet vs, String name, int y, int height) {
      TextVSAssembly child = new TextVSAssembly();
      child.getVSAssemblyInfo().setName(name);
      child.getVSAssemblyInfo().setPixelOffset(new Point(0, y));
      child.getVSAssemblyInfo().setPixelSize(new Dimension(180, height));
      vs.addAssembly(child);
      return child;
   }

   @Test
   void testBug77179PlainOpenDoesNotMoveNonFlushTopTabs() {
      // top-tabs Tab with a 20px gap between the tab bar (30..60) and its child (80)
      TextVSAssembly child = addChild(viewsheet, "Text1", 80, 100);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      simulateRuntimeOpen(viewsheet);

      // the INITIAL_STATE round-trip restores the value the tab already has -- nothing owed
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(80, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testBug77179PlainOpenDoesNotUndoScriptMovedBottomTabs() {
      // bottom-tabs Tab whose bar a script (e.g. onLoad) moved away from the flush position
      // (flush would be 60 + 100 = 160)
      TextVSAssembly child = addChild(viewsheet, "Text1", 60, 100);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setBottomTabsValue(true);
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 200));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      simulateRuntimeOpen(viewsheet);

      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      assertEquals(200, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testBug77179BookmarkWithDifferentValueStillRepositions() {
      TextVSAssembly child = addChild(viewsheet, "Text1", 60, 100);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      VSBookmark bookmarks = simulateRuntimeOpen(viewsheet);
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());

      // a named bookmark saved while the tab was in bottom-tabs mode
      Viewsheet saved = viewsheet.clone();
      ((TabVSAssemblyInfo) saved.getAssembly("Tab1").getVSAssemblyInfo()).setBottomTabs(true);
      bookmarks.addBookmark("bm1", saved, VSBookmarkInfo.PRIVATE, false, true);

      bookmarks.getBookmark("bm1", viewsheet);
      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      assertTrue(tabVSAssemblyInfo.isPositionNeedsSync());

      // sticky: re-parsing the same (now equal) value must not clear the owed reposition
      bookmarks.getBookmark("bm1", viewsheet);
      assertTrue(tabVSAssemblyInfo.isPositionNeedsSync());

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      assertEquals(160, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());
   }

   @Test
   void testBug77179ComposerPreviewOfDesignScriptBottomTabsRepositions() {
      TextVSAssembly child = addChild(viewsheet, "Text1", 60, 100);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      // design-time sandbox (isRuntime() == false): onInit sets bottomTabs = true
      when(viewsheetSandbox.isRuntime()).thenReturn(false);
      tabVSAScriptable.setBottomTabs(true);

      // design geometry never moves
      assertTrue(tabVSAssemblyInfo.isBottomTabs());
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);

      // ViewsheetEngine.openPreviewViewsheet: clone + resetRuntimeValues (bottomTabs rValue
      // is deliberately kept), then the preview RuntimeViewsheet's INITIAL_STATE round-trip
      Viewsheet preview = viewsheet.clone();
      VSUtil.resetRuntimeValues(preview, true);
      simulateRuntimeOpen(preview);

      TabVSAssemblyInfo previewTab =
         (TabVSAssemblyInfo) preview.getAssembly("Tab1").getVSAssemblyInfo();
      assertTrue(previewTab.isBottomTabs());
      assertTrue(previewTab.isPositionNeedsSync());

      // preview runtime sandbox: onInit re-asserts the same value, then the sweep
      ViewsheetSandbox previewBox = mock(ViewsheetSandbox.class);
      when(previewBox.getID()).thenReturn("vs1");
      when(previewBox.getViewsheet()).thenReturn(preview);
      when(previewBox.isRuntime()).thenReturn(true);
      TabVSAScriptable previewScriptable = new TabVSAScriptable(previewBox);
      previewScriptable.setAssembly("Tab1");
      previewScriptable.setBottomTabs(true);
      TabVSAssemblyInfo.syncPendingBottomTabsPositions(preview);

      assertEquals(160, previewTab.getPixelOffset().y);
      assertEquals(60, preview.getAssembly("Text1").getVSAssemblyInfo()
         .getPixelOffset().y);
      assertFalse(previewTab.isPositionNeedsSync());

      // the design viewsheet itself is untouched
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
   }

   @Test
   void testBug77179PlainOpenDoesNotMoveBottomTabsWithTopLabelInputChild() {
      // bottom-tabs Tab over a text input whose TOP label renders outside its pixel size:
      // the design layout (child 180..200, tab bar at 200) is not a fixed point of
      // repositionForBottomTabs, which counts the label height, so any open-time sweep
      // would push the tab bar down by labelRenderedHeight + labelGap
      TextInputVSAssembly input = new TextInputVSAssembly();
      TextInputVSAssemblyInfo inputInfo = (TextInputVSAssemblyInfo) input.getVSAssemblyInfo();
      inputInfo.setName("TextInput1");
      inputInfo.setPixelOffset(new Point(0, 180));
      inputInfo.setPixelSize(new Dimension(180, 20));
      inputInfo.getLabelInfo().setLabelVisibleValue("true");
      inputInfo.getLabelInfo().setLabelPositionValue(LabelInfo.TOP);
      viewsheet.addAssembly(input);

      tabVSAssemblyInfo.setAssemblies(new String[]{"TextInput1"});
      tabVSAssemblyInfo.setBottomTabsValue(true);
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 200));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      // precondition: the label really does make this layout non-flush for the sweep
      assertTrue(TabVSAssemblyInfo.getBottomTabChildHeight(inputInfo,
         inputInfo.getPixelSize()) > 20);

      simulateRuntimeOpen(viewsheet);
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());

      TabVSAssemblyInfo.syncPendingBottomTabsPositions(viewsheet);

      assertEquals(200, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(180, inputInfo.getPixelOffset().y);
   }

   @Test
   void testBug77179HomeAfterBottomTabsBookmarkReturnsToTopTabs() {
      TextVSAssembly child = addChild(viewsheet, "Text1", 60, 100);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));

      // RuntimeViewsheet ctor: originalVs == vs, INITIAL_STATE written with bottomTabs=false
      Viewsheet originalVs = viewsheet;
      VSBookmark ibookmark = simulateRuntimeOpen(originalVs);

      // switch to a named bookmark saved in bottom-tabs mode (gotoBookmark: vs.clone())
      Viewsheet saved = originalVs.clone();
      ((TabVSAssemblyInfo) saved.getAssembly("Tab1").getVSAssemblyInfo()).setBottomTabs(true);
      VSBookmark userBookmarks = new VSBookmark();
      userBookmarks.addBookmark("bm1", saved, VSBookmarkInfo.PRIVATE, false, true);

      Viewsheet live = originalVs.clone();
      userBookmarks.getBookmark("bm1", live);
      TabVSAssemblyInfo.syncPendingBottomTabsPositions(live);
      TabVSAssemblyInfo liveTab = (TabVSAssemblyInfo) live.getAssembly("Tab1").getVSAssemblyInfo();
      assertTrue(liveTab.isBottomTabs());
      assertEquals(160, liveTab.getPixelOffset().y);

      // HOME via gotoBookmark: INITIAL_STATE parsed onto originalVs.clone(), whose value and
      // positions already agree -- nothing owed, design layout comes back untouched
      Viewsheet home = ibookmark.getBookmark(VSBookmark.INITIAL_STATE, originalVs.clone());
      TabVSAssemblyInfo homeTab = (TabVSAssemblyInfo) home.getAssembly("Tab1").getVSAssemblyInfo();
      assertFalse(homeTab.isBottomTabs());
      assertFalse(homeTab.isPositionNeedsSync());
      TabVSAssemblyInfo.syncPendingBottomTabsPositions(home);
      assertEquals(30, homeTab.getPixelOffset().y);
      assertEquals(60, home.getAssembly("Text1").getVSAssemblyInfo().getPixelOffset().y);

      // HOME based on the live (bottom-tabs) sheet (getOriginalBookmark: vs.clone() +
      // INITIAL_STATE): the restore changes true -> false, so the reposition is still owed
      Viewsheet homeFromLive = ibookmark.getBookmark(VSBookmark.INITIAL_STATE, live.clone());
      TabVSAssemblyInfo homeLiveTab =
         (TabVSAssemblyInfo) homeFromLive.getAssembly("Tab1").getVSAssemblyInfo();
      assertFalse(homeLiveTab.isBottomTabs());
      assertTrue(homeLiveTab.isPositionNeedsSync());
      TabVSAssemblyInfo.syncPendingBottomTabsPositions(homeFromLive);
      assertEquals(30, homeLiveTab.getPixelOffset().y);
      assertEquals(60, homeFromLive.getAssembly("Text1").getVSAssemblyInfo()
         .getPixelOffset().y);
      assertFalse(homeLiveTab.isPositionNeedsSync());

      // the design viewsheet itself was never moved
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   @Test
   void testBug77179DesignScriptRevertingToDesignValueClearsFlag() {
      TextVSAssembly child = addChild(viewsheet, "Text1", 60, 100);
      tabVSAssemblyInfo.setAssemblies(new String[]{"Text1"});
      tabVSAssemblyInfo.setPixelOffset(new Point(0, 30));
      tabVSAssemblyInfo.setPixelSize(new Dimension(180, 30));
      when(viewsheetSandbox.isRuntime()).thenReturn(false);

      // design onInit sets true (positions are laid out for the dValue false): owed
      tabVSAScriptable.setBottomTabs(true);
      assertTrue(tabVSAssemblyInfo.isPositionNeedsSync());

      // the designer edits the script back to the design value: nothing is owed any more,
      // so Preview must not sweep (and possibly move) a layout that already matches
      tabVSAScriptable.setBottomTabs(false);
      assertFalse(tabVSAssemblyInfo.isBottomTabs());
      assertFalse(tabVSAssemblyInfo.isPositionNeedsSync());

      // re-asserting a value that differs from the design value keeps it owed
      tabVSAScriptable.setBottomTabs(true);
      tabVSAScriptable.setBottomTabs(true);
      assertTrue(tabVSAssemblyInfo.isPositionNeedsSync());

      // design geometry never moves
      assertEquals(30, tabVSAssemblyInfo.getPixelOffset().y);
      assertEquals(60, child.getVSAssemblyInfo().getPixelOffset().y);
   }

   private static Element parseXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      Document doc = factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
   }
}
