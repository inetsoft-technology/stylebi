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

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.uql.XConstants;

import java.awt.*;

/**
 * Axis label length and rotation rules for a chart under the modern mark.
 */
final class ModernLabelRule {
   /** The longest label, in px, for 10px text. */
   static final double LIMIT_PX = 66;
   /** The room kept between neighbouring labels, in px. */
   static final double GAP_PX = 4;
   /** The share of the graph width a category column may take. */
   static final double COLUMN_RATIO = 0.3;
   /** The widest a category column may grow to, in px, for 10px text. */
   static final double COLUMN_PX = 110;

   private ModernLabelRule() {
   }

   /**
    * The longest label length for the font.
    */
   static double limitPx(Font font) {
      return LIMIT_PX * font.getSize2D() / 10;
   }

   /**
    * The longest label length of a category column on a Y axis.
    */
   static double columnLimitPx(Font font, double graphWidth) {
      return Math.max(limitPx(font),
                      Math.min(COLUMN_RATIO * graphWidth, COLUMN_PX * font.getSize2D() / 10));
   }

   /**
    * The smallest k, from 1, with k slots wide enough for the widest label.
    */
   static int step(double widestPx, double slotPx) {
      if(slotPx <= 0) {
         return Integer.MAX_VALUE;
      }

      return Math.max(1, (int) Math.ceil((widestPx + GAP_PX) / slotPx - 1e-9));
   }

   /**
    * The rotation of the labels: dates and numbers stay flat, text turns to 45 once it needs
    * more than every other slot.
    */
   static int rotation(boolean dateOrNumber, double widestPx, double slotPx) {
      return dateOrNumber || step(widestPx, slotPx) <= 2 ? 0 : 45;
   }

   /**
    * Whether the measure sums a number field with nothing else applied, so "Sum" is the default.
    */
   static boolean dropsDefaultSum(ChartAggregateRef agg) {
      return agg != null && agg.getFormula() == AggregateFormula.SUM &&
         agg.isAggregateEnabled() && agg.getDataRef() != null && agg.getCalculator() == null &&
         agg.getPercentageOption() == XConstants.PERCENTAGE_NONE &&
         !VSUtil.isAggregateCalc(agg.getDataRef()) &&
         AggregateFormula.getDefaultFormula(agg.getOriginalDataType()) == AggregateFormula.SUM;
   }

   /**
    * The name of the measure's field.
    */
   static String fieldText(ChartAggregateRef agg) {
      return Tool.localize(agg.getDataRef().toView());
   }
}
