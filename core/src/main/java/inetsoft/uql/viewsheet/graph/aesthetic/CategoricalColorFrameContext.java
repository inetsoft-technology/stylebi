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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Helper object exposed as a ThreadLocal used to pass information down from the GraphGenerator
 * to the VSFrameVisitor
 */
public class CategoricalColorFrameContext {
   public static CategoricalColorFrameContext getContext() {
      return context.get();
   }

   /**
    * Point the calling thread's context at the given viewsheet, setting both the shared frames
    * and the dimension colors every time, so state left by another viewsheet on a pooled thread
    * is never read. Pair with {@link #clear()} in a finally block.
    *
    * @param vs the viewsheet that owns the chart being edited or rendered, null for none.
    */
   public static void bind(Viewsheet vs) {
      final CategoricalColorFrameContext context = getContext();
      context.sharedFrames = vs == null ? new WeakHashMap<>() : vs.getSharedFrames();
      context.dimensionColors = new HashMap<>();
   }

   /**
    * Clear the calling thread's context so it carries no viewsheet state.
    */
   public static void clear() {
      bind(null);
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

   /**
    * Get the shape frame shared by another frame on the same column with the same palette.
    *
    * @param palette the shape frame whose palette must match, normally the caller's own frame.
    */
   public VisualFrame getSharedShapeFrame(String columnName, DataRef ref,
                                          CategoricalShapeFrame palette)
   {
      return sharedFrames.get(getShapeParameters(columnName, ref, palette));
   }

   /**
    * Get the shape frame a chart last shared on the column, whatever its palette. Only for a
    * data tip view, which never renders its full data, to borrow the frame of its parent chart.
    *
    * @param chart the absolute name of the chart that shared the frame.
    */
   public VisualFrame getChartSharedShapeFrame(String columnName, DataRef ref, String chart) {
      return sharedFrames.get(getChartShapeParameters(columnName, ref, chart));
   }

   /**
    * @param chart the absolute name of the chart sharing the frame, null if unknown.
    */
   public void addSharedShapeFrame(String columnName, CategoricalShapeFrame frame, DataRef ref,
                                   String chart)
   {
      sharedFrames.put(getShapeParameters(columnName, ref, frame), frame);

      if(chart != null) {
         sharedFrames.put(getChartShapeParameters(columnName, ref, chart), frame);
      }
   }

   private SharedFrameParameters getChartShapeParameters(String columnName, DataRef ref,
                                                         String chart)
   {
      final SharedFrameParameters params = new SharedFrameParameters();
      addParameters(columnName, ref, params);
      params.addParameter(FrameType.SHAPE);
      params.addParameter(new ChartName(chart));
      return params;
   }

   /**
    * The palette is part of the key so a frame is only shared between bindings that would
    * assign the same shapes anyway. Otherwise one chart would show another chart's shape
    * palette for the same column. (78462)
    */
   private SharedFrameParameters getShapeParameters(String columnName, DataRef ref,
                                                    CategoricalShapeFrame frame)
   {
      final SharedFrameParameters params = new SharedFrameParameters();
      addParameters(columnName, ref, params);
      params.addParameter(FrameType.SHAPE);
      final List<String> palette = new ArrayList<>();

      for(int i = 0; i < frame.getShapeCount(); i++) {
         final GShape shape = frame.getShape(i);
         palette.add(shape == null ? "null" : shape.getLegendId() + ":" + shape.isFill());
      }

      params.addParameter(palette);
      return params;
   }

   public void addParameters(String columnName, DataRef ref, SharedFrameParameters sharedFrame) {
      sharedFrame.addParameter(columnName);

      if(ref instanceof VSChartDimensionRef && XSchema.isDateType(ref.getDataType())) {
         final int dateLevel = ((VSChartDimensionRef) ref).getDateLevel();
         sharedFrame.addParameter(dateLevel);
      }
   }

   private enum FrameType { SHAPE }

   private record ChartName(String name) { }

   private static final ThreadLocal<CategoricalColorFrameContext> context =
      ThreadLocal.withInitial(CategoricalColorFrameContext::new);
   private Map<SharedFrameParameters, VisualFrame> sharedFrames = new WeakHashMap<>();
   private Map<String, Color> dimensionColors = new HashMap<>();
}
