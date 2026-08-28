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
package inetsoft.web.composer.vs.dialog;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PaddingFollowDefaultTest {
   @Test
   void readSendsTheResolvedInsetAndTheFlag() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      PaddingPaneModel model = read(info);

      assertEquals(12, model.getTop(), "the dialog shows the resolved card inset");
      assertTrue(model.getFollowsDefault());
   }

   @Test
   void readSendsNoFlagForAnUnmarkedChart() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();

      PaddingPaneModel model = read(info);

      assertEquals(10, model.getTop());
      assertNull(model.getFollowsDefault(), "no checkbox on legacy content");
   }

   @Test
   void anUnchangedApplyDoesNotStampTheResolvedValue() {
      // the defect this guards: the pane was shown 12, the author changed nothing, and Apply must
      // not turn that 12 into a stored author value
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      apply(read(info), info);

      assertFalse(info.isUserPadding());
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "still resolving, not pinned");
   }

   @Test
   void clearingTheCheckboxPinsWhatThePaneShows() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);

      PaddingPaneModel model = read(info);
      model.setFollowsDefault(false);
      model.setTop(4);
      model.setLeft(4);
      model.setBottom(4);
      model.setRight(4);
      apply(model, info);

      assertTrue(info.isUserPadding());
      assertEquals(new Insets(4, 4, 4, 4), info.getPadding());
   }

   @Test
   void tickingTheCheckboxRestoresTheLegacyStoredInsetAndResolvesAgain() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setUserPadding(true);
      info.setPadding(new Insets(4, 4, 4, 4));

      PaddingPaneModel model = read(info);
      model.setFollowsDefault(true);
      apply(model, info);

      assertFalse(info.isUserPadding(), "the flag is cleared");
      assertEquals(new Insets(12, 12, 12, 12), info.getPadding(),
                   "the pane now resolves the card inset");
   }

   @Test
   void anUnmarkedChartStillAcceptsAnEditedInset() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.initDefaultFormat();

      PaddingPaneModel model = read(info);
      model.setTop(6);
      model.setLeft(6);
      model.setBottom(6);
      model.setRight(6);
      apply(model, info);

      assertEquals(new Insets(6, 6, 6, 6), info.getPadding());
   }

   @Test
   void theFlagSurvivesTheDialogsMergeOntoTheLiveAssembly() {
      // the dialog edits a clone and hands it to setVSAssemblyInfo, so the flag has to travel with
      // the inset across copyInfo; without that the live info reports false, the reopened pane shows
      // the box ticked, and the next OK discards the author's inset
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo live = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      live.initDefaultFormat();
      live.setVizMark(VizMark.MODERN_LIGHT);

      ChartVSAssemblyInfo edited = (ChartVSAssemblyInfo) Tool.clone(live);
      PaddingPaneModel model = read(edited);
      model.setFollowsDefault(false);
      model.setTop(4);
      model.setLeft(4);
      model.setBottom(4);
      model.setRight(4);
      apply(model, edited);
      chart.setVSAssemblyInfo(edited);

      ChartVSAssemblyInfo merged = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();

      assertTrue(merged.isUserPadding(), "the flag crossed the merge");
      assertEquals(new Insets(4, 4, 4, 4), merged.getPadding(), "the author's inset is kept");
      assertFalse(read(merged).getFollowsDefault(), "the reopened pane shows the box cleared");
   }

   /** Mirrors ChartPropertyDialogService's read of the padding pane. */
   private static PaddingPaneModel read(ChartVSAssemblyInfo info) {
      PaddingPaneModel model = new PaddingPaneModel();
      Insets padding = info.getPadding();
      model.setTop(padding.top);
      model.setLeft(padding.left);
      model.setBottom(padding.bottom);
      model.setRight(padding.right);
      model.setFollowsDefault(info.getVizMark() == null ? null : !info.isUserPadding());
      return model;
   }

   /** Mirrors ChartPropertyDialogService's apply of the padding pane. */
   private static void apply(PaddingPaneModel model, ChartVSAssemblyInfo info) {
      Insets edited = new Insets(model.getTop(), model.getLeft(), model.getBottom(),
                                 model.getRight());
      Boolean followsDefault = model.getFollowsDefault();

      if(followsDefault == null) {
         if(!edited.equals(info.getPadding())) {
            info.setUserPadding(true);
            info.setPadding(edited);
         }
      }
      else if(followsDefault) {
         info.setUserPadding(false);
         info.setPadding(VSObjectChromeDefaults.legacyChartPadding());
      }
      else {
         info.setUserPadding(true);
         info.setPadding(edited);
      }
   }
}
