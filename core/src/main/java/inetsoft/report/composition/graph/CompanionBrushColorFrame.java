/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brushing colours resolved per mark. An unselected mark recedes to the companion of the colour it
 * would otherwise have carried, so a brushed chart keeps its series distinguishable; a selected one
 * keeps that colour unchanged.
 *
 * Also the marker for which marks are brushed. A predicate answers per row, which is the case where
 * one layer holds both states. A null predicate means the layer is uniform and selected decides it,
 * which is the case where the chart is split into an all-data layer and a brushed-data layer.
 */
public class CompanionBrushColorFrame extends ColorFrame {
   public CompanionBrushColorFrame(HLColorFrame predicate, boolean selected, ColorFrame base,
                                   boolean dark)
   {
      this.predicate = predicate;
      this.selected = selected;
      this.base = base;
      this.dark = dark;
      this.companions = new ConcurrentHashMap<>();
   }

   /**
    * Whether the mark at this row is one of the brushed ones, ignoring any level the predicate is
    * scoped to. Callers that hold the column being resolved should pass it.
    */
   public boolean isSelected(DataSet data, int row) {
      return predicate == null ? selected : predicate.getHighlight(data, row) != null;
   }

   /**
    * Whether the mark at this row and column is one of the brushed ones.
    */
   public boolean isSelected(DataSet data, String col, int row) {
      if(predicate == null) {
         return selected;
      }

      String level = predicate.getVisualField();

      // a relation brush condition that lands on one level scopes its highlight to that level. the
      // row matches the condition either way, so without this a brushed parent reports its whole
      // row as selected and its children never dim
      if(level != null && !level.equals(GraphUtil.getOriginalCol(col))) {
         return false;
      }

      return predicate.getHighlight(data, row) != null;
   }

   public ColorFrame getBase() {
      return base;
   }

   @Override
   public Color getColor(DataSet data, String col, int row) {
      Color c = base == null ? null : base.getColor(data, col, row);

      if(c == null) {
         return null;
      }

      return process(isSelected(data, col, row) ? c : companion(c), getBrightness());
   }

   @Override
   public Color getColor(Object val) {
      Color c = base == null ? null : base.getColor(val);

      if(c == null) {
         return null;
      }

      // no row to evaluate, so only a uniform unselected layer recedes
      return process(predicate != null || selected ? c : companion(c), getBrightness());
   }

   @Override
   public boolean isApplicable(String field) {
      // only for the field the wrapped frame answers for. a relation geometry asks with the source
      // dimension for a root node, and a categorical frame reads its own field rather than the
      // column it is handed, so answering yes there returns the target row's colour and the root
      // takes its child's hue. saying no leaves the node to the element's own frame, which is
      // brushed too. (57364, 57373)
      return base != null && base.isApplicable(field);
   }

   /**
    * A copy of this frame receding from a different base colour frame.
    */
   public CompanionBrushColorFrame withBase(ColorFrame base) {
      return new CompanionBrushColorFrame(predicate, selected, base, dark);
   }

   @Override
   public String getVisualField() {
      return base == null ? super.getVisualField() : base.getVisualField();
   }

   @Override
   public Object clone() {
      CompanionBrushColorFrame frame = (CompanionBrushColorFrame) super.clone();

      if(frame == null) {
         return null;
      }

      // callers clone a colour frame so an element gets its own brightness, and brightness is
      // pushed down into sub-frames. sharing the base would defeat that, so copy it here the way
      // a composite copies the frames it holds.
      if(base != null) {
         frame.base = (ColorFrame) base.clone();
      }

      if(predicate != null) {
         frame.predicate = (HLColorFrame) predicate.clone();
      }

      frame.companions = new ConcurrentHashMap<>();

      return frame;
   }

   /**
    * The companion of a series colour, memoised: resolving one rebuilds the palette stack, and an
    * unselected mark asks on every repaint.
    */
   private Color companion(Color c) {
      Map<Color, Color> memo = companions;

      if(memo == null) {
         // transient, so a deserialized frame starts without one
         companions = memo = new ConcurrentHashMap<>();
      }

      VizContext ctx = context;

      if(ctx == null) {
         // resolved once per frame rather than per mark. not in the constructor: it reads
         // SreeEnv, and a frame may be built before the configuration context is up.
         context = ctx = VizContext.of(dark ? VizMark.MODERN_DARK : VizMark.MODERN_LIGHT);
      }

      VizContext context0 = ctx;

      return memo.computeIfAbsent(c, color -> {
         Color companion = VSChartPaletteDefaults.companionOf(color, context0);
         // a palette may declare no companion. substitute the source colour rather than a null,
         // which a ConcurrentHashMap cannot hold and computeIfAbsent would drop, not cache.
         return companion == null ? color : companion;
      });
   }

   private HLColorFrame predicate;
   private ColorFrame base;
   private final boolean selected;
   private final boolean dark;
   // neither serialises, and both are rebuilt on demand
   private transient VizContext context;
   private transient Map<Color, Color> companions;
}
