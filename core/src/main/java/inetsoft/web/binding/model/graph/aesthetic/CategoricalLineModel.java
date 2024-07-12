/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalLineFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalLineFrameWrapper;

public class CategoricalLineModel extends LineFrameModel {
   public CategoricalLineModel() {
   }

   public CategoricalLineModel(CategoricalLineFrameWrapper wrapper) {
      super(wrapper);
      CategoricalLineFrame frame = (CategoricalLineFrame) wrapper.getVisualFrame();
      int[] lines = new int[frame.getLineCount()];

      for(int i = 0; i < lines.length; i++) {
         lines[i] = wrapper.getLine(i);
      }

      setLines(lines);
   }

   /**
    * Set the current using Lines.
    */
   public void setLines(int[] lines) {
      this.lines = lines;
   }

   /**
    * Get the current using Lines.
    */
   public int[] getLines() {
      return lines;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new CategoricalLineFrame();
   }

   private int[] lines;
}