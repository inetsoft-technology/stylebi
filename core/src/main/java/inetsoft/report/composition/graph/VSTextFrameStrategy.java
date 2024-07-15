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
package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.*;

/**
 * VS text frame strategy.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VSTextFrameStrategy implements VSFrameStrategy {
   /**
    * Create an instance of VSTextFrameStrategy.
    */
   public VSTextFrameStrategy(ChartInfo info) {
      super();

      this.info = info;
   }

   /**
    * Get the AestheticRef to be visited in the specified ChartInfo.
    */
   @Override
   public AestheticRef getAestheticRef(ChartAggregateRef aggr) {
      return (aggr != null) ? aggr.getTextField() : 
         info.isMultiAesthetic() ? null : info.getTextField();
   }

   /**
    * Check if supports getting frame from ChartInfo.
    */
   @Override
   public boolean supportsGeneralFrame() {
      return false;
   }

   /**
    * Get the VisualFrame to be visited in the ChartInfo.
    */
   @Override
   public VisualFrame getGeneralFrame() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if supports getting frame from chart aggregate ref.
    */
   @Override
   public boolean supportsFieldFrame() {
      return false;
   }

   /**
    * Get the VisualFrame to be visited in the specified ChartAggregateRef.
    */
   @Override
   public VisualFrame getFieldFrame(ChartAggregateRef ref) {
      throw new RuntimeException("Unsupported method called!");
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
                                          boolean same, boolean force)
   {
      throw new RuntimeException("Unsupported method called!");
   }

   private ChartInfo info;
}
