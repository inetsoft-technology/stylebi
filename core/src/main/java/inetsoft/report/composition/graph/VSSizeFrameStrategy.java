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
import inetsoft.util.Catalog;

import java.util.HashMap;
import java.util.Map;

/**
 * VS size frame strategy.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VSSizeFrameStrategy implements VSFrameStrategy {
   /**
    * Create an instance of VSSizeFrameStrategy.
    */
   public VSSizeFrameStrategy(ChartInfo info) {
      super();
      this.info = info;
   }

   /**
    * Get the AestheticRef to be visited in the specified ChartInfo.
    */
   @Override
   public AestheticRef getAestheticRef(ChartAggregateRef aggr) {
      return (aggr != null) ? aggr.getSizeField() :
         info.isMultiAesthetic() ? null : info.getSizeField();
   }

   /**
    * Check if supports getting frame from ChartInfo.
    */
   @Override
   public boolean supportsGeneralFrame() {
      return true;
   }

   /**
    * Get the VisualFrame to be visited in the ChartInfo.
    */
   @Override
   public VisualFrame getGeneralFrame() {
      return info.getSizeFrame();
   }

   /**
    * Check if supports getting frame from chart aggregate ref.
    */
   @Override
   public boolean supportsFieldFrame() {
      return info.supportsSizeFieldFrame();
   }

   /**
    * Get the VisualFrame to be visited in the specified ChartAggregateRef.
    */
   @Override
   public VisualFrame getFieldFrame(ChartAggregateRef ref) {
      return ref.getSizeFrame();
   }

   /**
    * Get the summary VisualFrame to be visited in the specified
    * ChartAggregateRef.
    */
   @Override
   public VisualFrame getSummaryFrame(ChartAggregateRef ref) {
      return null;
   }

   /**
    * Create combined frames.
    */
   @Override
   public VisualFrame createCombinedFrame(String[] names, VisualFrame[] frames,
                                          boolean same, final boolean force)
   {
      final double[] sizes = new double[frames.length];
      boolean interval = GraphTypeUtil.isChartType(info, GraphTypes::isInterval);

      CategoricalSizeFrame frame = new CategoricalSizeFrame() {
         @Override
         public boolean isVisible() {
            // showing legend for static user-set size seems to be useless
            if(getField() == null) {
               return false;
            }

            return force && getLegendSpec().isVisible() && sizes.length > 1 ||
               super.isVisible();
         }
      };

      for(int i = 0; i < frames.length; i++) {
         sizes[i] = ((StaticSizeFrame) frames[i]).getSize();
         framemap.put(names[i], (StaticSizeFrame) frames[i]);
         // make sure size is available for brushed dataset
         frame.setSize(BrushDataSet.ALL_HEADER_PREFIX + names[i], sizes[i]);

         // interval bar uses top column as var
         if(interval) {
            frame.setSize(IntervalDataSet.TOP_PREFIX + names[i], sizes[i]);
            frame.setSize(BrushDataSet.ALL_HEADER_PREFIX + IntervalDataSet.TOP_PREFIX + names[i],
                          sizes[i]);
         }
      }

      frame.getLegendSpec().setTitle(Catalog.getCatalog().getString("Measures"));
      frame.init(names, sizes);
      return frame;
   }

   /**
    * Get the frame for the aggregate.
    */
   public SizeFrame getFrame(String name) {
      return framemap.get(name);
   }

   private ChartInfo info;
   private Map<String,SizeFrame> framemap = new HashMap<>();
}
