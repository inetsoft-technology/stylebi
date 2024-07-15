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
package inetsoft.graph.data;

/**
 * A TopDataSet should always be at the top-level, in a graph or subgraph. If SubDataSet is
 * created to be used in a subgraph, it should be wrapped in the TopDataSet.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class TopDataSet extends AbstractDataSetFilter {
   public TopDataSet(DataSet data) {
      super(data);
   }

   /**
    * Wrap a sub dataset in this data set.
    * @param base sub dataset.
    * @param proto the original TopDataSet to copy options from.
    * @return a new top dataset with base.
    */
   public abstract DataSet wrap(DataSet base, TopDataSet proto);

   /**
    * Check if calc should always be processed in sub dataset.
    */
   public boolean isCalcInSub() {
      return false;
   }
}
