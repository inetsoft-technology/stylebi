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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.VSCurrentSelectionHelper;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.model.VSSelectionContainerModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OutSelectionRowHeightTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private CurrentSelectionVSAssembly container(boolean marked) {
      Viewsheet vs = new Viewsheet();
      CurrentSelectionVSAssembly assembly = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      vs.addAssembly(assembly);
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setVizMark(marked ? VizMark.MODERN_LIGHT : null);

      info.setShowCurrentSelection(true);
      info.setPixelSize(new Dimension(300, 200));
      info.setOutSelectionValue("State", "State", "CA");
      info.setOutSelectionValue("City", "City", "SF");
      return assembly;
   }

   private CurrentSelectionVSAssemblyInfo info(CurrentSelectionVSAssembly a) {
      return (CurrentSelectionVSAssemblyInfo) a.getVSAssemblyInfo();
   }

   private int modelHeight(CurrentSelectionVSAssembly a) {
      return new VSSelectionContainerModel(a, null).getDataRowHeight();
   }

   private List<Double> exportHeights(CurrentSelectionVSAssembly a) {
      List<Double> heights = new ArrayList<>();
      new Probe(heights).run(a);

      return heights;
   }

   private static final class Probe extends VSCurrentSelectionHelper {
      Probe(List<Double> heights) {
         this.heights = heights;
      }

      @Override
      protected void writeTitle(CurrentSelectionVSAssemblyInfo info) {
      }

      @Override
      protected void writeObjectBackground(CurrentSelectionVSAssemblyInfo info) {
      }

      @Override
      protected void writeOutTitle(String title, String value, Rectangle2D bounds,
                                   VSCompositeFormat format, double titleRatio, Insets padding)
      {
         heights.add(bounds.getHeight());
      }

      void run(CurrentSelectionVSAssembly assembly) {
         cHelper = Mockito.mock(CoordinateHelper.class);
         Mockito.when(cHelper.createBounds(Mockito.any(Point.class), Mockito.any(Dimension.class)))
            .thenAnswer(i -> {
               Point p = i.getArgument(0);
               Dimension d = i.getArgument(1);
               return new Rectangle2D.Double(p.x, p.y, d.width, d.height);
            });
         write(assembly);
      }

      private final List<Double> heights;
   }

   private static final String[] MODES = { "dense", "compact", "comfortable" };
   private static final int[] TIERS = { 20, 26, 30 };

   @Test
   void markedContainerRowsFollowTheTitleMatrix() {
      for(int i = 0; i < MODES.length; i++) {
         SreeEnv.setProperty("viewsheet.density", MODES[i]);
         assertEquals(TIERS[i], info(container(true)).getOutSelectionRowHeight(18), MODES[i]);
         assertEquals(TIERS[i], info(container(true)).getOutSelectionRowHeight(AssetUtil.defh), MODES[i]);
      }
   }

   @Test
   void markedBrowserAndExportBothReadTheTierValue() {
      for(int i = 0; i < MODES.length; i++) {
         SreeEnv.setProperty("viewsheet.density", MODES[i]);
         CurrentSelectionVSAssembly a = container(true);
         assertEquals(TIERS[i], modelHeight(a), "browser " + MODES[i]);
         List<Double> rows = exportHeights(a);
         assertEquals(2, rows.size(), MODES[i] + " rows");

         for(double h : rows) {
            assertEquals((double) TIERS[i], h, "export " + MODES[i]);
         }
      }
   }

   // D6: an unmarked container is unchanged on both surfaces, including their 18-vs-defh split
   @Test
   void unmarkedContainerIsUnchangedOnBothSurfacesAtEveryDensity() {
      for(String mode : MODES) {
         SreeEnv.setProperty("viewsheet.density", mode);
         CurrentSelectionVSAssembly a = container(false);
         assertEquals(18, modelHeight(a), "browser keeps its legacy 18, " + mode);
         assertEquals(7, info(a).getOutSelectionRowHeight(7), "legacy is passed through, " + mode);
         List<Double> rows = exportHeights(a);
         assertEquals(2, rows.size(), mode + " rows");

         for(double h : rows) {
            assertEquals((double) AssetUtil.defh, h, "export keeps AssetUtil.defh, " + mode);
         }
      }
   }
}
