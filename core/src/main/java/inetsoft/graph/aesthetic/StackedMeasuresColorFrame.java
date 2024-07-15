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

import java.awt.*;
import java.util.*;

/**
 * This color frame returns a color value for each measure in a stack
 *
 * @author InetSoft Technology
 * @version 13.4
 */
public class StackedMeasuresColorFrame extends ColorFrame implements StackedMeasuresFrame<ColorFrame> {
   public StackedMeasuresColorFrame(ColorFrame defaultFrame) {
      this.defaultFrame = defaultFrame;
   }

   @Override
   public String getField() {
      return getDefaultField();
   }

   @Override
   public void init(DataSet data) {
      StackedMeasuresFrame.super.init(data);
   }

   @Override
   public Color getColor(DataSet data, String col, int row) {
      ColorFrame frame = frameMap.get(col);

      if(frame != null) {
         return frame.getColor(data, col, row);
      }

      // legend calls getColor(Object) to get legend item color. we should apply the
      // same logic for VO so the colors matches the legend.
      if(getField() != null) {
         return getColor(data.getData(getField(), row));
      }

      return defaultFrame != null ? defaultFrame.getColor(data, col, row) : null;
   }

   @Override
   public Color getColor(Object val) {
      Color color = null;

      for(ColorFrame frame : frameMap.values()) {
         if((color = frame.getColor(val)) != null) {
            return color;
         }
      }

      return defaultFrame != null ? defaultFrame.getColor(val) : null;
   }

   @Override
   public void setFrame(String measure, ColorFrame frame) {
      frameMap.put(measure, frame);
   }

   @Override
   public Collection<ColorFrame> getFrames() {
      return frameMap.values();
   }

   @Override
   public ColorFrame getDefaultFrame() {
      return defaultFrame;
   }

   @Override
   public boolean isVisible() {
      return StackedMeasuresFrame.super.isVisible();
   }

   @Override
   public String getTitle() {
      return StackedMeasuresFrame.super.getTitle();
   }

   @Override
   public Object[] getValues() {
      return StackedMeasuresFrame.super.getValues();
   }

   @Override
   public void setScaleOption(int option) {
      StackedMeasuresFrame.super.setScaleOption(option);
   }

   @Override
   public String getShareId() {
      return StackedMeasuresFrame.super.getShareId(super.getShareId());
   }

   private final Map<String, ColorFrame> frameMap = new HashMap<>();
   private final ColorFrame defaultFrame;
   private static final long serialVersionUID = 1L;
}
