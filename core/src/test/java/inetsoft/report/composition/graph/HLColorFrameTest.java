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
package inetsoft.report.composition.graph;

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug 76558 / VSH-003 (reopened 2026-09-16): two highlights on the same chart mark -- one setting
 * only {@code background}, one setting only {@code foreground} -- made neither render, the bar
 * falling back to its default color. {@code HLColorFrame} resolved the mark's color from the
 * FIRST matching rule only, so a background-only rule (a no-op for a mark by design) consumed the
 * match and the foreground rule that also matched was never consulted.
 *
 * <p>Every other highlight path -- axis labels ({@code GraphGenerator.AxisHLColorFrame}) and data
 * labels ({@code HLTextSpec}) -- goes through {@code HighlightGroup.findGroup}, which merges every
 * matching rule field by field, which is why those two were unaffected by the same pair of rules.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HLColorFrameTest {
   @Test
   void aBackgroundOnlyRuleDoesNotBlankOutAForegroundRuleMatchingTheSameMark() {
      HighlightGroup group = new HighlightGroup();
      // registration order is iteration order (HighlightGroup keeps an OrderedMap per level), and
      // this is the repro's order: the colorless rule is seen first.
      group.addHighlight("TopRegionBackground", backgroundOnly());
      group.addHighlight("TopRegionForeground", foregroundOnly());

      DefaultDataSet data = data();
      HLColorFrame frame = new HLColorFrame(MEASURE, group, data);

      assertEquals(Color.RED, frame.getColor(data, MEASURE, 0),
                   "the foreground rule also matches this row and must still color the mark -- " +
                   "before the fix the background-only rule consumed the match and this was null");
   }

   @Test
   void theSamePairResolvesTheSameWayInTheOppositeRegistrationOrder() {
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("TopRegionForeground", foregroundOnly());
      group.addHighlight("TopRegionBackground", backgroundOnly());

      DefaultDataSet data = data();
      HLColorFrame frame = new HLColorFrame(MEASURE, group, data);

      assertEquals(Color.RED, frame.getColor(data, MEASURE, 0),
                   "resolution must not depend on which rule happens to be registered first");
   }

   @Test
   void aBackgroundOnlyRuleAloneStillLeavesTheMarkUncolored() {
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("TopRegionBackground", backgroundOnly());

      DefaultDataSet data = data();
      HLColorFrame frame = new HLColorFrame(MEASURE, group, data);

      assertNull(frame.getColor(data, MEASURE, 0),
                 "background remains a correct-by-design no-op for a chart mark -- HLColorFrame " +
                 "only ever reads getForeground(); this fix must not start honoring it");
   }

   @Test
   void aRowNoRuleMatchesFallsBackToTheFrameDefault() {
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("TopRegionBackground", backgroundOnly());
      group.addHighlight("TopRegionForeground", foregroundOnly());

      DefaultDataSet data = data();
      HLColorFrame frame = new HLColorFrame(MEASURE, group, data);
      frame.setDefaultColor(Color.GRAY);

      assertEquals(Color.GRAY, frame.getColor(data, MEASURE, 1),
                   "'USA West' matches neither rule, so the frame's default (the brushing " +
                   "path's non-brushed color) must still be returned");
   }

   private static TextHighlight foregroundOnly() {
      TextHighlight hl = new TextHighlight();
      hl.setName("TopRegionForeground");
      hl.setConditionGroup(topRegion());
      hl.setForeground(Color.RED);

      return hl;
   }

   private static TextHighlight backgroundOnly() {
      TextHighlight hl = new TextHighlight();
      hl.setName("TopRegionBackground");
      hl.setConditionGroup(topRegion());
      hl.setBackground(Color.BLUE);

      return hl;
   }

   private static ConditionList topRegion() {
      Condition cond = new Condition();
      cond.addValue("USA East");
      cond.setOperation(XCondition.EQUAL_TO);

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef("REGION"), cond, 0));

      return conds;
   }

   private static DefaultDataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "REGION", MEASURE },
         { "USA East", 100.0 },
         { "USA West", 50.0 },
      });
   }

   private static final String MEASURE = "SUM(REVENUE)";
}
