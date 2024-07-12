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
package inetsoft.uql.asset;

import inetsoft.mv.RuntimeMV;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.ContentObject;

import java.awt.*;
import java.util.Enumeration;
import java.util.jar.JarOutputStream;

/**
 * Table assembly represents a table in <tt>Worksheet</tt>, which occuies an
 * area in its <tt>Worksheet</tt>, and show table data as its content.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface TableAssembly extends WSAssembly, ContentObject {
   /**
    * Get the table assembly info.
    * @return the table assembly info of the table assembly.
    */
   TableAssemblyInfo getTableInfo();

   /**
    * Get the minimum size.
    * @param embedded <tt>true</tt> to embed the table assembly.
    * @return the minimum size of the assembly.
    */
   Dimension getMinimumSize(boolean embedded);

   /**
    * Get the inner column selection.
    * @return the inner column selection of the table assembly.
    */
   ColumnSelection getColumnSelection();

   /**
    * Get the column selection.
    * @param pub <tt>true</tt> indicates the public column selection,
    * <tt>false</tt> otherwise.
    * @return the column selection of the table assembly.
    */
   ColumnSelection getColumnSelection(boolean pub);

   /**
    * Set the column selection.
    * @param selection the specified selection.
    */
   void setColumnSelection(ColumnSelection selection);

   /**
    * Set the column selection.
    * @param pub <tt>true</tt> indicates the public column selection,
    * <tt>false</tt> otherwise.
    * @param selection the specified selection.
    */
   void setColumnSelection(ColumnSelection selection, boolean pub);

   /**
    * Check if is a plain table.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isPlain();

   /**
    * Get all variables in the condition value list.
    */
   @Override
   UserVariable[] getAllVariables();

   /**
    * Get the preprocess runtime condition list.
    * @return the preprocess runtime condition list of the table assembly.
    */
   ConditionListWrapper getPreRuntimeConditionList();

   /**
    * Set the preprocess runtime condition list.
    * @param conds the specified preprocess runtime condition list.
    */
   void setPreRuntimeConditionList(ConditionListWrapper conds);

   /**
    * Get the postprocess runtime condition list.
    * @return the postprocess runtime condition list of the table assembly.
    */
   ConditionListWrapper getPostRuntimeConditionList();

   /**
    * Set the postprocess runtime condition list.
    * @param conds the specified postprocess runtime condition list.
    */
   void setPostRuntimeConditionList(ConditionListWrapper conds);

   /**
    * Get the ranking runtime condition list.
    * @return the ranking runtime condition list of the table assembly.
    */
   ConditionListWrapper getRankingRuntimeConditionList();

   /**
    * Set the ranking runtime condition list.
    * @param conds the specified ranking runtime condition list.
    */
   void setRankingRuntimeConditionList(ConditionListWrapper conds);

   /**
    * Get the preprocess condition list.
    * @return the preprocess condition list of the table assembly.
    */
   ConditionListWrapper getPreConditionList();

   /**
    * Set the preprocess condition list.
    * @param conds the specified preprocess condition list.
    */
   void setPreConditionList(ConditionListWrapper conds);

   /**
    * Get the postprocess condition list.
    * @return the postprocess condition list of the table assembly..
    */
   ConditionListWrapper getPostConditionList();

   /**
    * Set the postprocess condition list.
    * @param conds the specified postprocess condition list.
    */
   void setPostConditionList(ConditionListWrapper conds);

   /**
    * Get the ranking condition list.
    * @return the ranking condition list of the table assembly..
    */
   ConditionListWrapper getRankingConditionList();

   /**
    * Set the ranking condition list.
    * @param conds the specified ranking condition list.
    */
   void setRankingConditionList(ConditionListWrapper conds);

   /**
    * Get mv condition, merged mv update and delete conditions.
    */
   ConditionList getMVConditionList();

   /**
    * Get mv update condition, merged mv update pre and post conditions.
    */
   ConditionList getMVUpdateConditionList();

   /**
    * Get mv delete condition, merged with mv delete pre and post conditions.
    */
   ConditionListWrapper getMVDeleteConditionList();

   /**
    * Get mv update pre condition.
    */
   ConditionListWrapper getMVUpdatePreConditionList();

   /**
    * Set mv update pre condition.
    */
   void setMVUpdatePreConditionList(ConditionListWrapper conds);

   /**
    * Get mv update post condition.
    */
   ConditionListWrapper getMVUpdatePostConditionList();

   /**
    * Set mv update post condition.
    */
   void setMVUpdatePostConditionList(ConditionListWrapper conds);

   /**
    * Get mv delete pre condition.
    */
   ConditionListWrapper getMVDeletePreConditionList();

   /**
    * Set mv delete pre condition.
    */
   void setMVDeletePreConditionList(ConditionListWrapper conds);

   /**
    * Get mv delete post condition.
    */
   ConditionListWrapper getMVDeletePostConditionList();

   /**
    * Set mv delete post condition.
    */
   void setMVDeletePostConditionList(ConditionListWrapper conds);

   /**
    * Gets the flag that determines if the results of an MV update are always
    * appended to the existing data.
    *
    * @return <tt>true</tt> to force updates to be appended.
    */
   boolean isMVForceAppendUpdates();

   /**
    * Sets the flag that determines if the results of an MV update are always
    * appended to the existing data.
    *
    * @param mvForceAppendUpdates <tt>true</tt> to force updates to be appended.
    */
   void setMVForceAppendUpdates(boolean mvForceAppendUpdates);

   /**
    * Get the group info.
    * @return the group info of the table assembly..
    */
   AggregateInfo getAggregateInfo();

   /**
    * Set the group info.
    * @param ginfo the specified group info.
    */
   void setAggregateInfo(AggregateInfo ginfo);

   /**
    * Get the sort info.
    * @return the sort info of the table assembly.
    */
   SortInfo getSortInfo();

   /**
    * Set the sort info.
    * @param info the specified sort info.
    */
   void setSortInfo(SortInfo info);

   /**
    * Get the maximum rows.
    * @return the maximum rows of the table assembly.
    */
   int getMaxRows();

   /**
    * Set the maximum rows.
    * @param row the specified maximum rows.
    */
   void setMaxRows(int row);

   /**
    * Get the maximum display rows.
    * @return the maximum display rows of the table assembly.
    */
   int getMaxDisplayRows();

   /**
    * Set the maximum display rows.
    * @param row the specified maximum display rows.
    */
   void setMaxDisplayRows(int row);

   /**
    * Check if only show distinct values.
    * @return <tt>true</tt> to show distinct values only,
    * <tt>false</tt> otherwise.
    */
   boolean isDistinct();

   /**
    * Set the distinct option.
    * @param distinct <tt>true</tt> to show distinct values only,
    * <tt>false</tt> otherwise.
    */
   void setDistinct(boolean distinct);

   /**
    * Check if show live data.
    * @return <tt>true</tt> to show live data, <tt>false</tt> to show metadata.
    */
   boolean isLiveData();

   /**
    * Set the live data option.
    * @param live <tt>true</tt> to show live data, <tt>false</tt>
    * to show metadata.
    */
   void setLiveData(boolean live);

   /**
    * Check if the table is in runtime mode.
    * @return <tt>true</tt> if in runtime mode, <tt>false</tt> otherwise.
    */
   boolean isRuntime();

   /**
    * Set the runtime mode.
    * @param runtime <tt>true</tt> if in runtime mode, <tt>false</tt> otherwise.
    */
   void setRuntime(boolean runtime);

   /**
    * Check if the table is in edit mode.
    * @return <tt>true</tt> if in edit mode, <tt>false</tt> otherwise.
    */
   boolean isEditMode();

   /**
    * Set the edit mode.
    * @param editMode <tt>true</tt> if in edit mode, <tt>false</tt> otherwise.
    */
   void setEditMode(boolean editMode);

   /**
    * Check if is an aggregate.
    * @return <tt>true</tt> if is an aggregate.
    */
   boolean isAggregate();

   /**
    * Set the aggregate flag.
    * @param aggregate <tt>true</tt> if is an aggregate.
    */
   void setAggregate(boolean aggregate);

   /**
    * Check if the sql query is mergeable.
    * @return <tt>true</tt> if the sql query is mergeable, <tt>false</tt>
    * otherwise.
    */
   boolean isSQLMergeable();

   /**
    * Set whether the sql query is mergeable.
    * @param mergeable <tt>true</tt> if the sql query is mergeable,
    * <tt>false</tt> otherwise.
    */
   void setSQLMergeable(boolean mergeable);

   /**
    * Check if runtime is selected.
    * @return true if the runtime is selected, false otherwise
    */
   boolean isRuntimeSelected();

   /**
    * Set whether runtime is selected.
    * @param runtimeSelected true if runtime is selected on the table, false otherwise
    */
   void setRuntimeSelected(boolean runtimeSelected);
   
   /**
    * Check if the worksheet is block.
    * @return <tt>true</tt>  if the worksheet is block., <tt>false</tt>
    * otherwise.
    */
   boolean isVisibleTable();

   /**
    * Set whether the worksheet is block.
    * @param visibleTable <tt>true</tt> if the worksheet is block.,
    * <tt>false</tt> otherwise.
    */
   void setVisibleTable(boolean visibleTable);

   /**
    * Get the source of the table assembly.
    * @return the source of the table assembly.
    */
   String getSource();

   /**
    * Set the runtime MV.
    */
   void setRuntimeMV(RuntimeMV rinfo);

   /**
    * Get the runtime MV.
    */
   RuntimeMV getRuntimeMV();

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   boolean equalsContent(Object obj);

   /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   int getContentCode();

   /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   String getProperty(String key);

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   void setProperty(String key, String value);

   /**
    * Get the lastModified.
    * @return lastModified.
    */
   long getLastModified();

   /**
    * Set the lastModified.
    * @param lastModified
    */
   void setLastModified(long lastModified);

   /**
    * Clear property.
    * @param key the property name.
    */
   void clearProperty(String key);

   /**
    * Clear cache.
    */
   void clearCache();

   /**
    * Get all the property keys.
    * @return all the property keys.
    */
   Enumeration getProperties();

   /**
    * Print the table information.
    */
   void print(int level, StringBuilder sb);

   /**
    * Reset column selection.
    */
   void resetColumnSelection();

   /**
    * Set column changed property name.
    */
   void setColumnPropertyName(String name);

   /**
    * Write out data content of this table.
    */
   void writeData(JarOutputStream out);

   /**
    * update properties of table.
    */
   void updateTable(TableAssembly table);
}
