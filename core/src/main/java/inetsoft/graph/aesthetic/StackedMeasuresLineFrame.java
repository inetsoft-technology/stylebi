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
 * This line frame returns a line value for each measure in a stack
 *
 * @author InetSoft Technology
 * @version 13.4
 */
public class StackedMeasuresLineFrame extends LineFrame implements StackedMeasuresFrame<LineFrame> {
   public StackedMeasuresLineFrame(LineFrame defaultFrame) {
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
   public GLine getLine(DataSet data, String col, int row) {
      LineFrame frame = frameMap.get(col);

      if(frame != null) {
         return frame.getLine(data, col, row);
      }

      return defaultFrame != null ? defaultFrame.getLine(data, col, row) : null;
   }

   @Override
   public GLine getLine(Object val) {
      for(LineFrame frame : frameMap.values()) {
         if(frame.getLine(val) != null) {
            return frame.getLine(val);
         }
      }

      return defaultFrame != null ? defaultFrame.getLine(val) : null;
   }

   @Override
   public void setFrame(String measure, LineFrame frame) {
      frameMap.put(measure, frame);
   }

   @Override
   public Collection<LineFrame> getFrames() {
      return frameMap.values();
   }

   @Override
   public LineFrame getDefaultFrame() {
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

   private final Map<String, LineFrame> frameMap = new HashMap<>();
   private final LineFrame defaultFrame;
   private static final long serialVersionUID = 1L;
}
