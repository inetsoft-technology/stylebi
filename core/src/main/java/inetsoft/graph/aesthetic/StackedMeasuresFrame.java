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

import inetsoft.graph.LegendSpec;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the common interface for all stacked measure visual frames.
 */
public interface StackedMeasuresFrame<T extends VisualFrame> extends MultiplexFrame<T> {
   /**
    * Get the frame that's used when there are no measure mappings
    */
   T getDefaultFrame();

   /**
    * Get the legend spec for this frame
    */
   LegendSpec getLegendSpec();

   /**
    * Get visual frames that should be used for creating legend.
    */
   default VisualFrame[] getLegendFrames() {
      Collection<T> frames = getFrames();
      boolean categorical = frames.stream()
         .map(f -> f instanceof CompositeVisualFrame
            ? ((CompositeVisualFrame) f).getGuideFrame() : f)
         .allMatch(f -> f instanceof CategoricalFrame);
      boolean sameField = frames.stream().map(f -> f.getField()).distinct().count() == 1;
      boolean combine = categorical && sameField;
      List<VisualFrame> legends = new ArrayList<>();

      if(!combine) {
         // show individual legends if not combining (e.g. linear color bound to different field,
         // or mix of categorical and linear, or categorical bound to different fields).
         if(frames.size() > 0) {
            legends.addAll(frames);
         }
         // don't show as color items (use band).
         else if(getDefaultFrame() instanceof LinearColorFrame) {
            legends.add(getDefaultFrame());
         }
      }

      // show as one legend
      if(legends.isEmpty()) {
         legends.add((VisualFrame) this);
      }

      // find legend frame
      return legends.stream()
         .map(f -> f instanceof CompositeVisualFrame
            ? ((CompositeVisualFrame) f).getGuideFrame() : f)
         .toArray(VisualFrame[]::new);
   }

   default String getDefaultField() {
      VisualFrame[] legends = getLegendFrames();
      String field = null;

      if(legends.length == 1) {
         // default frame
         if(legends[0] != this) {
            field = legends[0].getField();
         }
         // showing combined frame bound to same field
         else if(!getFrames().isEmpty()) {
            field = getFrames().stream().findFirst().map(f -> f.getField()).orElse(null);
         }
      }

      return field;
   }

   default boolean isVisible() {
      LegendSpec legendSpec = getLegendSpec();

      if(getFrames().size() > 0 || getDefaultFrame() == null) {
         return legendSpec.isVisible();
      }

      if(!legendSpec.isVisible()) {
         return false;
      }

      return getDefaultFrame().isVisible();
   }

   default String getTitle() {
      LegendSpec legendSpec = getLegendSpec();

      if(legendSpec.getTitle() != null) {
         return legendSpec.getTitle();
      }

      Collection<T> frames = getFrames();

      if(frames.size() > 0) {
         return frames.stream()
            .map(VisualFrame::getField)
            .distinct()
            .collect(Collectors.joining("/"));
      }
      else {
         return getDefaultFrame() != null ? getDefaultFrame().getTitle() : null;
      }
   }

   default Object[] getValues() {
      Collection<T> frames = getFrames();

      if(frames.size() > 0) {
         return frames.stream()
            .map(VisualFrame::getValues)
            .flatMap(Arrays::stream)
            .distinct()
            .toArray();
      }
      else {
         return getDefaultFrame() != null ? getDefaultFrame().getValues() : null;
      }
   }

   default String getShareId(String defaultId) {
      // should ignore stacked measure legend only if default frame is not set. (50344)
      if(getDefaultFrame() == null) {
         return "stacked-measure";
      }

      return defaultId;
   }
}
