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
package inetsoft.report.composition.region;

import inetsoft.uql.viewsheet.graph.ChartInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChartToolTipTest {
   @Test
   void defaultStyleRendersLegacyFormat() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.addTooltip(palette.put("Month"), palette.put("2026 May"));
      tip.addTooltip(palette.put("Sales"), palette.put("2.5%"));

      String out = tip.getTooltip(palette);

      assertFalse(out.contains("tt-tier-"), "Default style must not emit tier divs");
      assertTrue(out.contains("Month" + ChartToolTip.COLON + "2026 May"));
      assertTrue(out.contains(ChartToolTip.ENTER));
   }

   @Test
   void cardStyleEmitsTierDivsCappedAtThree() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("Sales"), palette.put("2.5%"));
      tip.addTooltip(palette.put("Year"), palette.put("2018 - Peak"));
      tip.addTooltip(palette.put("Series"), palette.put("2015-20 Cycle"));
      tip.addTooltip(palette.put("Region"), palette.put("West"));

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Sales"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Year"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Series"));
      // The 4th pair must reuse tier 3 (cap), not produce a tier-4 class.
      assertTrue(out.contains("<div class=\"tt-tier-3\">Region"));
      assertFalse(out.contains("tt-tier-4"), "Tiers must cap at 3");
   }

   @Test
   void cardStyleSkipsSeparatorMarkers() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip first = new ChartToolTip();
      first.setStyle(ChartInfo.TooltipStyle.CARD);
      first.addTooltip(palette.put("Sales"), palette.put("2.5%"));

      ChartToolTip second = new ChartToolTip();
      second.addTooltip(palette.put("Year"), palette.put("2018"));
      first.appendTooltips(second);

      String out = first.getTooltip(palette);

      // The (-1, -1) separator pair injected by appendTooltips must not
      // produce a stray tier div. Exactly two tier divs are expected (one
      // per real pair); a leaked separator would produce a third with
      // "null:&nbsp;null" content (since palette.get(-1) returns null).
      int tierCount = out.split("<div class=\"tt-tier-", -1).length - 1;
      assertEquals(2, tierCount, "Separator must not produce a stray tier div");
      assertFalse(out.contains("null"), "Separator must not leak null content");
      assertTrue(out.contains("<div class=\"tt-tier-1\">Sales"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Year"));
   }

   @Test
   void cardStyleSplitsCustomTooltipLinesIntoTiers() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setCustomToolTip("2.5%" + ChartToolTip.ENTER + "2018 - Peak" + ChartToolTip.ENTER + "2015-20 Cycle");

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">2.5%"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">2018 - Peak"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">2015-20 Cycle"));
   }

   @Test
   void cardStyleSkipsWhitespaceOnlyCustomTooltipLines() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setCustomToolTip("   " + ChartToolTip.ENTER + "Line1" + ChartToolTip.ENTER + "Line2");

      String out = tip.getTooltip(palette);

      int tierCount = out.split("<div class=\"tt-tier-", -1).length - 1;
      assertEquals(2, tierCount,
                   "Whitespace-only line must be skipped, leaving only the two real lines");
      assertTrue(out.contains("<div class=\"tt-tier-1\">Line1"),
                 "Real content must claim tier-1, not be demoted by a blank leading line");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Line2"));
      assertFalse(out.contains("tt-tier-3"));
   }

   @Test
   void setStyleNullDefaultsToLegacyStyle() {
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(null);
      assertEquals(ChartInfo.TooltipStyle.DEFAULT, tip.getStyle());
   }

   @Test
   void combinedCardUsesListLayout() {
      IndexedSet<String> palette = new IndexedSet<>();
      int xKey = palette.put("Cal QTR");
      int xVal = palette.put("Q3-LY");

      ChartToolTip primary = new ChartToolTip();
      primary.setStyle(ChartInfo.TooltipStyle.CARD);
      primary.setHeader(xKey, xVal);
      primary.addTooltip(palette.put("Sum"), palette.put("73.3K"));
      primary.addTooltip(palette.put("Category"), palette.put("Business"));

      ChartToolTip personal = new ChartToolTip();
      personal.addTooltip(palette.put("Sum"), palette.put("74.7K"));
      personal.addTooltip(palette.put("Category"), palette.put("Personal"));
      primary.appendTooltips(personal);

      ChartToolTip total = new ChartToolTip();
      total.addTooltip(palette.put("Total"), palette.put("936.3K"));
      primary.setStackTotalName("Total");
      primary.appendTooltips(total);

      String out = primary.getTooltip(palette);

      assertTrue(out.startsWith("<div class=\"tt-tier-1\">Sum:&nbsp;73.3K"),
                 "Combined card must promote the hovered measure to tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2 tt-subtitle\">Cal QTR:&nbsp;Q3-LY"),
                 "Shared X-dim must render as a tier-2 subtitle");
      assertTrue(out.contains("<div class=\"tt-section tt-section-first\">"),
                 "First section wrapper must carry tt-section-first for CSS targeting");
      assertTrue(out.contains("<div class=\"tt-tier-3\">Category:&nbsp;Business"),
                 "First section's remaining context drops to tier-3");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Sum:&nbsp;74.7K"),
                 "Peer section's measure stays prominent at tier-2");
      assertTrue(out.contains("<div class=\"tt-tier-3\">Category:&nbsp;Personal"),
                 "Peer section's context drops to tier-3");
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-stack-total\">Total:&nbsp;936.3K"),
                 "Stack total emits the dedicated tt-stack-total class");

      int firstClassCount = out.split("tt-section-first", -1).length - 1;
      assertEquals(1, firstClassCount, "tt-section-first must appear exactly once");
   }

   @Test
   void combinedCardWithoutExplicitHeaderFallsBackToFirstPair() {
      // Fallback layout for callers that build a combined tooltip without
      // going through PlotArea (which sets the header). Tier-1 = first added
      // pair, remaining rows at tier-2. Preserves legacy behavior.
      IndexedSet<String> paletteA = new IndexedSet<>();
      ChartToolTip dimFirst = new ChartToolTip();
      dimFirst.setStyle(ChartInfo.TooltipStyle.CARD);
      dimFirst.addTooltip(paletteA.put("Year"), paletteA.put("2024"));
      dimFirst.addTooltip(paletteA.put("Sales"), paletteA.put("100"));

      ChartToolTip secondA = new ChartToolTip();
      secondA.addTooltip(paletteA.put("Sales"), paletteA.put("200"));
      dimFirst.appendTooltips(secondA);

      assertTrue(dimFirst.getTooltip(paletteA)
            .startsWith("<div class=\"tt-tier-1\">Year:&nbsp;2024"),
         "Fallback: first added pair becomes the tier-1 header");

      IndexedSet<String> paletteB = new IndexedSet<>();
      ChartToolTip measureFirst = new ChartToolTip();
      measureFirst.setStyle(ChartInfo.TooltipStyle.CARD);
      measureFirst.addTooltip(paletteB.put("Sales"), paletteB.put("100"));
      measureFirst.addTooltip(paletteB.put("Year"), paletteB.put("2024"));

      ChartToolTip secondB = new ChartToolTip();
      secondB.addTooltip(paletteB.put("Sales"), paletteB.put("200"));
      measureFirst.appendTooltips(secondB);

      assertTrue(measureFirst.getTooltip(paletteB)
            .startsWith("<div class=\"tt-tier-1\">Sales:&nbsp;100"),
         "Fallback is positional: measure added first → measure as header");
   }

   @Test
   void combinedCardWithoutStackTotalUsesHeaderSubtitleLayout() {
      IndexedSet<String> palette = new IndexedSet<>();
      int xKey = palette.put("Cal QTR");
      int xVal = palette.put("Q3");

      ChartToolTip primary = new ChartToolTip();
      primary.setStyle(ChartInfo.TooltipStyle.CARD);
      primary.setHeader(xKey, xVal);
      primary.addTooltip(palette.put("Sum"), palette.put("100"));

      ChartToolTip second = new ChartToolTip();
      second.addTooltip(palette.put("Sum"), palette.put("200"));
      primary.appendTooltips(second);

      String out = primary.getTooltip(palette);

      int tier1 = out.split("tt-tier-1", -1).length - 1;
      int tier2 = out.split("tt-tier-2", -1).length - 1;
      int tier3 = out.split("tt-tier-3", -1).length - 1;
      assertEquals(1, tier1, "Without stack total, only the hovered measure is tier-1");
      assertEquals(2, tier2, "Subtitle + peer measure both render at tier-2");
      assertEquals(0, tier3, "No additional context → no tier-3 rows");
      assertTrue(out.contains("tt-tier-2 tt-subtitle"),
                 "Header must carry the tt-subtitle modifier");
   }

   @Test
   void combinedCardPromotesMeasureWhenHeaderSet() {
      IndexedSet<String> palette = new IndexedSet<>();
      int xKey = palette.put("Year");
      int xVal = palette.put("2024");

      ChartToolTip primary = new ChartToolTip();
      primary.setStyle(ChartInfo.TooltipStyle.CARD);
      primary.setHeader(xKey, xVal);
      primary.addTooltip(palette.put("Sales"), palette.put("500"));

      ChartToolTip peer = new ChartToolTip();
      peer.addTooltip(palette.put("Sales"), palette.put("750"));
      primary.appendTooltips(peer);

      String out = primary.getTooltip(palette);

      assertTrue(out.startsWith("<div class=\"tt-tier-1\">Sales:&nbsp;500"),
                 "Header set → hovered measure leads at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2 tt-subtitle\">Year:&nbsp;2024"),
                 "Header set → shared X-dim renders as tier-2 subtitle");
   }

   @Test
   void combinedCardEmitsHeaderOnce() {
      IndexedSet<String> palette = new IndexedSet<>();
      int xKey = palette.put("Region");
      int xVal = palette.put("West");

      ChartToolTip primary = new ChartToolTip();
      primary.setStyle(ChartInfo.TooltipStyle.CARD);
      primary.setHeader(xKey, xVal);
      primary.addTooltip(palette.put("Sales"), palette.put("100"));

      ChartToolTip peer1 = new ChartToolTip();
      peer1.addTooltip(palette.put("Sales"), palette.put("200"));
      primary.appendTooltips(peer1);

      ChartToolTip peer2 = new ChartToolTip();
      peer2.addTooltip(palette.put("Sales"), palette.put("300"));
      primary.appendTooltips(peer2);

      String out = primary.getTooltip(palette);

      int regionCount = out.split("Region:&nbsp;West", -1).length - 1;
      assertEquals(1, regionCount, "Header must be emitted exactly once across all sections");
   }
}
