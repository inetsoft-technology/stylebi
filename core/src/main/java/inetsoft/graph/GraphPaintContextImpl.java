/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph;

import inetsoft.graph.guide.VMeasureTitle;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.GridLine;
import inetsoft.graph.visual.*;

public class GraphPaintContextImpl implements GraphPaintContext {
   public GraphPaintContextImpl(boolean paintLegends,
                                boolean paintTitles,
                                boolean paintAxes,
                                boolean paintVOVisuals)
   {
      this.paintLegends = paintLegends;
      this.paintTitles = paintTitles;
      this.paintAxes = paintAxes;
      this.paintVOVisuals = paintVOVisuals;
   }

   /**
    * @inheritDoc
    */
   @Override
   public boolean paintLegends() {
      return paintLegends;
   }

   /**
    * @inheritDoc
    */
   @Override
   public boolean paintTitles() {
      return paintTitles;
   }

   /**
    * @inheritDoc
    */
   @Override
   public boolean paintVisual(Visualizable visual) {
      if(visual instanceof GridLine) {
         return paintAxes || ((GridLine) visual).getAxis() == null;
      }
      else if(visual instanceof Axis) {
         return paintAxes;
      }
      else if(!paintVOVisuals && !(visual instanceof FormVO && !((FormVO) visual).isInPlot()) &&
              (visual instanceof VisualObject || visual instanceof VOText ||
               visual instanceof VMeasureTitle))
      {
         return false;
      }

      return true;
   }

   public static class Builder {
      public Builder paintLegends(boolean paintLegends) {
         this.paintLegends = paintLegends;
         return this;
      }

      public Builder paintTitles(boolean paintTitles) {
         this.paintTitles = paintTitles;
         return this;
      }

      public Builder paintAxes(boolean paintAxes) {
         this.paintAxes = paintAxes;
         return this;
      }

      public Builder paintVOVisuals(boolean paintVOVisuals) {
         this.paintVOVisuals = paintVOVisuals;
         return this;
      }

      public GraphPaintContextImpl build() {
         return new GraphPaintContextImpl(paintLegends, paintTitles, paintAxes,
                                          paintVOVisuals);
      }

      private boolean paintLegends = true;
      private boolean paintTitles = true;
      private boolean paintAxes = true;
      private boolean paintVOVisuals = true;
   }

   private final boolean paintLegends;
   private final boolean paintTitles;
   private final boolean paintAxes;
   private final boolean paintVOVisuals;
}
