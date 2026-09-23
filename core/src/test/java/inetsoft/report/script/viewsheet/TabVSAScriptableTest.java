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
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
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
