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

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.VSFormatTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static inetsoft.report.io.viewsheet.ExportCellFormatFixture.*;

/**
 * Bug #80208: a MessageFormat cell ("{0} pcs") must export its display text, and an unformatted
 * decimal must get a pattern with its own decimals instead of the fixed "0.0".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ExportUtilTextAndDecimalFormatTest {
   private VSTableLens lens;

   @BeforeEach
   void setUp() {
      // tfmt=true is what DataVSAQuery passes for table data
      lens = new VSTableLens(new VSFormatTableLens(box("Table1"), "Table1", new Base(), true));
   }

   private String pattern(int c) {
      return ExportUtil.getCellFormat(lens, 1, c, false, false);
   }

   private Object value(int c) {
      return ExportUtil.getObject(lens, 1, c, pattern(c), true);
   }

   @Test
   void messageFormatOnNumberExportsDisplayTextInAnyColumn() {
      assertEquals("12 pcs", value(MSG0));
      assertEquals("12 pcs", value(MSG1));
      assertEquals("@", pattern(MSG0));
   }

   @Test
   void messageFormatOnStringExportsDisplayText() {
      assertEquals("ID-A1", value(MSG_STR));
   }

   @Test
   void unformattedNumberKeepsTheRawValue() {
      assertEquals(12.25, value(D1225));
      assertEquals(123456789012.0, value(D_12DIG));
   }

   @Test
   void unformattedDecimalPatternHasTheValuesOwnDecimals() {
      assertEquals("0.00", pattern(D1225));
      assertEquals("0.0000", pattern(D_1234));
      assertEquals("0.000000000", pattern(D_SMALL));
      assertEquals("0.0", pattern(D_12DIG_DEC));
      assertEquals("0.000", pattern(D_NEG));
   }

   @Test
   void integerTextKeepsIntegerPattern() {
      assertEquals("0", pattern(D_INT));
      assertEquals("0", pattern(D_12DIG));
   }

   @Test
   void nanAndInfinityStayGeneral() {
      assertEquals("General", pattern(D_NAN));
      assertEquals("General", pattern(D_INF));
      // the raw value is kept so Excel writes its own number cell
      assertEquals(Double.NaN, value(D_NAN));
      assertEquals(Double.POSITIVE_INFINITY, value(D_INF));
   }

   @Test
   void floatUsesItsDisplayedDecimals() {
      assertEquals("0.0", pattern(F_01));
   }

   @Test
   void messageFormatNonExcelCallersGetDisplayTextAndCsvIsUnchanged() throws Exception {
      // excel=false now also returns the display text for a MessageFormat cell; HTML ignores a
      // String result (it renders lens.getObject) and CSV re-reads lens.getObject, so the CSV
      // text must be exactly what the dashboard shows, before and after the fix
      assertEquals("12 pcs", ExportUtil.getObject(lens, 1, MSG0, pattern(MSG0), false));

      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      inetsoft.report.io.viewsheet.excel.CSVUtil.writeTableDataAssembly(lens, out, ",");
      String[] lines = out.toString("UTF-8").split("\\r?\\n");
      assertTrue(lines[1].startsWith("\"12 pcs\",\"12 pcs\",\"ID-A1\",\"12.25\","), lines[1]);
   }

   @Test
   void sixteenDigitLongBaselineKeepsTextPatternAndRawValue() {
      // known limit, not fixed here: the pattern is "@" but the raw Long is returned and the
      // Excel writers still write a numeric cell
      assertEquals("@", pattern(L_16DIG));
      assertEquals(1234567890123456L, value(L_16DIG));
   }

   @Test
   void moreThanThirtyDecimalsFallBackToGeneral() {
      assertEquals("General", pattern(D_TINY));
   }
}
