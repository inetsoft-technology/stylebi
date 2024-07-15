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
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.visual.VOText;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Helper for a vlabel.
 */
public abstract class LabelHelper extends LayoutHelper {
   public LabelHelper(VLabel label, VGraph vgraph) {
      super(label, vgraph);

      this.label = label;
      GraphElement elem = (label instanceof VOText)
         ? ((VOText) label).getGraphElement() : null;
      overlay = (elem != null) ? elem.getHint("overlay") : null;
   }

   /**
    * Get the text label.
    */
   public VLabel getLabel() {
      return label;
   }

   /**
    * Compare the minimum resistance.
    */
   @Override
   public int compareTo(Object obj) {
      int rc = super.compareTo(obj);

      if(rc == 0 && obj instanceof LabelHelper) {
         String str1 = getLabel().getLabel() + "";
         String str2 = ((LabelHelper) obj).getLabel().getLabel() + "";
         return str1.compareTo(str2);
      }

      return rc;
   }

   /**
    * Get the actual bounds on screen.
    */
   @Override
   protected Shape getTransformedBounds() {
      return label.getTransformedBounds();
   }

   /**
    * Check if this label is in plot bounds.
    */
   @Override
   public boolean isContained(Rectangle2D pbounds) {
      return label.isContained(pbounds, 1, 1);
   }

   /**
    * Get the option from the VOText.
    */
   @Override
   public int getCollisionModifier() {
      return label.getCollisionModifier();
   }

   private VLabel label;
}
