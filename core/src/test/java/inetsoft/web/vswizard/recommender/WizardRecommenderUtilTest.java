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
    * <p>Only the first and last buckets need an explicit "field != null" guard — the middle,
    * range-bounded buckets ("field &gt;= X &amp;&amp; field &lt; Y") are already safe for any
    * positive X, since null-as-0 cannot satisfy a positive lower bound. Asserting the guard on
    * every branch would be a stricter (and less accurate) claim than the actual fix makes.
    */
   @Test
   void theFirstAndLastBucketsGuardAgainstNull() {
      WizardRecommenderUtil.RangeExpression built =
         WizardRecommenderUtil.buildRangeExpression(20, 20, 6, "estimated_hours");
      String expr = built.expression();
      String[] branches = expr.split("(?=\\belse\\b|^if\\()");

      assertTrue(branches[0].contains("!= null"),
         "first bucket must guard field != null: " + branches[0]);
      assertTrue(branches[branches.length - 1].contains("!= null"),
         "last bucket must guard field != null: " + branches[branches.length - 1]);
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
}
