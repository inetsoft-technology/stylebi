/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.aesthetic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * CompositeColorFrame combines multiple legend frames for cascading style.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public interface CompositeVisualFrame {
   /**
    * Add a legend frame.
    */
   public void addFrame(VisualFrame frame);

   /**
    * Get the number of legend frames.
    */
   public int getFrameCount();

   /**
    * Get the legend frame at the specified index.
    */
   public VisualFrame getFrame(int idx);

   /**
    * Remove the legend frame at the specified index.
    */
   public void removeFrame(int idx);

   /**
    * Get the legend frame to generate legend guide.
    */
   public VisualFrame getGuideFrame();

   default String[] getVisualFields() {
      List<String> fields = new ArrayList<>();

      for(int i = 0; i < getFrameCount(); i++) {
         if(getFrame(i).getVisualField() != null) {
            fields.add(getFrame(i).getVisualField());
         }
      }

      return fields.toArray(new String[0]);
   }

   /**
    * Get sub-frames of the given type.
    */
   Stream<VisualFrame> getFrames(Class type);
}
