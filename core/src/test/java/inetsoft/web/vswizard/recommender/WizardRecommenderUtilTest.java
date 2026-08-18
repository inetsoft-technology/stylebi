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
package inetsoft.web.vswizard.recommender;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WizardRecommenderUtil#applyNumericBin(inetsoft.uql.viewsheet.VSDimensionRef)},
 * the {@code Range@<field>} shorthand helper that lets an explicit {@code apply_binding} request a
 * numeric range-bin dimension via the same mechanism the lone-measure histogram uses.
 *
 * Uses the Spring/@SreeHome harness because constructing a VSChartDimensionRef triggers
 * GDefaults/SreeEnv init that throws ShutdownException in a plain JVM (see ChartTypeFilterPinsTest).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizardRecommenderUtilTest {
   @Test
   void applyNumericBinPrefixesRangeShorthand() {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue("price");
      WizardRecommenderUtil.applyNumericBin(ref);
      assertEquals("Range@price", ref.getGroupColumnValue());
   }

   @Test
   void applyNumericBinIsIdempotent() {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      ref.setGroupColumnValue("Range@price");
      WizardRecommenderUtil.applyNumericBin(ref);
      assertEquals("Range@price", ref.getGroupColumnValue());
   }

   @Test
   void applyNumericBinNoOpOnBlank() {
      VSChartDimensionRef ref = new VSChartDimensionRef();
      WizardRecommenderUtil.applyNumericBin(ref);
      assertNull(ref.getGroupColumnValue());
   }

   /**
    * Regression: found live sweeping openproject F1 ("distribution of estimated hours"). A bare
    * "field &lt; min" for the first bucket, and a bare unconditional "else" for the last, both
    * silently admit a null value — JS coerces null to 0 in a relational comparison, so
    * "null &lt; 20" is true. With ~18% of work packages having no estimate, the first bucket read
    * 803 instead of the true 629 — inflated by exactly the null count (verified live via an
    * explicit IS NOT NULL filter, which reproduced 629 unchanged).
    *
    * <p>Every branch is guarded, not just the first and last: a middle, range-bounded bucket
    * ("field &gt;= X &amp;&amp; field &lt; Y") is only safe from null-as-0 when X is positive, and
    * this generator is shared by every numeric histogram, not just non-negative fields like
    * estimated_hours. See {@link #aZeroSpanningMiddleBucketDoesNotSilentlyAdmitNull()}.
    */
   @Test
   void everyBucketGuardsAgainstNull() {
      WizardRecommenderUtil.RangeExpression built =
         WizardRecommenderUtil.buildRangeExpression(20, 20, 6, "estimated_hours");
      String expr = built.expression();
      String[] branches = expr.split("(?=\\belse\\b|^if\\()");

      for(String branch : branches) {
         assertTrue(branch.contains("!= null"), "every bucket must guard field != null: " + branch);
      }
   }

   @Test
   void theTrailingBucketIsNoLongerAnUnconditionalElse() {
      WizardRecommenderUtil.RangeExpression built =
         WizardRecommenderUtil.buildRangeExpression(20, 20, 6, "estimated_hours");

      // A bare "else {" with no "if" is exactly the shape that used to catch every null the
      // (now-guarded) first bucket correctly rejects -- relocating the bug rather than fixing it.
      assertFalse(built.expression().contains("else {\n"),
         "trailing bucket must not be an unconditional else: " + built.expression());
   }

   /**
    * Regression for the reviewer's finding on stylebi#4597 (larryliang-inetsoft): guarding only the
    * first and last buckets is safe for a non-negative field like estimated_hours, but not in
    * general. min=-40, inc=20 produces a bucket "0 - 20" whose range straddles zero
    * (field &gt;= 0 &amp;&amp; field &lt; 20) — a null field coerces to 0 in JS, so "0 &gt;= 0
    * &amp;&amp; 0 &lt; 20" is true, and the null would silently land in that bucket instead of
    * being excluded, for any field whose values can go negative (profit/loss, temperature delta).
    */
   @Test
   void aZeroSpanningMiddleBucketDoesNotSilentlyAdmitNull() {
      WizardRecommenderUtil.RangeExpression built =
         WizardRecommenderUtil.buildRangeExpression(-40, 20, 5, "balance");
      String expr = built.expression();

      assertTrue(expr.contains("field['balance'] >= 0 && field['balance'] < 20"),
         "expected a zero-spanning bucket in the generated expression: " + expr);
      assertTrue(expr.contains("field['balance'] != null && field['balance'] >= 0"),
         "the zero-spanning bucket must guard against null: " + expr);
   }
}
