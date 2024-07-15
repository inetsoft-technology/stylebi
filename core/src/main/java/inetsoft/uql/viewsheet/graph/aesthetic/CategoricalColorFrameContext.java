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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;

import java.awt.*;
import java.util.*;

/**
 * Helper object exposed as a ThreadLocal used to pass information down from the GraphGenerator
 * to the VSFrameVisitor
 */
public class CategoricalColorFrameContext {
   public static CategoricalColorFrameContext getContext() {
      return context.get();
   }

   public Map<String, Color> getDimensionColors() {
      return Collections.unmodifiableMap(dimensionColors);
   }

   public void setDimensionColors(Map<String, Color> dimensionColors) {
      this.dimensionColors = dimensionColors;
   }

   public void setSharedFrames(Map<SharedFrameParameters, VisualFrame> frames) {
      sharedFrames = frames;
   }

   public void addSharedFrame(String columnName, VisualFrame frame, DataRef ref) {
      final SharedFrameParameters sharedFrame = new SharedFrameParameters();
      addParameters(columnName, ref, sharedFrame);
      sharedFrames.put(sharedFrame, frame);
   }

   public VisualFrame getSharedFrame(String columnName, DataRef ref) {
      final SharedFrameParameters sharedFrame = new SharedFrameParameters();
      addParameters(columnName, ref, sharedFrame);
      return sharedFrames.get(sharedFrame);
   }

   public void addParameters(String columnName, DataRef ref, SharedFrameParameters sharedFrame) {
      sharedFrame.addParameter(columnName);

      if(ref instanceof VSChartDimensionRef && XSchema.isDateType(ref.getDataType())) {
         final int dateLevel = ((VSChartDimensionRef) ref).getDateLevel();
         sharedFrame.addParameter(dateLevel);
      }
   }

   private static final ThreadLocal<CategoricalColorFrameContext> context =
      ThreadLocal.withInitial(CategoricalColorFrameContext::new);
   private Map<SharedFrameParameters, VisualFrame> sharedFrames = new WeakHashMap<>();
   private Map<String, Color> dimensionColors = new HashMap<>();
}
