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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.Hyperlink;
import inetsoft.report.filter.HighlightGroup;

/**
 * Interface for a merged chart info class which maintains binding info of chart
 * which containts highligh and hyperlink.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface MergedChartInfo extends ChartInfo {
   /**
    * Set the Highlight group.
    */
   public void setHighlightGroup(HighlightGroup group);

   /**
    * Set the Hyperlink.
    */
   public void setHyperlink(Hyperlink link);
}
