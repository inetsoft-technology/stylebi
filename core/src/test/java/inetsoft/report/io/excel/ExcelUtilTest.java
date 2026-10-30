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
package inetsoft.report.io.excel;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ExtendedDecimalFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79602: a K/M/B {@link ExtendedDecimalFormat} must produce Excel's real scaling-comma
 * number format (e.g. {@code #,##0,"K"}) instead of the literal, unscaled pattern - otherwise
 * Excel shows the raw underlying number (1000x/1,000,000x/1,000,000,000x too large) with the
 * K/M/B label still attached.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ExcelUtilTest {
   @Test
   void analyzeAddsScalingCommaForKSuffix() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0K", US);
      assertEquals("#,##0,\"K\"", ExcelUtil.analyze(format));
   }

   @Test
   void analyzeAddsScalingCommaForMSuffix() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0M", US);
      assertEquals("#,##0,,\"M\"", ExcelUtil.analyze(format));
   }

   @Test
   void analyzeAddsScalingCommaForBSuffix() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0B", US);
      assertEquals("#,##0,,,\"B\"", ExcelUtil.analyze(format));
   }

   // Bug #79602: a parenthesized-negative sub-pattern must not bypass the K/M/B scaling comma
   // on the negative branch - it previously copied the negative sub-pattern verbatim from the
   // unscaled simple pattern, so negative values would show no comma and no unit label.
   @Test
   void analyzeScalesNegativeBranchOfParenthesizedPattern() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0;(#,##0)K", US);
      assertEquals("#,##0,\"K\";\"(\"#,##0,\"K\"\")\"", ExcelUtil.analyze(format));
   }

   // the pre-existing, non-extended parenthesized-negative shortcut must be unaffected.
   @Test
   void analyzeLeavesOrdinaryParenthesizedPatternUnchanged() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0;(#,##0)", US);
      assertEquals("#,##0;(#,##0)", ExcelUtil.analyze(format));
   }

   private static final DecimalFormatSymbols US = new DecimalFormatSymbols(Locale.US);
}
