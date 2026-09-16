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
import inetsoft.graph.aesthetic.CompositeColorFrame;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.geometry.ElementGeometry;

/**
 * Whether a rendered mark is one of the brushed ones. Asks the colour frame's brush condition
 * rather than comparing the mark's colour against a known highlight, which only holds while every
 * brushed mark shares one colour.
 */
public final class BrushedMarks {
   private BrushedMarks() {
   }

   public static boolean isBrushed(ElementGeometry gobj) {
      if(gobj == null || gobj.getElement() == null) {
         return false;
      }

      VisualModel model = gobj.getVisualModel();

      return isBrushed(gobj.getElement().getColorFrame(),
                       model == null ? null : model.getDataSet(),
                       gobj.getTupleIndex());
   }

   /**
    * Whether the mark's colour frame carries a brush marker at all — a
    * {@code CompanionBrushColorFrame} or an {@code HLColorFrame} in the composite. False means
    * neither is present, which is the only case a legacy flat-colour comparison remains valid for.
    */
   public static boolean hasMarker(ElementGeometry gobj) {
      if(gobj == null || gobj.getElement() == null) {
         return false;
      }

      return hasMarker(gobj.getElement().getColorFrame());
   }

   static boolean hasMarker(ColorFrame frame) {
      if(!(frame instanceof CompositeColorFrame)) {
         return false;
      }

      CompositeColorFrame composite = (CompositeColorFrame) frame;
      return composite.getFrames(CompanionBrushColorFrame.class).findAny().isPresent() ||
         composite.getFrames(HLColorFrame.class).findAny().isPresent();
   }

   static boolean isBrushed(ColorFrame frame, DataSet data, int tidx) {
      if(!(frame instanceof CompositeColorFrame) || data == null) {
         return false;
      }

      CompositeColorFrame composite = (CompositeColorFrame) frame;

      if(composite.getFrames(CompanionBrushColorFrame.class).findAny().isPresent()) {
         return composite.getFrames(CompanionBrushColorFrame.class)
            .anyMatch(f -> ((CompanionBrushColorFrame) f).isSelected(data, tidx));
      }

      return composite.getFrames(HLColorFrame.class)
         .anyMatch(f -> ((HLColorFrame) f).getHighlight(data, tidx) != null);
   }

   /**
    * Ordering that puts a brushed mark after an unbrushed one, so it draws on top.
    */
   static int order(boolean b1, boolean b2) {
      return b1 == b2 ? 0 : (b1 ? 1 : -1);
   }
}
