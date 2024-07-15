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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;

public class StaticSizeModel extends SizeFrameModel {
   public StaticSizeModel() {
   }

   public StaticSizeModel(StaticSizeFrameWrapper wrapper) {
      super(wrapper);
      setSize(wrapper.getSize());
      setChanged(wrapper.isChanged());
   }

   /**
    * Get the size of the static size frame.
    */
   public double getSize() {
      return this.size;
   }

   /**
    * Set the size of the static size frame.
    */
   public void setSize(double size) {
      this.size = size;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new StaticSizeFrame();
   }

   private double size = 1;
}
