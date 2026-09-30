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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77369: an onLoad/onInit script {@code TextInput2.position = [300, 40]} on an input inside
 * a bottom-tabs tab kept x but had y snapped back to the tab bar, because the #74555 re-flush in
 * {@link ViewsheetSandbox#executeView} ran unconditionally after the assembly script. A position
 * set by script is now marked ({@link VSAssemblyInfo#isPositionByScript()}) and not re-flushed,
 * while the #74555 label re-flush keeps working for children that were not moved by script.
 *
 * <p>Layout: Tab1 at (20,160), bottom tabs, 300x20. TextInput2 is its child at (20,140),
 * 100x20, so its bottom edge is flush with the tab bar.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxBottomTabsScriptPositionTest {
   private static final int TAB_Y = 160;
   private static final int CHILD_HEIGHT = 20;
   private static final int FLUSH_Y = TAB_Y - CHILD_HEIGHT;

   private Viewsheet vs;
   private TabVSAssembly tab;
   private TextInputVSAssembly input;
   private ViewsheetSandbox box;

   @BeforeEach
   void setUp() throws Exception {
      vs = new Viewsheet();

      // same wiring as ViewsheetSandboxProcessOnInitTest: the base worksheet is only
      // reachable through a full update(), so set it directly
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ViewsheetSandboxBottomTabsScriptPositionTest", null,
         Organization.getDefaultOrganizationID());
      vs.setEntry(entry);
      vs.getViewsheetInfo().setScriptEnabled(true);

      input = new TextInputVSAssembly(vs, "TextInput2");
      input.getVSAssemblyInfo().setPixelOffset(new Point(20, FLUSH_Y));
      input.getVSAssemblyInfo().setPixelSize(new Dimension(100, CHILD_HEIGHT));
      labelInfo().setLabelPositionValue(LabelInfo.TOP);
      vs.addAssembly(input);

      tab = new TabVSAssembly(vs, "Tab1");
      TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
      tabInfo.setAssemblies(new String[] { "TextInput2" });
      tabInfo.setPixelOffset(new Point(20, TAB_Y));
      tabInfo.setPixelSize(new Dimension(300, 20));
      tabInfo.setBottomTabsValue(true);
      vs.addAssembly(tab);

      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void onLoadScriptPositionIsKeptAcrossRefreshes() {
      vs.getViewsheetInfo().setOnLoad("TextInput2.position = [300, 40];");

      refresh();
      assertEquals(new Point(300, 40), inputPos());
      assertTrue(input.getVSAssemblyInfo().isPositionByScript());

      refresh();
      assertEquals(new Point(300, 40), inputPos());
   }

   @Test
   void onInitScriptPositionIsKeptAcrossRefreshes() {
      vs.getViewsheetInfo().setOnInit("TextInput2.position = [300, 40];");

      refresh();
      assertEquals(new Point(300, 40), inputPos());

      // onInit runs only once, the marker must survive resetRuntimeValues()
      refresh();
      assertEquals(new Point(300, 40), inputPos());
      assertTrue(input.getVSAssemblyInfo().isPositionByScript());
   }

   @Test
   void ownLabelScriptStillReflushesChild() {
      input.getVSAssemblyInfo().setScript("labelVisible = true;");
      int expectedY = TAB_Y - labelledHeight();

      refresh();
      assertEquals(expectedY, inputPos().y);

      refresh();
      assertEquals(expectedY, inputPos().y);
      assertEquals(20, inputPos().x);
   }

   @Test
   void conditionalLabelScriptTurningLabelOffReflushesBack() {
      input.getVSAssemblyInfo().setScript("labelVisible = true;");
      refresh();
      assertEquals(TAB_Y - labelledHeight(), inputPos().y);

      input.getVSAssemblyInfo().setScript("labelVisible = false;");
      refresh();
      assertEquals(FLUSH_Y, inputPos().y);
   }

   @Test
   void designTimeLabelIsReflushedWithoutScript() {
      labelInfo().setLabelVisibleValue("true");
      int expectedY = TAB_Y - labelledHeight();

      refresh();
      assertEquals(expectedY, inputPos().y);
   }

   @Test
   void childMovedWithContainerStaysFlushAndIsNotMarked() {
      vs.getViewsheetInfo().setOnLoad("Tab1.position = [20, 300];");

      refresh();
      assertEquals(new Point(20, 300 - CHILD_HEIGHT), inputPos());
      assertFalse(input.getVSAssemblyInfo().isPositionByScript());
      assertTrue(tab.getVSAssemblyInfo().isPositionByScript());
   }

   @Test
   void scriptPositionWinsOverOwnLabelScript() {
      vs.getViewsheetInfo().setOnLoad("TextInput2.position = [300, 40];");
      input.getVSAssemblyInfo().setScript("labelVisible = true;");

      refresh();
      assertEquals(new Point(300, 40), inputPos());

      refresh();
      assertEquals(new Point(300, 40), inputPos());
   }

   @Test
   void markerIsClonedAndTakenFromSourceWhenPositionIsCopied() {
      VSAssemblyInfo info = input.getVSAssemblyInfo();
      info.setPixelOffset(new Point(300, 40));
      info.setPositionByScript(true);

      assertTrue(info.clone().isPositionByScript());

      // copying a design info with a different position drops the marker with the position
      VSAssemblyInfo design = info.clone();
      design.setPixelOffset(new Point(20, FLUSH_Y));
      design.setPositionByScript(false);
      info.copyInfo(design);
      assertEquals(new Point(20, FLUSH_Y), info.getPixelOffset());
      assertFalse(info.isPositionByScript());
   }

   /**
    * Known edge (documented, accepted): a script placing the child exactly at the flush y is
    * still a script position, so a later own-script TOP label does not re-flush it.
    */
   @Test
   void scriptSetFlushPositionIsNotReflushedByLabel() {
      vs.getViewsheetInfo().setOnLoad("TextInput2.position = [20, " + FLUSH_Y + "];");
      input.getVSAssemblyInfo().setScript("labelVisible = true;");

      refresh();
      assertEquals(new Point(20, FLUSH_Y), inputPos());
   }

   private void refresh() {
      // CoreLifecycleService resets runtime values before each refresh
      VSUtil.resetRuntimeValues(vs, false);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   private LabelInfo labelInfo() {
      return ((InputVSAssemblyInfo) input.getVSAssemblyInfo()).getLabelInfo();
   }

   private int labelledHeight() {
      LabelInfo label = labelInfo();
      return CHILD_HEIGHT + label.getRenderedHeight() + label.getLabelGap();
   }

   private Point inputPos() {
      return input.getVSAssemblyInfo().getPixelOffset();
   }
}
