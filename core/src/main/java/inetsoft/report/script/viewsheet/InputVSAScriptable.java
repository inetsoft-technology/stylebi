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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.script.NativeJavaArray2;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * The input viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class InputVSAScriptable extends VSAScriptable {
   /**
    * Create an input viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public InputVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "InputVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof InputVSAssembly)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }

   @Override
   protected boolean hasActions() {
      return false;
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null : vs.getAssembly(assembly);

      if(assembly == null) {
         return;
      }

      VSAssemblyInfo info = vassembly.getVSAssemblyInfo();

      if(!(info instanceof InputVSAssemblyInfo)) {
         return;
      }

      if(vassembly instanceof SingleInputVSAssembly &&
         !(vassembly instanceof TextInputVSAssembly))
      {
         addProperty("selectedObject", "getSelectedObject", "setSelectedObject",
                     Object.class, getClass(), this);
         addProperty("selectedLabel", "getSelectedLabel", null,
                     Object.class, InputVSAssemblyInfo.class, info);
      }

      if(vassembly instanceof CompositeInputVSAssembly) {
         addProperty("selectedObjects", "getSelectedObjectsArray", "setSelectedObjects",
                     Object[].class, getClass(), this);
         addProperty("selectedLabels", "getSelectedLabelsArray", null,
                     String[].class, getClass(), this);
      }

      if(vassembly instanceof ListInputVSAssembly) {
         addProperty("values", "getValues", "setValues", Object[].class, getClass(), this);
         addProperty("labels", "getLabels", "setLabels", String[].class, getClass(), this);
         addProperty("datatype", "getDataType", "setDataType", String.class, getClass(), this);
         addProperty("sortType", "getSortType", "setSortType",
            int.class, ListInputVSAssemblyInfo.class, info);
         addProperty("sortByValue", "isSortByValue", "setSortByValue",
            boolean.class, ListInputVSAssemblyInfo.class, info);
         addProperty("embeddedDataOnBottom", "isEmbeddedDataDown",
            "setEmbeddedDataDown", boolean.class, ListInputVSAssemblyInfo.class, info);
      }

      addProperty("submitOnChange", "isSubmitOnChange", "setSubmitOnChange",
         boolean.class, InputVSAssemblyInfo.class, info);
      addProperty("refreshAfterSubmit", "isRefresh", "setRefresh",
                  boolean.class, info.getClass(), info);

      if (vassembly instanceof TextInputVSAssembly) {
         addProperty("defaultText","getDefaultText","setDefaultText",String.class,info.getClass(), info);
         addProperty("placeholderText","getToolTip","setToolTip",String.class,info.getClass(), info);
      }
   }

   /**
    * Set the data type.
    * @param datatype the data type of the list input.
    */
   public void setDataType(String datatype) {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof ListInputVSAssembly) {
         ListInputVSAssemblyInfo listInfo = (ListInputVSAssemblyInfo) vassembly.getVSAssemblyInfo();
         ListData listData = listInfo.getListData();
         listInfo.setDataType(datatype);

         if(listData != null) {
            listData.setDataType(datatype);
         }
      }
   }

   /**
    * Get the datatype.
    * @return the data type of the list input.
    */
   public String getDataType() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof ListInputVSAssembly) {
         VSAssemblyInfo info = vassembly.getVSAssemblyInfo();
         ListData listData = ((ListInputVSAssemblyInfo) info).getListData();

         return listData == null ? null : listData.getDataType();
      }

      return null;
   }

   /**
    * Set the values.
    * @param values the values of the list input.
    */
   public void setValues(Object[] values) {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof ListInputVSAssembly) {
         VSAssemblyInfo info = vassembly.getVSAssemblyInfo();
         ListData listData = ((ListInputVSAssemblyInfo) info).getRListData();

         if(listData != null) {
            listData.setValues(values);
         }
      }
   }

   /**
    * Get the values.
    * @return the values of the list input.
    */
   public Object[] getValues() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof ListInputVSAssembly) {
         VSAssemblyInfo info = vassembly.getVSAssemblyInfo();
         ListData listData = ((ListInputVSAssemblyInfo) info).getRListData();

         return listData == null ? null : listData.getValues();
      }

      return null;
   }

   /**
    * Set the labels.
    * @param labels the labels of the list input.
    */
   public void setLabels(String[] labels) {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof ListInputVSAssembly) {
         VSAssemblyInfo info = vassembly.getVSAssemblyInfo();
         ListData listData = ((ListInputVSAssemblyInfo) info).getRListData();

         if(listData != null) {
            listData.setLabels(labels);
         }
      }
   }

   /**
    * Get the labels.
    * @return the labels of the list input.
    */
   public String[] getLabels() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof ListInputVSAssembly) {
         VSAssemblyInfo info = vassembly.getVSAssemblyInfo();
         ListData listData = ((ListInputVSAssemblyInfo) info).getRListData();
         return listData == null ? null : listData.getLabels();
      }

      return null;
   }

   /**
    * This function is called if the referenced is used without any indexing,
    * e.g. name + 2
    */
   @Override
   public Object getDefaultValue(Class hint) {
      VSAssembly vassembly = getVSAssembly();

      if(!(vassembly instanceof InputVSAssembly)) {
         return Undefined.instance;
      }

      if(vassembly instanceof SingleInputVSAssembly) {
         return ((SingleInputVSAssembly) vassembly).getSelectedObject();
      }
      else if(vassembly instanceof CompositeInputVSAssembly) {
         return ((CompositeInputVSAssembly) vassembly).getSelectedObjects();
      }

      return Undefined.instance;
   }

   public Object getSelectedObject() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof InputVSAssembly) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) vassembly.getVSAssemblyInfo();
         return info.getSelectedObject();
      }

      return null;
   }

   public void setSelectedObject(Object obj) {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof InputVSAssembly) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) vassembly.getVSAssemblyInfo();
         info.setSelectedObject(Tool.getData(info.getDataType(), obj));
      }
   }

   public Object getSelectedObjectsArray() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof InputVSAssembly) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) vassembly.getVSAssemblyInfo();
         return new NativeJavaArray2(info.getSelectedObjects(), getParentScope());
      }

      return null;
   }

   public void setSelectedObjects(Object[] objs) {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof InputVSAssembly) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) vassembly.getVSAssemblyInfo();
         info.setSelectedObjects(objs);
      }
   }

   public Object getSelectedLabelsArray() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly instanceof InputVSAssembly) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) vassembly.getVSAssemblyInfo();
         return new NativeJavaArray2(info.getSelectedLabels(), getParentScope());
      }

      return null;
   }
}
