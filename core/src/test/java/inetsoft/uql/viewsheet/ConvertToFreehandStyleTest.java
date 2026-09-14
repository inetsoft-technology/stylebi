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
package inetsoft.uql.viewsheet;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Crosstab -> freehand conversion swaps in CrosstabStyle to mimic the crosstab's alignment, but the
 * modern structure palette is overlaid only onto the shipped "Default Style"
 * (DataVSAQuery.getViewTableLens), so the swap silently opts a marked assembly out of the modern
 * (and dark) table interior. The alignment fallback in syncCellFormat covers the marked case instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConvertToFreehandStyleTest {
   private static final String CROSSTAB_STYLE = "inetsoft.report.style.CrosstabStyle";

   private CalcTableVSAssembly freehand(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.getVSAssemblyInfo().setVizMark(mark);
      calc.setTableStyleValue(TableDataVSAssemblyInfo.DEFAULT_STYLE);

      return calc;
   }

   private void sync(CalcTableVSAssembly calc) {
      TableLens source = XTableUtil.getDefaultTableLens();
      TableLens target = XTableUtil.getDefaultTableLens();
      VSLayoutTool.syncCellFormat(calc, source, target, true, null, false);
   }

   @Test
   void markedConversionKeepsDefaultStyle() {
      CalcTableVSAssembly calc = freehand(VizMark.MODERN_DARK);
      sync(calc);

      assertEquals(TableDataVSAssemblyInfo.DEFAULT_STYLE, calc.getTableStyleValue(),
                   "a marked freehand must keep the style the modern overlay recognizes");
   }

   @Test
   void unmarkedConversionStillTakesCrosstabStyle() {
      CalcTableVSAssembly calc = freehand(null);
      sync(calc);

      assertEquals(CROSSTAB_STYLE, calc.getTableStyleValue(),
                   "legacy conversion is unchanged");
   }

   @Test
   void markedConversionAlignsCellsLikeCrosstabStyle() {
      CalcTableVSAssembly calc = freehand(VizMark.MODERN_DARK);
      sync(calc);

      FormatInfo finfo = calc.getVSAssemblyInfo().getFormatInfo();
      int header = alignment(finfo, 0, 0);
      int data = alignment(finfo, 1, 1);

      assertEquals(inetsoft.report.StyleConstants.H_CENTER, header & inetsoft.report.StyleConstants.H_CENTER,
                   "header cells keep the centered alignment CrosstabStyle gave them");
      assertEquals(inetsoft.report.StyleConstants.H_RIGHT, data & inetsoft.report.StyleConstants.H_RIGHT,
                   "data cells keep the right alignment CrosstabStyle gave them");
   }

   private int alignment(FormatInfo finfo, int row, int col) {
      TableLens target = XTableUtil.getDefaultTableLens();
      VSCompositeFormat fmt =
         finfo.getFormat(target.getDescriptor().getCellDataPath(row, col));
      assertNotNull(fmt, "cell format at " + row + "," + col);

      return fmt.getUserDefinedFormat().getAlignmentValue();
   }
}
