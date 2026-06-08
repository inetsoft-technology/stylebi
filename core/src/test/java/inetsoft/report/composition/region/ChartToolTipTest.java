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
import inetsoft.uql.viewsheet.graph.ChartRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
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
   void cardMapKeepsGeoDimAtTier1WithNoSubtitle() {
      // Card-style map (G=geo dim, Color=aesthetic) as getToolTip now emits it:
      // the geo dim row leads at tier-1 and the color aesthetic follows at
      // tier-2, with no subtitle. The earlier solo-card lift pulled the geo dim
      // into the header, which promoted the color aesthetic to the headline and
      // pushed the geo dim down to a subtitle (Region before State). A header
      // set here would reproduce that regression, so assert none is lifted.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("State"), palette.put("FL"));   // geo dim (cols loop)
      tip.addTooltip(palette.put("Region"), palette.put("USA East")); // color (appendInfo)

      String out = tip.getTooltip(palette);

      assertFalse(tip.hasHeader(), "geo dim must not be lifted to a header");
      assertFalse(out.contains("tt-subtitle"), "card map carries no subtitle");
      assertTrue(out.contains("<div class=\"tt-tier-1\">State:&nbsp;FL"),
                 "geo dim leads at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Region:&nbsp;USA East"),
                 "color aesthetic follows at tier-2");
      assertTrue(out.indexOf("State:&nbsp;FL") < out.indexOf("Region:&nbsp;USA East"),
                 "State renders before Region");
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
   void cardStyleSoloEmphasizesStackTotal() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("Sum(Gross Amount)"), palette.put("994.7K"));
      tip.addTooltip(palette.put("Cal Month"), palette.put("JUL-TY"));
      tip.addTooltip(palette.put("Category"), palette.put("Personal"));
      tip.addTooltip(palette.put("Total"), palette.put("13.6M"));
      tip.setStackTotalName("Total");

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Sum(Gross Amount):&nbsp;994.7K"),
                 "Hovered measure stays at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Cal Month:&nbsp;JUL-TY"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Category:&nbsp;Personal"));
      assertTrue(out.endsWith("<div class=\"tt-tier-1 tt-stack-total\">Total:&nbsp;13.6M</div>"),
                 "Solo card must emphasize the stack total with tt-stack-total at the end");
      // The stack total must not also appear as a plain tier row.
      int totalCount = out.split("Total:&nbsp;13\\.6M", -1).length - 1;
      assertEquals(1, totalCount, "Stack total must appear exactly once");
   }

   @Test
   void groupedSoloCardWithHeaderEmphasizesStackTotal() {
      // A grouped card with a header set (e.g. candle, whose X-date heads the
      // card) renders the header as the tier-1 subtitle and emphasizes the
      // stack total. Plain solo cards set no header, so they carry no subtitle.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setHeader(palette.put("Year(Order Date)"), palette.put("2022"));
      tip.addTooltip(palette.put("Sum(Order Number)"), palette.put("398.8K"));
      tip.addTooltip(palette.put("Total"), palette.put("1.8M"));
      tip.addTooltip(palette.put("Employee"), palette.put("Sue"));
      tip.setStackTotalName("Total");

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Sum(Order Number):&nbsp;398.8K"),
                 "Hovered measure stays at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-subtitle\">Year(Order Date):&nbsp;2022"),
                 "X-dim renders as the tier-1 subtitle");
      assertTrue(out.endsWith("<div class=\"tt-tier-1 tt-stack-total\">Total:&nbsp;1.8M</div>"),
                 "Grouped solo card must emphasize the stack total at the end");
      int totalCount = out.split("Total:&nbsp;1\\.8M", -1).length - 1;
      assertEquals(1, totalCount, "Stack total must appear exactly once");
   }

   @Test
   void groupedSoloCardEmphasizesStackTotalNotLastInList() {
      // The stack total is added before aesthetics in PlotArea, so it is not the
      // last pair. findStackTotalIndex matches by name, so it is still pulled out.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(1);
      tip.addTooltip(palette.put("Sum(Order Number)"), palette.put("398.8K"));
      tip.addTooltip(palette.put("Total"), palette.put("1.8M"));
      tip.addTooltip(palette.put("Employee"), palette.put("Sue"));
      tip.setStackTotalName("Total");

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Sum(Order Number):&nbsp;398.8K"),
                 "Hovered measure stays at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Employee:&nbsp;Sue"),
                 "Aesthetic that followed the total in the list is not consumed by it");
      assertTrue(out.endsWith("<div class=\"tt-tier-1 tt-stack-total\">Total:&nbsp;1.8M</div>"),
                 "Total emphasized at the end even though it was not last in the list");
      int totalCount = out.split("Total:&nbsp;1\\.8M", -1).length - 1;
      assertEquals(1, totalCount, "Stack total must appear exactly once");
   }

   @Test
   void uniformCardEmphasizesStackTotal() {
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setUniformTier(true);
      tip.addTooltip(palette.put("Sum(Order Number)"), palette.put("398.8K"));
      tip.addTooltip(palette.put("Total"), palette.put("1.8M"));
      tip.addTooltip(palette.put("Region"), palette.put("West"));
      tip.setStackTotalName("Total");

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-2\">Sum(Order Number):&nbsp;398.8K"),
                 "Uniform rows render at tier-2");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Region:&nbsp;West"),
                 "Row after the total stays at tier-2, not consumed by it");
      assertTrue(out.endsWith("<div class=\"tt-tier-1 tt-stack-total\">Total:&nbsp;1.8M</div>"),
                 "Uniform card must emphasize the stack total at the end even when not last");
      int totalCount = out.split("Total:&nbsp;1\\.8M", -1).length - 1;
      assertEquals(1, totalCount, "Stack total must appear exactly once");
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
   void soloCardWithHeaderRendersCandleStockLayout() {
      // Candle/Stock: Close at tier-1, Date as tier-1 subtitle, OHL grouped at
      // tier-2, aesthetic dims (e.g. bullOrbear) at tier-3.
      IndexedSet<String> palette = new IndexedSet<>();
      int xKey = palette.put("Week(date)");
      int xVal = palette.put("2025-01-19");

      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setHeader(xKey, xVal);
      tip.addTooltip(palette.put("Last(close, date)"), palette.put("165.8"));
      tip.addTooltip(palette.put("First(open, date)"), palette.put("158.3"));
      tip.addTooltip(palette.put("Max(high)"), palette.put("166.5"));
      tip.addTooltip(palette.put("Min(low)"), palette.put("157.5"));
      tip.addTooltip(palette.put("bullOrbear"), palette.put("Bullish"));
      tip.setTier2GroupSize(3);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Last(close, date):&nbsp;165.8"),
                 "Close is the canonical headline price at tier-1");
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-subtitle\">Week(date):&nbsp;2025-01-19"),
                 "X-dim renders as a tier-1 subtitle under the headline");
      assertTrue(out.contains("<div class=\"tt-tier-2\">First(open, date):&nbsp;158.3"),
                 "Open stays in the tier-2 OHL group");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Max(high):&nbsp;166.5"),
                 "High stays in the tier-2 OHL group");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Min(low):&nbsp;157.5"),
                 "Low stays in the tier-2 OHL group");
      assertTrue(out.contains("<div class=\"tt-tier-3\">bullOrbear:&nbsp;Bullish"),
                 "Aesthetic dims past the OHL group drop to tier-3");
      assertFalse(out.contains("tt-tier-4"), "Tiers must cap at 3");
   }

   @Test
   void candleHeaderPicksXDimNotYDim() {
      // X=Week(date) dim, Y=bullOrbear dim. The header must be the X period dim,
      // never the Y grouping dim, regardless of element.getDims() order.
      String[] dims = { "bullOrbear", "Week(date)" }; // Y-dim first (the bug case)
      ChartRef[] xfields = { dimRef("Week(date)") };
      assertEquals("Week(date)", PlotArea.candleHeaderDim(xfields, dims));
   }

   @Test
   void candleHeaderPicksInnermostXDim() {
      String[] dims = { "Year", "Week(date)", "bullOrbear" };
      ChartRef[] xfields = { dimRef("Year"), dimRef("Week(date)") };
      assertEquals("Week(date)", PlotArea.candleHeaderDim(xfields, dims));
   }

   @Test
   void candleHeaderFallsBackToOuterXDimWhenInnermostAbsent() {
      // Week(date) is innermost in xfields but never reached the tooltip dims;
      // promote the next-outer X dim (Year), never the Y-axis dim.
      String[] dims = { "bullOrbear", "Year" };
      ChartRef[] xfields = { dimRef("Year"), dimRef("Week(date)") };
      assertEquals("Year", PlotArea.candleHeaderDim(xfields, dims));
   }

   @Test
   void candleHeaderSkipsMeasureXFields() {
      // A measure on X is not a period identifier; fall through to the X dimension.
      String[] dims = { "bullOrbear", "Week(date)" };
      ChartRef[] xfields = { measureRef(), dimRef("Week(date)") };
      assertEquals("Week(date)", PlotArea.candleHeaderDim(xfields, dims));
   }

   @Test
   void candleHeaderFallsBackToFirstDimWhenNoXDim() {
      String[] dims = { "bullOrbear" };
      ChartRef[] xfields = {}; // X has only measures or nothing
      assertEquals("bullOrbear", PlotArea.candleHeaderDim(xfields, dims));
   }

   private static ChartRef dimRef(String name) {
      ChartRef ref = mock(ChartRef.class);
      when(ref.isMeasure()).thenReturn(false);
      when(ref.getFullName()).thenReturn(name);
      return ref;
   }

   private static ChartRef measureRef() {
      ChartRef ref = mock(ChartRef.class);
      when(ref.isMeasure()).thenReturn(true);
      return ref;
   }

   @Test
   void soloCardWithHeaderRespectsTier2GroupSizeBoundary() {
      // tier2GroupSize=1 (default) keeps only one pair at tier-2 below the
      // headline; the rest fall to tier-3.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setHeader(palette.put("Year"), palette.put("2024"));
      tip.addTooltip(palette.put("Close"), palette.put("100"));
      tip.addTooltip(palette.put("Open"), palette.put("90"));
      tip.addTooltip(palette.put("High"), palette.put("110"));

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Close:&nbsp;100"));
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-subtitle\">Year:&nbsp;2024"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Open:&nbsp;90"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">High:&nbsp;110"));
   }

   @Test
   void boxSoloCardRespectsIQRTier2Boundary() {
      // Boxplot card as PlotArea builds it: Median headline, X-dim subtitle, the
      // IQR (Q1/Q3) grouped at tier-2, and Min/Max + the color aesthetic at tier-3.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setHeader(palette.put("Category"), palette.put("Business"));
      tip.addTooltip(palette.put("Median"), palette.put("15,000"));
      tip.addTooltip(palette.put("Q1"), palette.put("2,000"));
      tip.addTooltip(palette.put("Q3"), palette.put("33,750"));
      tip.addTooltip(palette.put("Min"), palette.put("125"));
      tip.addTooltip(palette.put("Max"), palette.put("60,000"));
      tip.addTooltip(palette.put("Region"), palette.put("USA East"));
      tip.setTier2GroupSize(2);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Median:&nbsp;15,000"));
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-subtitle\">Category:&nbsp;Business"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Q1:&nbsp;2,000"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Q3:&nbsp;33,750"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Min:&nbsp;125"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Max:&nbsp;60,000"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Region:&nbsp;USA East"),
                 "color aesthetic drops to tier-3");
      assertFalse(out.contains("tt-tier-4"));
   }

   @Test
   void boxNoXDimGroupsIQRWithoutSubtitle() {
      // A single box with no X dim: the IQR still groups at tier-2, but there is
      // no X-dim to lift, so no subtitle.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(2);
      tip.addTooltip(palette.put("Median"), palette.put("15,000"));
      tip.addTooltip(palette.put("Q1"), palette.put("2,000"));
      tip.addTooltip(palette.put("Q3"), palette.put("33,750"));
      tip.addTooltip(palette.put("Min"), palette.put("125"));
      tip.addTooltip(palette.put("Max"), palette.put("60,000"));

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Median:&nbsp;15,000"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Q1:&nbsp;2,000"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Q3:&nbsp;33,750"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Min:&nbsp;125"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Max:&nbsp;60,000"));
      assertFalse(out.contains("tt-subtitle"));
   }

   @Test
   void soloCardWithoutHeaderKeepsLegacyTierCap() {
      // No header → legacy path: 1, 2, 3, 3, 3...
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setTier2GroupSize(3); // Without a header this has no effect.
      tip.addTooltip(palette.put("A"), palette.put("1"));
      tip.addTooltip(palette.put("B"), palette.put("2"));
      tip.addTooltip(palette.put("C"), palette.put("3"));
      tip.addTooltip(palette.put("D"), palette.put("4"));

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">A"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">B"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">C"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">D"));
      assertFalse(out.contains("tt-subtitle"));
   }

   @Test
   void soloCardWithHeaderTier2GroupSizeZeroSkipsTier2() {
      // Partial OHL binding (only Close): no auxiliary tier-2 group, so the
      // first aesthetic after the headline drops straight to tier-3.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setHeader(palette.put("Year"), palette.put("2024"));
      tip.addTooltip(palette.put("Close"), palette.put("100"));
      tip.addTooltip(palette.put("Bullish"), palette.put("true"));
      tip.setTier2GroupSize(0);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Close:&nbsp;100"));
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-subtitle\">Year:&nbsp;2024"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">Bullish:&nbsp;true"),
                 "tier2GroupSize=0 puts the next pair directly at tier-3");
      assertFalse(out.contains("<div class=\"tt-tier-2\">"),
                  "tier2GroupSize=0 must produce no tier-2 rows");
   }

   @Test
   void soloCardWithHeaderRendersPartialOhlBinding() {
      // Stock chart bound with Close + High + Low but no Open: tier-2 group
      // shrinks accordingly and aesthetic dims still drop to tier-3.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.setHeader(palette.put("Week"), palette.put("2025-01-19"));
      tip.addTooltip(palette.put("Close"), palette.put("165.8"));
      tip.addTooltip(palette.put("High"), palette.put("166.5"));
      tip.addTooltip(palette.put("Low"), palette.put("157.5"));
      tip.addTooltip(palette.put("color"), palette.put("Bullish"));
      tip.setTier2GroupSize(2);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Close:&nbsp;165.8"));
      assertTrue(out.contains("<div class=\"tt-tier-1 tt-subtitle\">Week:&nbsp;2025-01-19"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">High:&nbsp;166.5"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Low:&nbsp;157.5"));
      assertTrue(out.contains("<div class=\"tt-tier-3\">color:&nbsp;Bullish"));
   }

   @Test
   void groupedSoloCardWithoutHeaderGroupsValuesAtTier2() {
      // Gantt: the Y-dim (task) is the tier-1 headline with no subtitle; Start
      // and End are equally-important values grouped at tier-2; aesthetic dims
      // drop to tier-3. Mirrors candle OHL grouping but without a header.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("Task"), palette.put("Design Phase"));
      tip.addTooltip(palette.put("Start"), palette.put("2025-01-01"));
      tip.addTooltip(palette.put("End"), palette.put("2025-03-15"));
      tip.addTooltip(palette.put("Owner"), palette.put("Alice"));
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(2);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">Task:&nbsp;Design Phase"),
                 "Task (Y-dim) is the tier-1 headline");
      assertTrue(out.contains("<div class=\"tt-tier-2\">Start:&nbsp;2025-01-01"),
                 "Start joins the tier-2 group");
      assertTrue(out.contains("<div class=\"tt-tier-2\">End:&nbsp;2025-03-15"),
                 "End joins the tier-2 group with equal importance to Start");
      assertTrue(out.contains("<div class=\"tt-tier-3\">Owner:&nbsp;Alice"),
                 "Aesthetic past the tier-2 group drops to tier-3");
      assertFalse(out.contains("tt-subtitle"),
                  "No header set → no subtitle row");
   }

   @Test
   void ganttCardGroupsOnlyDatesAtTier2WhenManyDims() {
      // Multi-dim gantt: the innermost Y dim headlines (tier-1), Start/End/
      // Milestone group at tier-2 as core interval data, and the outer Y dim, X
      // dim, and color aesthetic all drop to tier-3 as secondary context. Row
      // order and tier2GroupSize match what PlotArea emits for the card layout.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("DataGroup(city)"), palette.put("A"));
      tip.addTooltip(palette.put("orderdate"), palette.put("2000-10-05"));
      tip.addTooltip(palette.put("end"), palette.put("2000-10-15"));
      tip.addTooltip(palette.put("milestone"), palette.put("2000-10-10"));
      tip.addTooltip(palette.put("reseller"), palette.put("true"));
      tip.addTooltip(palette.put("state"), palette.put("TX"));
      tip.addTooltip(palette.put("product_name"), palette.put("WebCalendar"));
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(3); // measurePairs when an interval bar renders Start + End + Milestone

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">DataGroup(city):&nbsp;A"),
                 "Innermost Y dim is the tier-1 headline");
      assertTrue(out.contains("<div class=\"tt-tier-2\">orderdate:&nbsp;2000-10-05"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">end:&nbsp;2000-10-15"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">milestone:&nbsp;2000-10-10"),
                 "All three date measures group at tier-2");
      assertTrue(out.contains("<div class=\"tt-tier-3\">reseller:&nbsp;true"),
                 "Outer Y dim is secondary context at tier-3");
      assertTrue(out.contains("<div class=\"tt-tier-3\">state:&nbsp;TX"),
                 "X dim is secondary context at tier-3");
      assertTrue(out.contains("<div class=\"tt-tier-3\">product_name:&nbsp;WebCalendar"),
                 "Aesthetic dim is secondary context at tier-3");
      assertFalse(out.contains("tt-tier-4"));
   }

   @Test
   void ganttCardMilestonePointGroupsOnlyMilestoneAtTier2() {
      // A milestone point exposes only Milestone via getVars(), so measurePairs == 1:
      // the innermost Y dim headlines and Milestone alone sits at tier-2, with the
      // outer Y dim and X dim at tier-3. Guards the per-element tier-2 sizing.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("DataGroup(city)"), palette.put("A"));
      tip.addTooltip(palette.put("milestone"), palette.put("2000-10-10"));
      tip.addTooltip(palette.put("reseller"), palette.put("true"));
      tip.addTooltip(palette.put("state"), palette.put("TX"));
      tip.setGroupedTiers(true);
      tip.setTier2GroupSize(1); // measurePairs for a milestone point

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-1\">DataGroup(city):&nbsp;A"),
                 "Innermost Y dim is the tier-1 headline");
      assertTrue(out.contains("<div class=\"tt-tier-2\">milestone:&nbsp;2000-10-10"),
                 "Milestone alone groups at tier-2 for a milestone point");
      assertTrue(out.contains("<div class=\"tt-tier-3\">reseller:&nbsp;true"),
                 "Outer Y dim drops to tier-3");
      assertTrue(out.contains("<div class=\"tt-tier-3\">state:&nbsp;TX"),
                 "X dim drops to tier-3");
      assertFalse(out.contains("tt-tier-4"));
   }

   @Test
   void uniformTierCardRendersAllRowsAtSameTier() {
      // Scatter with no identity dim: the measures are coordinates of equal
      // weight, so every row renders at the same tier with no headline.
      IndexedSet<String> palette = new IndexedSet<>();
      ChartToolTip tip = new ChartToolTip();
      tip.setStyle(ChartInfo.TooltipStyle.CARD);
      tip.addTooltip(palette.put("Quantity Purchased"), palette.put("5"));
      tip.addTooltip(palette.put("Paid"), palette.put("20"));
      tip.addTooltip(palette.put("Discount"), palette.put("0.1"));
      tip.setUniformTier(true);

      String out = tip.getTooltip(palette);

      assertTrue(out.contains("<div class=\"tt-tier-2\">Quantity Purchased:&nbsp;5"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Paid:&nbsp;20"));
      assertTrue(out.contains("<div class=\"tt-tier-2\">Discount:&nbsp;0.1"));
      assertFalse(out.contains("tt-tier-1"), "Uniform card must not promote any row to tier-1");
      assertFalse(out.contains("tt-tier-3"), "Uniform card keeps every row at the same tier");
      assertFalse(out.contains("tt-subtitle"));
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
