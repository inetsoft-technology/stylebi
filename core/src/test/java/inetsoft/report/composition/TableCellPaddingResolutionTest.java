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
package inetsoft.report.composition;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lens resolves a cell's content inset and a row's growth from one rule. With no
 * CSSTableStyle in the chain - the common case, and the one this feature creates - both come
 * from the assembly's own seeded padding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableCellPaddingResolutionTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void cellInsetsFallBackToTheSeededPadding() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertNull(lens.getInsets(0, 0), "no CSSTableStyle, so nothing from the chain");
      assertEquals(new Insets(4, 6, 4, 6), lens.getCellInsets(0, 0, markedTable("compact")));
   }

   @Test
   void cellInsetsAreNullForAnUnmarkedTable() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertNull(lens.getCellInsets(0, 0, unmarkedTable()));
   }

   @Test
   void cellInsetsSurviveANullInfo() {
      // the browser cell model calls this for non-table assemblies too
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertNull(lens.getCellInsets(0, 0, null));
   }

   @Test
   void rowGrowthIsTheSeededVerticalPadding() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertEquals(0, lens.getCSSRowPadding(0), "no stylesheet contributes anything");
      assertEquals(8, lens.getRowPadding(0, markedTable("compact")), "4 top + 4 bottom");
      assertEquals(12, lens.getRowPadding(0, markedTable("comfortable")), "6 top + 6 bottom");
      assertEquals(6, lens.getRowPadding(0, markedTable("dense")), "3 top + 3 bottom");
   }

   @Test
   void rowGrowthIsZeroForAnUnmarkedTable() {
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());

      assertEquals(0, lens.getRowPadding(0, unmarkedTable()));
   }

   @Test
   void authorTypedRowHeightSurvivesASaveAndReopen() {
      // Review Focus 1: the composer stores a resize as a CONTENT height by subtracting the
      // padding (ComposerVSTableService:958/969) and the render adds it back
      // (BaseTableService:492). Both must read the SAME source, or the height drifts by
      // 2 * padding-y on every round trip. Driving both sides off the real resolver is what
      // makes this test fail if one of the two call sites is missed.
      VSTableLens lens = new VSTableLens(XTableUtil.getDefaultTableLens());
      TableVSAssemblyInfo info = markedTable("compact");
      int typed = 40;

      int stored = Math.max(0, typed - lens.getRowPadding(lens.getHeaderRowCount(), info));
      int rendered = stored + lens.getRowPadding(lens.getHeaderRowCount(), info);

      assertEquals(typed, rendered);
      assertEquals(32, stored, "stored as a content height, not the typed one");
   }

   // seedChromeDefaults is protected and this test is in a different package, so the value is
   // set directly at the tier the seed would have written. cellPaddingForMode is package-private
   // to inetsoft.uql.viewsheet.internal, so the public cellPadding(VizContext) resolves the same
   // matrix here. The seed itself is covered by TableCellPaddingSeedTest; this test is about
   // the lens.
   private TableVSAssemblyInfo markedTable(String density) {
      SreeEnv.setProperty("viewsheet.density", density);
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(VSDensityDefaults.cellPadding(VizContext.of(VizMark.MODERN_LIGHT)),
                          CompositeValue.Type.DEFAULT);
      return info;
   }

   private TableVSAssemblyInfo unmarkedTable() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setCellPadding(null, CompositeValue.Type.DEFAULT);
      return info;
   }
}
