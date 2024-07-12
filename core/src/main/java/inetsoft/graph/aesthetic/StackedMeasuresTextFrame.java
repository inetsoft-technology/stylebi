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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;

import java.util.*;

/**
 * This text frame returns a text value for each measure in a stack
 *
 * @author InetSoft Technology
 * @version 13.4
 */
public class StackedMeasuresTextFrame extends TextFrame implements StackedMeasuresFrame<TextFrame> {
   public StackedMeasuresTextFrame(TextFrame defaultFrame) {
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
   public Object getText(DataSet data, String col, int row) {
      TextFrame frame = frameMap.get(col);

      if(frame != null) {
         return frame.getText(data, col, row);
      }

      return defaultFrame != null ? defaultFrame.getText(data, col, row) : null;
   }

   @Override
   public void setFrame(String measure, TextFrame frame) {
      frameMap.put(measure, frame);
   }

   @Override
   public Collection<TextFrame> getFrames() {
      return frameMap.values();
   }

   @Override
   public TextFrame getDefaultFrame() {
      return defaultFrame;
   }

   @Override
   public void setScaleOption(int option) {
      StackedMeasuresFrame.super.setScaleOption(option);
   }

   @Override
   public String getShareId() {
      return StackedMeasuresFrame.super.getShareId(super.getShareId());
   }

   private final Map<String, TextFrame> frameMap = new HashMap<>();
   private final TextFrame defaultFrame;
   private static final long serialVersionUID = 1L;
}
