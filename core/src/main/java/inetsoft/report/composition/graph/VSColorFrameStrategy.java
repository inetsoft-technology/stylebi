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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.viewsheet.graph.*;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * VS color frame strategy.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VSColorFrameStrategy implements VSFrameStrategy {
   /**
    * Create an instance of VSColorFrameStrategy.
    */
   public VSColorFrameStrategy(ChartInfo info) {
      super();

      this.info = info;
   }

   /**
    * Get the AestheticRef to be visited in the specified ChartInfo.
    */
   @Override
   public AestheticRef getAestheticRef(ChartAggregateRef ref) {
      return (ref != null) ? ref.getColorField() :
         info.isMultiAesthetic() ? null : info.getColorField();
   }

   /**
    * Check if supports getting frame from ChartInfo.
    */
   @Override
   public boolean supportsGeneralFrame() {
      return info instanceof MergedChartInfo;
   }

   /**
    * Get the VisualFrame to be visited in the ChartInfo.
    */
   @Override
   public VisualFrame getGeneralFrame() {
      return info.getColorFrame();
   }

   /**
    * Check if supports getting frame from chart aggregate ref.
    */
   @Override
   public boolean supportsFieldFrame() {
      return info.supportsColorFieldFrame();
   }

   /**
    * Get the VisualFrame to be visited in the specified ChartAggregateRef.
    */
   @Override
   public VisualFrame getFieldFrame(ChartAggregateRef ref) {
      return ref.getColorFrame();
   }

   /**
    * Get the summary VisualFrame to be visited in the specified
    * ChartAggregateRef.
    */
   @Override
   public VisualFrame getSummaryFrame(ChartAggregateRef ref) {
      return ref.getSummaryColorFrame();
   }

   /**
    * Create combined frames.
    */
   @Override
   public VisualFrame createCombinedFrame(String[] names, VisualFrame[] frames,
                                          boolean same, final boolean force)
   {
      Color[] colors = new Color[frames.length];
      Set<Color> usedColors = new HashSet<>();
      Color[] acolors = CategoricalColorFrame.COLOR_PALETTE;
      List<Color> negColors = new ArrayList<>();
      List hidden = new ArrayList();

      for(int i = 0; i < frames.length; i++) {
         StaticColorFrame frame = (StaticColorFrame) frames[i];
         colors[i] = frame.getColor();

         if(colors[i] != null && same && usedColors.contains(colors[i])) {
            colors[i] = getColor(usedColors, acolors);
         }

         if(same) {
            usedColors.add(colors[i]);
         }

         // allow measure to be hidden in legend (for date comparison).
         if(!frame.getLegendSpec().isVisible()) {
            hidden.add(names[i]);
         }

         if(frame.getNegativeColor() != null) {
            negColors.add(frame.getNegativeColor());
         }
      }

      CategoricalColorFrame frame = new MultiMeasureColorFrame(force, names, colors);
      frame.setNegativeColors(negColors.toArray(new Color[0]));
      hidden.forEach(a -> frame.getLegendSpec().setVisible(a, false));
      return frame;
   }

   /**
    * Get the new color which is not occupied.
    */
   private Color getColor(Set<Color> usedColors, Color[] colors) {
      for(int i = 0; i < colors.length; i++) {
         if(!usedColors.contains(colors[i])) {
            return colors[i];
         }
      }

      return colors[0];
   }

   private ChartInfo info;
}
