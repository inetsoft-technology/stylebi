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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;

/**
 * VS texture frame strategy.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VSTextureFrameStrategy implements VSFrameStrategy {
   /**
    * Create an instance of VSTextureFrameStrategy.
    */
   public VSTextureFrameStrategy(ChartInfo info) {
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

      if(ref != null && ref.getVisualFrame() instanceof TextureFrame) {
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
      return info.getTextureFrame();
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
      return ref.getTextureFrame();
   }

   /**
    * Get the summary VisualFrame to be visited in the specified
    * ChartAggregateRef.
    */
   @Override
   public VisualFrame getSummaryFrame(ChartAggregateRef ref) {
      return ref.getSummaryTextureFrame();
   }

   /**
    * Create combined frames.
    */
   @Override
   public VisualFrame createCombinedFrame(String[] names, VisualFrame[] frames,
                                          boolean same, final boolean force)
   {
      final GTexture[] texts = new GTexture[frames.length];

      for(int i = 0; i < frames.length; i++) {
         texts[i] = ((StaticTextureFrame) frames[i]).getTexture(null);
      }

      CategoricalTextureFrame frame = new CategoricalTextureFrame() {
         @Override
         public boolean isVisible() {
            return force && getLegendSpec().isVisible() &&
               // don't show a texture legend if there is only one item
               // and it has no special texture defined
               (texts.length > 1 || texts.length == 1 && texts[0] != null)
               || super.isVisible();
         }

         @Override
         public GTexture getTexture(DataSet data, String col, int row) {
            col = getField() == null ? GraphUtil.getOriginalCol(col) : col;
            return super.getTexture(data, col, row);
         }
      };

      frame.getLegendSpec().setTitle(Catalog.getCatalog().getString("Measures"));
      frame.init(names, texts);
      return frame;
   }

   private ChartInfo info;
}
