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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;

import java.util.Collection;

/**
 * The defines the interface where multiple visual frames are used to map visual attributes
 * for different dimension or measure.
 */
public interface MultiplexFrame<T extends VisualFrame> extends MultiFieldFrame {
   /**
    * Set a frame for a dimension or measure
    */
   void setFrame(String measure, T frame);

   /**
    * Get all frames.
    */
   Collection<T> getFrames();

   default void setScaleOption(int option) {
      getFrames().forEach(f -> f.setScaleOption(option));
   }

   default void init(DataSet data) {
      getFrames().forEach(f -> f.init(data));
   }

   @Override
   default void setFields(String... fields) {
      // do nothing
   }

   @Override
   default String[] getFields() {
      return getFrames().stream()
         .map(VisualFrame::getField)
         .filter(f -> f != null)
         .distinct()
         .toArray(String[]::new);
   }
}
