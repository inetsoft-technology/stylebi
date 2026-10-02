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
package inetsoft.report.io.viewsheet;

import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.service.ExcelVSExporter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A selection exports its rows inside the card inset, and a cell-grid format takes no inset. The
 * helpers are real and the exporters are real classes; the Excel exporter is the abstract class
 * with its real methods, since its concrete subclasses are not part of this module.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionExportInsetTest {
   @Test
   void theGridOriginMovesInByTheInset() {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      Rectangle bounds = contentBoundsFor(info, new Rectangle(0, 0, 132, 202));

      assertEquals(new Rectangle(16, 16, 100, 170), bounds);
   }

   @Test
   void excelTreeTakesNoInset() {
      SelectionTreeVSAssemblyInfo info = seededTree("comfortable");

      Rectangle bounds = contentBoundsFor(info, new Rectangle(0, 0, 132, 202), excelExporter());

      assertEquals(new Rectangle(0, 0, 132, 202), bounds,
                   "insetsTableCard() is false for Excel, and the tree inherits through the base");
   }

   @Test
   void csvTakesNoInsetEither() {
      Rectangle bounds = contentBoundsFor(
         seededList("comfortable"), new Rectangle(0, 0, 132, 202), new CSVVSExporter());

      assertEquals(new Rectangle(0, 0, 132, 202), bounds);
   }

   @Test
   void theRowsMoveInAndTheTitleStays() {
      Rectangle2D total = new Rectangle2D.Double(0, 0, 132, 202);
      CoordinateHelper cHelper = Mockito.mock(CoordinateHelper.class);
      Mockito.when(cHelper.getBounds(null, CoordinateHelper.ALL, true, null)).thenReturn(total);
      Rectangle2D title = new Rectangle2D.Double(0, 0, 132, 20);
      VSSelectionListHelper helper = new VSSelectionListHelper();
      helper.setExporter(new HTMLVSExporter(new ByteArrayOutputStream()));
      helper.cHelper = cHelper;
      helper.boundsList = new ArrayList<>(List.of(
         title,
         new Rectangle2D.Double(0, 20, 132, 50),
         new Rectangle2D.Double(0, 70, 132, 50),
         new Rectangle2D.Double(0, 120, 132, 50),
         new Rectangle2D.Double(0, 170, 132, 50)));

      helper.insetRowBounds(null, seededList("comfortable"));

      assertEquals(4, helper.boundsList.size(), "the last row's centre falls below the content");
      assertSame(title, helper.boundsList.get(0), "the title entry is untouched");
      assertEquals(new Rectangle2D.Double(16, 36, 100, 50), helper.boundsList.get(1));
      assertEquals(new Rectangle2D.Double(16, 136, 100, 50), helper.boundsList.get(3));
   }

   @Test
   void anUnmarkedSelectionIsUnchanged() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();

      Rectangle bounds = contentBoundsFor(info, new Rectangle(0, 0, 100, 120));

      assertEquals(new Rectangle(0, 0, 100, 120), bounds);
   }

   @Test
   void anInsetLargerThanTheAssemblyClampsAtZero() {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      Rectangle bounds = contentBoundsFor(info, new Rectangle(5, 7, 20, 30));

      assertEquals(new Rectangle(21, 23, 0, 0), bounds);
   }

   @Test
   void theInsetIsACopy() {
      SelectionListVSAssemblyInfo info = seededList("comfortable");

      Insets inset = new HTMLVSExporter(new ByteArrayOutputStream()).getSelectionCardInset(info);
      inset.left = 99;

      assertEquals(16, info.getPadding().left);
   }

   private Rectangle contentBoundsFor(SelectionBaseVSAssemblyInfo info, Rectangle bounds) {
      return contentBoundsFor(info, bounds, new HTMLVSExporter(new ByteArrayOutputStream()));
   }

   private Rectangle contentBoundsFor(SelectionBaseVSAssemblyInfo info, Rectangle bounds,
                                      AbstractVSExporter exporter)
   {
      VSSelectionListHelper helper = info instanceof SelectionTreeVSAssemblyInfo
         ? new VSSelectionTreeHelper() : new VSSelectionListHelper();
      helper.setExporter(exporter);
      return helper.getContentBounds(info, bounds).getBounds();
   }

   private AbstractVSExporter excelExporter() {
      return Mockito.mock(ExcelVSExporter.class, Mockito.CALLS_REAL_METHODS);
   }

   // the tier values the seed writes; the seed itself is covered by SelectionCardInsetSeedTest
   private SelectionListVSAssemblyInfo seededList(String density) {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPadding(tierInset(density));
      return info;
   }

   private SelectionTreeVSAssemblyInfo seededTree(String density) {
      SelectionTreeVSAssemblyInfo info = new SelectionTreeVSAssemblyInfo();
      info.setPadding(tierInset(density));
      return info;
   }

   private Insets tierInset(String density) {
      int px = "comfortable".equals(density) ? 16 : "compact".equals(density) ? 12 : 8;
      return new Insets(px, px, px, px);
   }
}
