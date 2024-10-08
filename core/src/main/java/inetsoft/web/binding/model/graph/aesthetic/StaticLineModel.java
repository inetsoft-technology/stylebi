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

import inetsoft.graph.aesthetic.StaticLineFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.StyleConstants;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticLineFrameWrapper;

public class StaticLineModel extends LineFrameModel {
   public StaticLineModel() {
   }

   public StaticLineModel(StaticLineFrameWrapper wrapper) {
      super(wrapper);
      setLine(wrapper.getLine());
   }

   /**
    * Set the current using Line.
    */
   public void setLine(int line) {
      this.line = line;
   }

   /**
    * Set the current using Line.
    */
   public int getLine() {
      return line;
   }

   @Override
   public VisualFrame createVisualFrame() {
      return new StaticLineFrame();
   }

   private int line = StyleConstants.THIN_LINE;
}