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
package inetsoft.graph.internal.text;

import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;

/**
 * Fixed position (can't be moved) labels.
 */
public class FixedHelper extends LayoutHelper {
   public FixedHelper(Visualizable label, VGraph vgraph) {
      super(label, vgraph);
      setRemovable(false);
   }

   @Override
   public void calc(LayoutHelper text) {
   }

   @Override
   public double getMinResistance() {
      return MAX_RESISTANCE;
   }

   @Override
   public void processOverlapped(LayoutHelper helper) {
   }

   @Override
   public void move() {
   }

   public String toString() {
      return "Fixed: " + getVisualizable();
   }
}
