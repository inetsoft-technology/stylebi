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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.internal.ListInputVSAssemblyInfo;

/**
 * ListInputVSAssembly represents one list input assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class ListInputVSAssembly extends InputVSAssembly {
   /**
    * None source type.
    */
   public static final int NONE_SOURCE = 0;
   /**
    * Embedded source type.
    */
   public static final int EMBEDDED_SOURCE = 1;
   /**
    * Bound source type.
    */
   public static final int BOUND_SOURCE = 2;
   /**
    * Merge source type.
    */
   public static final int MERGE_SOURCE = 3;

   /**
    * Constructor.
    */
   public ListInputVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ListInputVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the list data.
    * @return the list data of this assembly info.
    */
   public ListData getListData() {
      return getListInputInfo().getListData();
   }

   /**
    * Set the list data to this assembly info.
    * @param data the specified list data.
    */
   public void setListData(ListData data) {
      getListInputInfo().setListData(data);
   }

   /**
    * Get the runtime list data.
    * @return the list data of this assembly info.
    */
   public ListData getRListData() {
      return getListInputInfo().getRListData();
   }

   /**
    * Set the runtime list data to this assembly info.
    * @param rdata the runtime list data.
    */
   public void setRListData(ListData rdata) {
      getListInputInfo().setRListData(rdata);
   }

   /**
    * Get the source type.
    * @return the source type of this assembly info.
    */
   public int getSourceType() {
      return getListInputInfo().getSourceType();
   }

   /**
    * Set the source type to this assembly info.
    * @param stype the specified source type.
    */
   public void setSourceType(int stype) {
      getListInputInfo().setSourceType(stype);
   }

   /**
    * Get the binding info.
    * @return the binding info of this assembly info.
    */
   public BindingInfo getBindingInfo() {
      return getListInputInfo().getBindingInfo();
   }

   /**
    * Check if is sort by value
    */
   public boolean isSortByValue() {
      return getListInputInfo().isSortByValue();
   }

   /**
    * Set sort by value
    */
   public void setSortByValue(boolean sortByValue) {
      getListInputInfo().setSortByValue(sortByValue);
   }

   /**
    * Get the sort type.
    * @return the sort type of this assembly info.
    */
   public int getSortType() {
      return getListInputInfo().getSortType();
   }

   /**
    * Set the sort type to this assembly info.
    * @param sortType the specified sort type.
    */
   public void setSortType(int sortType) {
      getListInputInfo().setSortType(sortType);
   }

   /**
    * Set the embedded data top or bottom.
    */
   public void setEmbeddedDataDown(boolean edataDown) {
      getListInputInfo().setEmbeddedDataDown(edataDown);
   }

   /**
    * Check whether the embedded data top or bottom.
    */
   public boolean isEmbeddedDataDown() {
      return getListInputInfo().isEmbeddedDataDown();
   }

   /**
    * Get the list binding info.
    * @return the list binding info of this assembly info.
    */
   public ListBindingInfo getListBindingInfo() {
      return getListInputInfo().getListBindingInfo();
   }

   /**
    * Set the list binding info to this assembly info.
    * @param binding the specified list binding info.
    */
   public void setListBindingInfo(ListBindingInfo binding) {
      getListInputInfo().setListBindingInfo(binding);
   }

   /**
    * Get the bound table name.
    */
   public String getBoundTableName() {
      int source = getSourceType();

      // no binding?
      if(source == ListInputVSAssembly.NONE_SOURCE) {
         return null;
      }
      // embedded list data?
      else if(source == ListInputVSAssembly.EMBEDDED_SOURCE) {
         return null;
      }

      ListBindingInfo binding = getListBindingInfo();
      return binding == null ? null : binding.getTableName();
   }

   /**
    * Get the values of this assembly info.
    * @return the values of this assembly info.
    */
   public Object[] getValues() {
      return getListInputInfo().getValues();
   }

   /**
    * Set the values of this assembly info.
    * @param values the values of this assembly info.
    */
   public void setValues(Object[] values) {
      getListInputInfo().setValues(values);
   }

   /**
    * Get the labels of this assembly info.
    * @return the labels of this assembly info.
    */
   public String[] getLabels() {
      return getListInputInfo().getLabels();
   }

   /**
    * Set the labels of this assembly info.
    * @param labels the labels of this assembly info.
    */
   public void setLabels(String[] labels) {
      getListInputInfo().setLabels(labels);
   }

   /**
    * Get the viewsheet formats.
    * @return the viewsheet formats.
    */
   public VSCompositeFormat[] getFormats() {
      return getListInputInfo().getFormats();
   }

   /**
    * Set the viewsheet formats.
    * @param formats the specified viewsheet formats.
    */
   public void setFormats(VSCompositeFormat[] formats) {
      getListInputInfo().setFormats(formats);
   }

   /**
    * Get list input assembly info.
    * @return the list input assembly info.
    */
   protected ListInputVSAssemblyInfo getListInputInfo() {
      return (ListInputVSAssemblyInfo) getInfo();
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      String table = getBoundTableName();

      if(table != null) {
         Worksheet ws = getWorksheet();
         Assembly assembly = ws == null || table == null ? null :
            ws.getAssembly(table);

         if(assembly instanceof TableAssembly) {
            return new AssemblyRef[] {new AssemblyRef(AssemblyRef.INPUT_DATA,
               assembly.getAssemblyEntry())};
         }
      }

      return new AssemblyRef[0];
   }

   /**
    * Clear the selected objects.
    */
   public void clearSelectedObjects() {
      getListInputInfo().clearSelectedObjects();
   }

   /**
    * Validate the selected object.
    */
   public void validate() {
      getListInputInfo().validate();
   }
}
