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
package inetsoft.report.io.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An exported tree row with no fill of its own shows the object behind it. The card tint is
 * already painted once as the object background, so a row must not take it as its own fill,
 * where the row's opaque alpha would turn it solid grey.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionTreeRowFillTest {
   @Test
   void aModernRowTakesNoFillFromTheCardTint() {
      SelectionTreeVSAssemblyInfo info = tree();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedCardSurface(VizContext.of(info));
      assertTrue(info.getFormat().isDefaultCardTint(), "precondition: the seeded tint");

      List<VSCompositeFormat> rows = writeRows(info);

      assertEquals(2, rows.size());

      for(VSCompositeFormat row : rows) {
         assertNull(row.getBackground(), "the object background already paints the tint");
      }
   }

   @Test
   void aLegacyRowStillTakesTheObjectFill() {
      SelectionTreeVSAssemblyInfo info = tree();
      info.getFormat().getDefaultFormat().setBackgroundValue("#ffeedd");

      List<VSCompositeFormat> rows = writeRows(info);

      assertEquals(2, rows.size());

      for(VSCompositeFormat row : rows) {
         assertEquals(0xffeedd, row.getBackground().getRGB() & 0xFFFFFF);
      }
   }

   private static SelectionTreeVSAssemblyInfo tree() {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Tree1");
      vs.addAssembly(tree);
      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo();
      info.initDefaultFormat();
      info.setPixelSize(new Dimension(160, 100));
      info.setCompositeSelectionValue(new CompositeSelectionValue());
      return info;
   }

   /** Drive the row loop: one selected row (no format) and one unselected row (a cloned format). */
   private static List<VSCompositeFormat> writeRows(SelectionTreeVSAssemblyInfo info) {
      List<VSCompositeFormat> written = new ArrayList<>();
      VSSelectionTreeHelper helper = new VSSelectionTreeHelper() {
         @Override
         protected void writeText(Rectangle2D bounds, Rectangle2D textBounds,
                                  VSCompositeFormat format, String dispText,
                                  boolean paintBackground, int ctype, VSCompositeFormat pfmt,
                                  Insets padding)
         {
            written.add(format);
         }

         @Override
         protected void paintMeasure(SelectionValue value, Rectangle2D bounds, SelectionList slist,
                                     SelectionBaseVSAssemblyInfo info)
         {
         }
      };
      helper.boundsList = List.of(new Rectangle2D.Double(0, 0, 160, 20),
                                  new Rectangle2D.Double(0, 20, 160, 20),
                                  new Rectangle2D.Double(0, 40, 160, 20));
      SelectionValue selected = new SelectionValue("East", "East");
      selected.setState(SelectionValue.STATE_SELECTED);
      SelectionValue unselected = new SelectionValue("West", "West");

      helper.writeTree0(info, List.of(new SelectionValue(), selected, unselected));
      return written;
   }
}
