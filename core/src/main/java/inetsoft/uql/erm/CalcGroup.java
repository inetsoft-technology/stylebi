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
package inetsoft.uql.erm;

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;

public interface CalcGroup extends DataRefWrapper {
   /**
    * Get the type.
    *
    * @return the type of the source info.
    */
   public int getSourceType();

   /**
    * Set the type.
    *
    * @param type the specified type.
    */
   public void setSourceType(int type);

   /**
    * Get the prefix.
    *
    * @return the prefix of the source info.
    */
   public String getSourcePrefix();

   /**
    * Set the prefix.
    *
    * @param prefix the specified prefix.
    */
   public void setSourcePrefix(String prefix);

   /**
    * Get the source.
    *
    * @return the source of the source info.
    */
   public String getSource();

   /**
    * Set the source.
    *
    * @param source the specified source.
    */
   public void setSource(String source);

   /**
    * Get the grouping ordering.
    */
   public OrderInfo getOrderInfo();

   /**
    * Set the grouping ordering.
    */
   public void setOrderInfo(OrderInfo info);

   /**
    * Get the topN definition.
    */
   public TopNInfo getTopN();

   /**
    * Set the topN definition.
    */
   public void setTopN(TopNInfo topn);

   /**
    * Check if is date type.
    *
    * @return true if is, false otherwise
    */
   public boolean isDate();

   /**
    * Check if a group should be timeSeries.
    */
   public boolean isTimeSeries();

   /**
    * Set if the group should be timeSeries.
    */
   public void setTimeSeries(boolean timeSeries);
}