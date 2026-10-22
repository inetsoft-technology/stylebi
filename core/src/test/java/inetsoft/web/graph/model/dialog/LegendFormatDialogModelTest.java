/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.graph.model.dialog;

import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSChartChromeDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
public class LegendFormatDialogModelTest {
   @Test
   public void hasCorrectBorderColor() throws Exception {
      LegendFormatDialogModel legendFormatDialogModel = new LegendFormatDialogModel();
      LegendFormatGeneralPaneModel legendFormatGeneralPaneModel = new LegendFormatGeneralPaneModel();
      legendFormatGeneralPaneModel.setPosition("");
      legendFormatDialogModel.setLegendFormatGeneralPaneModel(legendFormatGeneralPaneModel);
      LegendScalePaneModel legendScalePaneModel = new LegendScalePaneModel();
      legendFormatDialogModel.setLegendScalePaneModel(legendScalePaneModel);
      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      legendFormatDialogModel.setAliasPaneModel(aliasPaneModel);

      LegendDescriptor legendDescriptor = new LegendDescriptor();
      LegendsDescriptor legendsDescriptor = new LegendsDescriptor();
      legendFormatGeneralPaneModel.setFillColor("#ffffff");
      legendFormatDialogModel.updateLegendFormatDialogModel(new VSChartInfo(), legendsDescriptor,
                                                            legendDescriptor, null,
                                                            VizContext.ofGate());

      assertEquals(Color.WHITE, legendsDescriptor.getBorderColor());
   }

   @Test
   public void symbolRoundCornersIsWrittenToEditedLegendOnly() {
      LegendFormatDialogModel dialog = new LegendFormatDialogModel();
      LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
      pane.setPosition("");
      pane.setFillColor("#ffffff");
      pane.setSymbolRoundCorners(false);
      dialog.setLegendFormatGeneralPaneModel(pane);
      dialog.setLegendScalePaneModel(new LegendScalePaneModel());
      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      dialog.setAliasPaneModel(aliasPaneModel);

      LegendsDescriptor legendsDesc = new LegendsDescriptor();
      LegendDescriptor edited = new LegendDescriptor();
      LegendDescriptor unrelated = new LegendDescriptor();
      legendsDesc.setColorLegendDescriptor(edited);
      legendsDesc.setSizeLegendDescriptor(unrelated);

      dialog.updateLegendFormatDialogModel(new VSChartInfo(), legendsDesc, edited, null,
                                          VizContext.ofGate());

      assertFalse(edited.isSymbolRoundCorners(),
                  "the edited legend should reflect the dialog value");
      assertTrue(unrelated.isSymbolRoundCorners(),
                 "another legend on the same chart must not be affected");
   }

   @Test
   public void equalsContentDiscriminatesSymbolRoundCorners() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      a.setSymbolRoundCorners(true);
      b.setSymbolRoundCorners(false);

      assertFalse(a.equalsContent(b));

      b.setSymbolRoundCorners(true);

