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
package inetsoft.uql.viewsheet;

import inetsoft.graph.data.DataSet;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.util.XNamedGroupInfo;

import java.util.Comparator;
import java.util.List;

/**
 * A XDimensionRef object represents a dimension reference.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface XDimensionRef extends AssetObject, DataRefWrapper, VSDataRef {
   /**
    * Get the date level of this dimension reference.
    * @return the date level of this dimension reference.
    */
   public int getDateLevel();

   /**
    * Get the sort order of this dimension ref.
    * @return the sort order defined in XConstants.
    */
   public int getOrder();

   /**
    * Check if is a date type.
    * @return <tt>true</tt> if date, <tt>false</tt> otherwise.
    */
   public boolean isDate();

   /**
    * Check if is a date type.
    * @return <tt>true</tt> if date, <tt>false</tt> otherwise.
    */
   public boolean isDateTime();

   /**
    * Check if is a time series dimension.
    * @return <tt>true</tt> if is a time series dimension, <tt>false</tt>
    * otherwise.
    */
   public boolean isTimeSeries();

   /**
    * Set the date level of this dimension reference. The date level options
    * are defined in DataRangeRef (e.g. YEAR_INTERVAL).
    * @param dlevel the date level of this dimension reference.
    */
   public void setDateLevel(int dlevel);

   /**
    * Set the sort order to this dimension ref.
    * @order the sort order defined in XConstants.
    */
   public void setOrder(int order);

   public List getManualOrderList();

   /**
    * Set the manual order list of the dimension ref.
    */
   public void setManualOrderList(List list);

   /**
    * Set the time series option.
    */
   public void setTimeSeries(boolean ts);

   /**
    * Get the column to sort by.
    */
   public String getSortByCol();

   /**
    * Set the column to sort by.
    */
   public void setSortByCol(String col);

   /**
    * Get the ranking column of this dimension reference.
    * @return the ranking column of this dimension reference.
    */
   public String getRankingCol();

   /**
    * Set the ranking column of this dimension reference.
    * @param col the ranking column of this dimension reference.
    */
   public void setRankingCol(String col);

   /**
    * Get the runtime ranking option value of this dimension reference.
    */
   public int getRankingOption();

   /**
    * Get the runtime ranking n value of this dimension reference.
    */
   public int getRankingN();

   /**
    * Create the associated comparer for this dimension ref.
    */
   public Comparator createComparator(DataSet data);

   /**
    * Get the dates for period comparison.
    * @return the dates.
    */
   public String[] getDates();

   /**
    * Get the named group definition.
    */
   public XNamedGroupInfo getNamedGroupInfo();

   /**
    * Get the named group definition.
    */
   default XNamedGroupInfo getRealNamedGroupInfo() {
      return getNamedGroupInfo();
   }

   default boolean isNamedGroupAvailable() {
      return !isTimeSeries() && (getOrder() & XConstants.SORT_SPECIFIC) != 0 &&
         getRealNamedGroupInfo() != null;
   }

   /**
    * Set the named group definition.
    */
   public void setNamedGroupInfo(XNamedGroupInfo info);

    /**
     * Check if 'Others' group should always be sorted as the last item.
     */
    boolean isSortOthersLast();

    /**
     * Set if 'Others' group should always be sorted as the last item.
     */
    void setSortOthersLast(boolean sortOthersLast);

   /**
    * Copy the dimension options such as sorting and topN.
    */
   public void copyOptions(XDimensionRef ref);

    /**
    * Check if others should be grouped or discarded.
    */
   public boolean isGroupOthers();

   /**
    * Check if this dimension defines ranking and group others.
    */
   default boolean isRankingGroupOthers() {
      return isGroupOthers() &&
         (getRankingOption() == XCondition.TOP_N || getRankingOption() == XCondition.BOTTOM_N);
   }
}
