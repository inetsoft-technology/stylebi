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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.visual.ElementVO;

import java.awt.*;
import java.util.Objects;

/**
 * Colours each candle or waterfall step by its direction, up or down.
 */
public class DirectionColorFrame extends StaticColorFrame {
   /**
    * A frame for a candle or stock chart, comparing the close with the open.
    */
   public DirectionColorFrame(Color up, Color down, String openField, String closeField) {
      super(up);
      this.up = up;
      this.down = down;
      this.openField = openField;
      this.closeField = closeField;
   }

   /**
    * A frame for a waterfall, reading the sign of each step.
    */
   public DirectionColorFrame(Color up, Color down) {
      this(up, down, null, null);
   }

   public Color getUpColor() {
      return up;
   }

   public Color getDownColor() {
      return down;
   }

   @Override
   public Color getColor(DataSet data, String col, int row) {
      return process(isDown(data, col, row) ? down : up, getBrightness());
   }

   @Override
   public Color getColor(Object val) {
      // no row to read a direction from
      return process(up, getBrightness());
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      DirectionColorFrame frame = (DirectionColorFrame) obj;
      return Objects.equals(up, frame.up) && Objects.equals(down, frame.down) &&
         Objects.equals(openField, frame.openField) &&
         Objects.equals(closeField, frame.closeField);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), up, down, openField, closeField);
   }

   private boolean isDown(DataSet data, String col, int row) {
      if(data == null) {
         return false;
      }

      if(openField == null && closeField == null) {
         Number step = number(data, col, row);
         return step != null && step.doubleValue() < 0;
      }

      Number open = number(data, openField, row);
      Number close = number(data, closeField, row);
      return open != null && close != null && close.doubleValue() < open.doubleValue();
   }

   // the plain value, else the all-data copy that a brushed chart's all-data rows hold
   private static Number number(DataSet data, String name, int row) {
      if(name == null) {
         return null;
      }

      Object val = value(data, name, row);

      if(val == null && !name.startsWith(ElementVO.ALL_PREFIX)) {
         val = value(data, ElementVO.ALL_PREFIX + name, row);
      }

      return val instanceof Number ? (Number) val : null;
   }

   private static Object value(DataSet data, String col, int row) {
      return data.indexOfHeader(col) >= 0 ? data.getData(col, row) : null;
   }

   // immutable, so the inherited clone copies them
   private final Color up;
   private final Color down;
   private final String openField;
   private final String closeField;

   private static final long serialVersionUID = 1L;
}
