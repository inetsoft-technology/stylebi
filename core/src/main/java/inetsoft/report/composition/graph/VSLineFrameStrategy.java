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
import inetsoft.graph.data.DataSet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;

/**
 * VS line frame strategy.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VSLineFrameStrategy implements VSFrameStrategy {
   /**
    * Create an instance of VSLineFrameStrategy.
    */
   public VSLineFrameStrategy(ChartInfo info) {
      super();

      this.info = info;
   }

   /**
    * Get the AestheticRef to be visited in the specified ChartInfo.
    */
   @Override
   public AestheticRef getAestheticRef(ChartAggregateRef aggr) {
      AestheticRef ref = (aggr != null) ? aggr.getShapeField() : 
         info.isMultiAesthetic() ? null : info.getShapeField();

      if(ref != null && ref.getVisualFrame() instanceof LineFrame) {
         return ref;
      }

      return null;
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
      return info.getLineFrame();
   }

   /**
    * Check if supports getting frame from chart aggregate ref.
    */
   @Override
   public boolean supportsFieldFrame() {
      return info.supportsShapeFieldFrame();
   }

   /**
    * Get the VisualFrame to be visited in the specified ChartAggregateRef.
    */
   @Override
   public VisualFrame getFieldFrame(ChartAggregateRef ref) {
      return ref.getLineFrame();
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
      final GLine[] lines = new GLine[frames.length];

      for(int i = 0; i < frames.length; i++) {
         lines[i] = ((StaticLineFrame) frames[i]).getLine(null);
      }

      CategoricalLineFrame frame = new CategoricalLineFrame() {
         @Override
         public boolean isVisible() {
            return force && getLegendSpec().isVisible() &&
               // don't show legend if there is only one item
               // and it's the default style
               (lines.length > 1 || 
                lines.length == 1 && !GLine.THIN_LINE.equals(lines[0]))
               || super.isVisible();
         }

         @Override
         public GLine getLine(DataSet data, String col, int row) {
            col = getField() == null ?
               GraphUtil.getOriginalCol(col) : col;
            return super.getLine(data, col, row);
         }
      };

      frame.getLegendSpec().setTitle(Catalog.getCatalog().getString("Measures"));
      frame.init(names, lines);
      return frame;
   }

   private ChartInfo info;
}