      assertTrue(a.equalsContent(b));
   }

   @Test
   public void aStylesheetGapLeavesTheBoxTicked() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(20, CompositeValue.Type.CSS);

      assertEquals(Boolean.TRUE,
                   LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.of(VizMark.MODERN_LIGHT)));
   }

   @Test
   public void anAuthorZeroUnticksTheBox() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setGap(0);

      assertEquals(Boolean.FALSE,
                   LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.of(VizMark.MODERN_LIGHT)));
      assertNull(LegendFormatDialogModel.gapFollowsDefault(legends, VizContext.LEGACY));
   }

   @Test
   public void aCompactChartShowsTheBuiltInValuesTicked() {
      LegendsDescriptor legends = new LegendsDescriptor();

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("compact")),
                 0, 4, 1, 4, Boolean.TRUE);
   }

   @Test
   public void aComfortableChartShowsItsDensityValues() {
      LegendsDescriptor legends = new LegendsDescriptor();

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("comfortable")),
                 1, 6, 2, 6, Boolean.TRUE);
   }

   @Test
   public void aStylesheetPaddingShowsTickedWithItsValue() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("compact")),
                 5, 5, 5, 5, Boolean.TRUE);
   }

   @Test
   public void anAuthorPaddingUnticksTheBox() {
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(3, 3, 3, 3));

      assertPane(LegendFormatDialogModel.paddingPane(legends, modernAt("compact")),
                 3, 3, 3, 3, Boolean.FALSE);
      assertNull(LegendFormatDialogModel.paddingPane(legends, VizContext.LEGACY).getFollowsDefault());
   }

   @Test
   public void okWhileTickedClearsTheAuthorTier() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);
      legends.setPadding(new Insets(3, 3, 3, 3));
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, ctx);
      pane.setFollowsDefault(true);

      ok(legends, pane, ctx);

      assertFalse(legends.hasPaddingUserValue());
      assertEquals(new Insets(5, 5, 5, 5), legends.getPadding());
   }

   @Test
   public void okWhileTickedLeavesAStylesheetPaddingStanding() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      legends.setPadding(new Insets(5, 5, 5, 5), CompositeValue.Type.CSS);

      ok(legends, LegendFormatDialogModel.paddingPane(legends, ctx), ctx);

      assertFalse(legends.hasPaddingUserValue());
      assertEquals(new Insets(5, 5, 5, 5), legends.getPadding());
   }

   @Test
   public void untickingOnCompactStoresTheShownValues() {
      VizContext ctx = modernAt("compact");
      LegendsDescriptor legends = new LegendsDescriptor();
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, ctx);
      pane.setFollowsDefault(false);

      ok(legends, pane, ctx);

      assertTrue(legends.hasPaddingUserValue());
      assertEquals(new Insets(0, 4, 1, 4), legends.getPadding());
   }

   @Test
   public void anUntickedEditIsStored() {
      VizContext ctx = modernAt("comfortable");
      LegendsDescriptor legends = new LegendsDescriptor();
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, ctx);
      pane.setTop(3);
      pane.setBottom(3);
      pane.setFollowsDefault(false);

      ok(legends, pane, ctx);

      assertEquals(new Insets(3, 6, 3, 6), legends.getPadding());
   }

   @Test
   public void aLegacyOkWithoutEditsStoresNothing() {
      LegendsDescriptor legends = new LegendsDescriptor();

      ok(legends, LegendFormatDialogModel.paddingPane(legends, VizContext.LEGACY), VizContext.LEGACY);

      assertFalse(legends.hasPaddingValue());
   }

   @Test
   public void aLegacyEditIsStored() {
      LegendsDescriptor legends = new LegendsDescriptor();
      PaddingPaneModel pane = LegendFormatDialogModel.paddingPane(legends, VizContext.LEGACY);
      pane.setTop(2);

      ok(legends, pane, VizContext.LEGACY);

      assertEquals(new Insets(2, 4, 1, 4), legends.getPadding());
   }

   @Test
   public void aRequestWithoutPaddingWritesNothing() {
      LegendsDescriptor legends = new LegendsDescriptor();

      assertDoesNotThrow(() -> ok(legends, null, modernAt("compact")));
      assertFalse(legends.hasPaddingValue());
   }

   private static void assertPane(PaddingPaneModel pane, int top, int left, int bottom, int right,
                                  Boolean followsDefault)
   {
      assertEquals(top, pane.getTop(), "top");
      assertEquals(left, pane.getLeft(), "left");
      assertEquals(bottom, pane.getBottom(), "bottom");
      assertEquals(right, pane.getRight(), "right");
      assertEquals(followsDefault, pane.getFollowsDefault(), "followsDefault");
   }

   /** Runs the dialog's OK with the given padding pane; every other field is left as shown. */
   private static void ok(LegendsDescriptor legends, PaddingPaneModel padding, VizContext ctx) {
      LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
      pane.setPosition("");
      pane.setFillColor("#ffffff");
      pane.setGap(VSChartChromeDefaults.resolveLegendGap(legends.getGap(), legends.hasGapValue(), ctx));
      pane.setPadding(padding);
      LegendFormatDialogModel dialog = new LegendFormatDialogModel();
      dialog.setLegendFormatGeneralPaneModel(pane);
      dialog.setLegendScalePaneModel(new LegendScalePaneModel());
      AliasPaneModel alias = new AliasPaneModel();
      alias.setAliasList(new ModelAlias[0]);
      dialog.setAliasPaneModel(alias);
      dialog.updateLegendFormatDialogModel(new VSChartInfo(), legends, new LegendDescriptor(), null,
                                           ctx);
   }

   /** A marked chart's context on a dashboard of the given density. */
   private static VizContext modernAt(String density) {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity(density);
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      return VizContext.of(chart.getVSAssemblyInfo());
   }
}
