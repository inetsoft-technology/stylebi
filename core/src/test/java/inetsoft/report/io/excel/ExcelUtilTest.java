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
import inetsoft.sree.SreeEnv;
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

   // Bug #80103: the unit shown depends on the cell value, so the Excel pattern must too.
   @Test
   void analyzeForValueDowngradesMillionFormat() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.#M", US);
      assertEquals("#,##0.#", ExcelUtil.analyze(format, 500.0));
      assertEquals("#,##0,.#\"K\"", ExcelUtil.analyze(format, 20250.0));
      assertEquals("#,##0,.#\"K\"", ExcelUtil.analyze(format, 317062.69));
      assertEquals("#,##0,,.#\"M\"", ExcelUtil.analyze(format, 2300872.18));
      assertEquals("#,##0,.#\"K\"", ExcelUtil.analyze(format, -317062.0));
      // M and K keep their unit on zero, as the dashboard does
      assertEquals("#,##0,,.#\"M\"", ExcelUtil.analyze(format, 0.0));
   }

   // the unit is decided from the raw value before rounding: the dashboard shows 1,000K
   @Test
   void analyzeForValueUsesRawValueAtUnitBoundary() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.#M", US);
      assertEquals("1,000K", format.format(999999.0).replace(".0", ""));
      assertEquals("#,##0,.#\"K\"", ExcelUtil.analyze(format, 999999.0));
      assertEquals("#,##0,,.#\"M\"", ExcelUtil.analyze(format, 1000000.0));
   }

   @Test
   void analyzeForValueDowngradesBillionFormat() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("$#,###B", US);
      assertEquals("\"$\"#,##0,,\"M\"", ExcelUtil.analyze(format, 2300872.18));
      assertEquals("\"$\"#,##0,\"K\"", ExcelUtil.analyze(format, 317062.69));
      assertEquals("\"$\"#,##0,\"K\"", ExcelUtil.analyze(format, 999499.0));
      assertEquals("\"$\"#,##0,,,\"B\"", ExcelUtil.analyze(format, 2.5E9));
      // no B on zero
      assertEquals("\"$\"#,##0", ExcelUtil.analyze(format, 0.0));
   }

   @Test
   void analyzeForValueDropsUnitBelowOneThousand() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("$#,###K", US);
      // the dashboard adds a decimal place when a pattern without one drops to no unit
      assertEquals("\"$\"#,###.#", ExcelUtil.analyze(format, 500.0));
      assertEquals("\"$\"#,##0,\"K\"", ExcelUtil.analyze(format, 2300872.18));
   }

   // fractional values without a unit get decimals added when the pattern has none
   @Test
   void analyzeForValueAddsDecimalsWhenNoUnitAndNoDecimalPoint() {
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("$#,###K", US);
      assertEquals("\"$\"#,###.#", ExcelUtil.analyze(format, 0.4));
   }

   @Test
   void analyzeForValueKeepsConfiguredUnitWhenAutoDowngradeDisabled() {
      
      SreeEnv.setProperty("format.auto.downgrade", "false");

      try {
         ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.#M", US);
         assertEquals("#,##0,,.#\"M\"", ExcelUtil.analyze(format, 500.0));
         assertEquals("#,##0,,.#\"M\"", ExcelUtil.analyze(format, 317062.69));
      }
      finally {
         SreeEnv.remove("format.auto.downgrade");
      }
   }

   @Test
   void analyzeForValueMatchesSingleArgForOtherFormats() {
      ExtendedDecimalFormat plain = new ExtendedDecimalFormat("#,##0.00", US);
      assertEquals(ExcelUtil.analyze(plain), ExcelUtil.analyze(plain, 500.0));
      ExtendedDecimalFormat format = new ExtendedDecimalFormat("#,##0.#M", US);
      assertEquals(ExcelUtil.analyze(format), ExcelUtil.analyze(format, "text"));
   }

   private static final DecimalFormatSymbols US = new DecimalFormatSymbols(Locale.US);
}
