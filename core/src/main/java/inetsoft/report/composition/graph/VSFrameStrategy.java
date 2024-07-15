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

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.viewsheet.graph.AestheticRef;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;

import java.io.Serializable;

/**
 * VSFrame strategy.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public interface VSFrameStrategy extends Serializable {
   /**
    * Get the AestheticRef to be visited in the specified ChartInfo.
    * @param ref the measure to get per measure aesthetic ref or null to
    * get the global aesthetic ref.
    */
   public AestheticRef getAestheticRef(ChartAggregateRef ref);

   /**
    * Check if supports getting frame from ChartInfo.
    */
   public boolean supportsGeneralFrame();

   /**
    * Get the VisualFrame to be visited in the ChartInfo.
    */
   public VisualFrame getGeneralFrame();

   /**
    * Check if supports getting frame from chart aggregate ref.
    */
   public boolean supportsFieldFrame();

   /**
    * Get the VisualFrame to be visited in the specified ChartAggregateRef.
    */
   public VisualFrame getFieldFrame(ChartAggregateRef ref);

   /**
    * Get the summary VisualFrame to be visited in the specified
    * ChartAggregateRef.
    */
   public VisualFrame getSummaryFrame(ChartAggregateRef ref);

   /**
    * Create combined frames.
    * @param same true if the names come from the same data ref.
    */
   public VisualFrame createCombinedFrame(String[] names, VisualFrame[] frames,
                                          boolean same, boolean force);
}
