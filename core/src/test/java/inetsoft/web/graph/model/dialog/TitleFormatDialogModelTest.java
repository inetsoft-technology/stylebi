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
package inetsoft.web.graph.model.dialog;

import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
public class TitleFormatDialogModelTest {
   @Test
   void aModernTitleShowsTheDashboardDensityDefault() {
      TitleFormatPaneModel pane =
         new TitleFormatDialogModel(new TitleDescriptor(), "X", modernAt("dense")).getTitleFormatPaneModel();

      assertEquals(2, pane.getLabelGap());
      assertEquals(Boolean.TRUE, pane.getLabelGapFollowsDefault());
   }

   @Test
   void anAuthorZeroIsShownUntickedAndKept() {
      TitleDescriptor title = new TitleDescriptor();
      title.setLabelGap(0);
      VizContext ctx = modernAt("compact");
      TitleFormatDialogModel dialog = new TitleFormatDialogModel(title, "X", ctx);

      assertEquals(0, dialog.getTitleFormatPaneModel().getLabelGap());
      assertEquals(Boolean.FALSE, dialog.getTitleFormatPaneModel().getLabelGapFollowsDefault());

      dialog.updateTitleProperties(title, ctx);

      assertTrue(title.hasLabelGapUserValue());
      assertEquals(0, title.getLabelGap());
   }

   @Test
   void aLegacyCustomTitleShowsWhatItDraws() {
      TitleDescriptor title = new TitleDescriptor();
      title.setLabelGap(20, CompositeValue.Type.CSS);

      assertEquals(20, TitleFormatDialogModel.shownLabelGap(title, VizContext.LEGACY));

      title.setTitle("Custom X Title");

      assertEquals(0, TitleFormatDialogModel.shownLabelGap(title, VizContext.LEGACY));
      assertNull(new TitleFormatDialogModel(title, "X", VizContext.LEGACY)
                    .getTitleFormatPaneModel().getLabelGapFollowsDefault());
   }

   @Test
   void aLegacyOkWithoutEditsStoresNothing() {
      TitleDescriptor title = new TitleDescriptor();
      TitleFormatDialogModel dialog = new TitleFormatDialogModel(title, "X", VizContext.LEGACY);

      dialog.updateTitleProperties(title, VizContext.LEGACY);

      assertFalse(title.hasLabelGapUserValue());
   }

   @Test
   void tickingTheBoxClearsOnlyTheGap() {
      TitleDescriptor title = new TitleDescriptor();
      title.setLabelGap(6);
      VizContext ctx = modernAt("compact");
      TitleFormatDialogModel dialog = new TitleFormatDialogModel(title, "X", ctx);
      dialog.getTitleFormatPaneModel().setLabelGapFollowsDefault(true);

      dialog.updateTitleProperties(title, ctx);

      assertFalse(title.hasLabelGapUserValue());
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
