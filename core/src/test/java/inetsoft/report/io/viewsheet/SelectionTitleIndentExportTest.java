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

import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.service.ExcelVSExporter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A modern selection's exported title starts over its rows: the card inset plus the browser
 * row's leading offset, in every format that draws the inset, and nothing in a cell-grid format.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionTitleIndentExportTest {
   @Test
   void aMarkedListsTitleStartsOverItsRows() {
      assertEquals(14, html().getSelectionTitleIndent(marked(new SelectionListVSAssemblyInfo(), 8)));
      assertEquals(18, html().getSelectionTitleIndent(marked(new SelectionListVSAssemblyInfo(), 12)));
      assertEquals(22, html().getSelectionTitleIndent(marked(new SelectionListVSAssemblyInfo(), 16)));
   }

   @Test
   void aTreeTakesTheListOffset() {
      SelectionBaseVSAssemblyInfo info = marked(new SelectionTreeVSAssemblyInfo(), 16);

      assertEquals(22, html().getSelectionTitleIndent(info),
                   "no export format draws the tree's expand toggle");
   }

   @Test
   void anAuthorZeroInsetStillOffsets() {
      assertEquals(6, html().getSelectionTitleIndent(marked(new SelectionListVSAssemblyInfo(), 0)));
   }

   @Test
   void excelAndCsvTakeNoIndent() {
      SelectionBaseVSAssemblyInfo info = marked(new SelectionListVSAssemblyInfo(), 16);
      AbstractVSExporter excel = Mockito.mock(ExcelVSExporter.class, Mockito.CALLS_REAL_METHODS);

      assertEquals(0, excel.getSelectionTitleIndent(info));
      assertEquals(0, new CSVVSExporter().getSelectionTitleIndent(info));
   }

   @Test
   void anUnmarkedSelectionTakesNoIndent() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setPadding(new Insets(16, 16, 16, 16));

      assertEquals(0, html().getSelectionTitleIndent(info));
   }

   @Test
   void theHelperScalesTheIndent() {
      SelectionBaseVSAssemblyInfo info = marked(new SelectionListVSAssemblyInfo(), 16);

      assertEquals(new Insets(0, 17, 0, 0), helper().getTitlePadding(info, 0.75));
   }

   @Test
   void theHelperPassesACssTitlePaddingThrough() {
      SelectionBaseVSAssemblyInfo info = marked(new SelectionListVSAssemblyInfo(), 16);
      info.setTitlePadding(new Insets(1, 9, 1, 3), CompositeValue.Type.CSS);

      assertEquals(new Insets(1, 9, 1, 3), helper().getTitlePadding(info));
   }

   @Test
   void theHelperGivesNoPaddingWithoutAnIndent() {
      assertNull(helper().getTitlePadding(new SelectionListVSAssemblyInfo()));
   }

   @Test
   void theTreeTitleTakesTheIndent() {
      Viewsheet vs = new Viewsheet();
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Tree1");
      vs.addAssembly(tree);
      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo();
      marked(info, 16);
      info.setTitleValue("Region");
      Insets[] drawn = new Insets[1];
      VSSelectionTreeHelper helper = new VSSelectionTreeHelper() {
         @Override
         protected void writeText(Rectangle2D bounds, VSCompositeFormat fmt, String dispText,
                                  VSCompositeFormat pfmt, Insets padding)
         {
            drawn[0] = padding;
         }
      };
      helper.setExporter(html());
      helper.cHelper = Mockito.mock(CoordinateHelper.class);
      Mockito.when(helper.cHelper.getScale()).thenReturn(1.0);
      helper.boundsList = List.of(new Rectangle2D.Double(0, 0, 132, 30));

      helper.writeTitle(info);

      assertEquals(new Insets(0, 22, 0, 0), drawn[0]);
   }

   private static <T extends SelectionBaseVSAssemblyInfo> T marked(T info, int inset) {
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setPadding(new Insets(inset, inset, inset, inset));
      return info;
   }

   private static HTMLVSExporter html() {
      return new HTMLVSExporter(new ByteArrayOutputStream());
   }

   private static VSSelectionListHelper helper() {
      VSSelectionListHelper helper = new VSSelectionListHelper();
      helper.setExporter(html());
      return helper;
   }
}
