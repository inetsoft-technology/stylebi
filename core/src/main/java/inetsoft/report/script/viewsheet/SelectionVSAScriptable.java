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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.script.NativeJavaArray2;
import org.mozilla.javascript.NativeJavaArray;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;

/**
 * The selection viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionVSAScriptable extends VSAScriptable {
   /**
    * Create a selection viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SelectionVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "SelectionVSA";
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("selectedObjects".equals(prop) || "singleSelectionLevels".equals(prop)) {
         return "[]";
      }

      return super.getSuffix(prop);
   }

   /**
    * Add the title visible assembly property.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      SelectionVSAssemblyInfo info = getSelectionVSAssemblyInfo();

      addProperty("query", "getQuery", "setQuery", String.class, getClass(), this);
      addProperty("queries", "getQueries", "setQueries", String[].class, getClass(), this);
      addProperty("fields", "getFields", "setFields", Object[].class, getClass(), this);

      if(!(info instanceof TimeSliderVSAssemblyInfo)) {
         addProperty("titleVisible", "isTitleVisible", "setTitleVisible",
            boolean.class, info.getClass(), info);
      }

      if(info instanceof SelectionBaseVSAssemblyInfo) {
         addProperty("measure", "getMeasure", "setMeasure", String.class, info.getClass(), info);
         addProperty("formula", "getFormula", "setFormula", String.class, info.getClass(), info);
         addProperty("showText", "isShowText", "setShowText", boolean.class, info.getClass(), info);
         addProperty("showBar", "isShowBar", "setShowBar", boolean.class, info.getClass(), info);
      }
   }

   /**
    * Get fix size.
    */
   protected Dimension getFixSize(SelectionVSAssemblyInfo info, int height) {
      Dimension dim = new Dimension();
      dim.width = info.getPixelSize().width;
      dim.height = Math.max(height * AssetUtil.defh, 6 * AssetUtil.defh);

      return dim;
   }

   public boolean isSingleSelection() {
      return getSelectionVSAssemblyInfo().isSingleSelection();
   }

   public void setSingleSelection(boolean single) {
      if(single && getSelectedObjects() != null &&
         getSelectedObjects().length > 1 && getSelectedObjects()[0] != null)
      {
         setSelectedObjects(new Object[] {getSelectedObjects()[0]});
      }

      getSelectionVSAssemblyInfo().setSingleSelection(single);
   }

   public void setSingleSelectionValue(boolean single) {
      setSingleSelection(single);
      getSelectionVSAssemblyInfo().setSingleSelectionValue(single);
   }

   public boolean isSelectFirstItem() {
      return getSelectionVSAssemblyInfo().isSelectFirstItem();
   }

   public void setSelectFirstItem(boolean selectFirstItem) {
      getSelectionVSAssemblyInfo().setSelectFirstItem(selectFirstItem);
   }

   /**
    * Set selected objects.
    */
   public void setSelectedObjects(Object[] values) {
   }

   /**
    * Get selected objects.
    */
   public Object[] getSelectedObjects() {
      return new Object[] {};
   }

   /**
    * Get Query.
    */
   public String getQuery() {
      return getSelectionVSAssemblyInfo().getFirstTableName();
   }

   /**
    * Set Query.
    */
   public void setQuery(String source) {
      SelectionVSAssemblyInfo info = getSelectionVSAssemblyInfo();
      info.setFirstTableName(source);
      info.setAdditionalTableNames(Collections.emptyList());
   }

   /**
    * @return the selection assembly's table queries.
    */
   public String[] getQueries() {
      return getSelectionVSAssemblyInfo().getTableNames().toArray(new String[0]);
   }

   /**
    * Set the selection assembly's table queries.
    *
    * @param queries the queries to set.
    */
   public void setQueries(String[] queries) {
      final SelectionVSAssemblyInfo info = getSelectionVSAssemblyInfo();

      if(queries == null) {
         info.setTableNames(Collections.emptyList());
      }
      else {
         info.setTableNames(Arrays.asList(queries));
      }
   }

   /**
    * Set Fields.
    */
   public void setFields(Object[] fields) {
   }

   /**
    * Return a JS native array of the selectedObjects(). The Array.includes() doesn't work
    * when called on Java array when it's converted to JS array at runtime. (64335)
    */
   public NativeJavaArray getSelectedObjectsArray() {
      Object[] arr = getSelectedObjects();
      return new NativeJavaArray2(arr, getParentScope());
   }

    /**
    * Get Fields.
    */
   public Object[] getFields() {
      return new Object[] {};
   }

   private SelectionVSAssemblyInfo getSelectionVSAssemblyInfo() {
      return (SelectionVSAssemblyInfo) getVSAssemblyInfo();
   }
}
