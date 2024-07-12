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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SelectionVSAssembly represents one selection assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class AbstractSelectionVSAssembly extends AbstractVSAssembly
   implements SelectionVSAssembly
{
   /**
    * Constructor.
    */
   public AbstractSelectionVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public AbstractSelectionVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the SelectionVSAssemblyInfo.
    * @return the SelectionVSAssemblyInfo.
    */
   protected SelectionVSAssemblyInfo getSelectionVSAssemblyInfo() {
      return (SelectionVSAssemblyInfo) getInfo();
   }

   /**
    * Check binding source type.
    */
   public int getSourceType() {
      return getSelectionVSAssemblyInfo().getSourceType();
   }

   /**
    * Set binding source type.
    */
   public void setSourceType(int sourceType) {
      getSelectionVSAssemblyInfo().setSourceType(sourceType);
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      if(getSourceType() == XSourceInfo.VS_ASSEMBLY) {
         String ass = getTableName();
         Viewsheet vs = getViewsheet();

         if(vs != null && ass != null) {
            Assembly assembly = vs.getAssembly(ass);

            if(assembly instanceof VSAssembly) {
               return ((VSAssembly) assembly).getDependedWSAssemblies();
            }
         }
      }
      else {
         String table = getSelectionTableName();
         Worksheet ws = getWorksheet();
         Assembly assembly = ws == null || table == null ? null : ws.getAssembly(table);

         if(assembly instanceof TableAssembly) {
            return new AssemblyRef[] {new AssemblyRef(AssemblyRef.INPUT_DATA,
               assembly.getAssemblyEntry())};
         }
      }

      return new AssemblyRef[0];
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      if(getSourceType() == XSourceInfo.VS_ASSEMBLY) {
         String ass = getTableName();
         Viewsheet vs = getViewsheet();

         if(vs != null && ass != null) {
            Assembly assembly = vs.getAssembly(ass);

            if(assembly instanceof VSAssembly) {
               return ((VSAssembly) assembly).getDependingWSAssemblies();
            }
         }
      }
      else {
         final List<String> tableNames = new ArrayList<>(getTableNames());
         Worksheet ws = getWorksheet();

         if(ws != null) {
            return tableNames.stream()
               .filter(Objects::nonNull)
               .map(ws::getAssembly)
               .filter(TableAssembly.class::isInstance)
               .map(TableAssembly.class::cast)
               .map((table) -> new AssemblyRef(AssemblyRef.OUTPUT_DATA, table.getAssemblyEntry()))
               .toArray(AssemblyRef[]::new);
         }
      }

      return new AssemblyRef[0];
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName() {
      return getSelectionVSAssemblyInfo().getTableName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      getSelectionVSAssemblyInfo().setTableName(table);
   }

   /**
    * Get the selection table name.
    * @return the selection table name.
    */
   @Override
   public String getSelectionTableName() {
      return (getTableName() == null) ? null : SELECTION + getTableName();
   }

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReset() {
      return false;
   }

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   @Override
   public int setVSAssemblyInfo(VSAssemblyInfo info) {
      int hint = super.setVSAssemblyInfo(info);

      if((hint & VSAssembly.INPUT_DATA_CHANGED) ==
         VSAssembly.INPUT_DATA_CHANGED)
      {
         resetSelection();
      }

      return hint;
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @return the string to represent the selected value.
    */
   @Override
   public String getDisplayValue(boolean onlyList) {
      return getDisplayValue(true, "; ");
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @param separator the specified separator.
    * @return the string to represent the selected value.
    */
   public String getDisplayValue(boolean onlyList, String separator) {
      if(!isEnabled() && onlyList) {
         return null;
      }

      SelectionList list = this instanceof AssociatedSelectionVSAssembly ?
         ((AssociatedSelectionVSAssembly) this).getSelectionList() : null;

      if(list != null) {
         SelectionValue[] values = list.getSelectionValues();

         if(values != null) {
            StringBuilder buff = new StringBuilder();
            boolean first = true;

            for(int j = 0; j < values.length; j++) {
               if(isVisibleValue(values[j])){
                  if(!first) {
                     buff.append(separator);
                  }

                  first = false;
                  buff.append(values[j].getLabel());
               }
            }

            return first ? null : buff.toString();
         }
      }

      return null;
   }

   /**
    * Check if a value should be in the exported view.
    */
   protected boolean isVisibleValue(SelectionValue value) {
      return !value.isExcluded() && (value.isSelected() || value.isIncluded());
   }

   /**
    * Get selection set.
    */
   protected SelectionSet getSelectionSet(DataRef ref, List<Object> list) {
      final SelectionSet selectionSet = createSelectionSet(ref);
      selectionSet.addAll(list);
      return selectionSet;
   }

   /**
    * Create a new selection set.
    */
   protected SelectionSet createSelectionSet(DataRef ref) {
      return (ref.getRefType() & DataRef.CUBE) == DataRef.CUBE ?
         new CubeSelectionSet() : new SelectionSet();
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @param print is print mode or not.
    * @return the string to represent the selected value.
    */
   public String getDisplayValue(boolean onlyList, boolean print) {
      String value = getDisplayValue(onlyList);

      // value is "", should not treat as none
      if(print && value == null &&
         getContainer() instanceof CurrentSelectionVSAssembly)
      {
         value = Catalog.getCatalog().getString("(none)");
      }

      return value;
   }

   @Override
   public DataRef[] getBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();

      DataRef[] refs = getDataRefs();

      if(refs != null) {
         datarefs.addAll(Arrays.asList(refs));
      }

      if(getInfo() instanceof SelectionBaseVSAssemblyInfo) {
         String measure = ((SelectionBaseVSAssemblyInfo) getInfo()).getMeasure();

         if(measure != null) {
            datarefs.add(new ColumnRef(new AttributeRef(measure)));
         }
      }

      return datarefs.toArray(new DataRef[] {});
   }

   /**
    * Check if a selection is made on this assembly.
    */
   @Override
   public abstract boolean containsSelection();

   /**
    * Save the assembly's properties for adhoc filter.
    */
   public void setAhFilterProperty(Map<String, Object> prop) {
      ahFilterProperty = prop;
   }

   /**
    * Get the saved properties for adhoc filter.
    */
   public Map<String, Object> getAhFilterProperty() {
      return ahFilterProperty;
   }

   /**
    * Get the measure column for displaying the value for selection items.
    */
   @Override
   public String getMeasure() {
      return null;
   }

   @Override
   public String getMeasureValue() {
      return null;
   }

   /**
    * Get the formula for aggregating the measure column.
    */
   @Override
   public String getFormula() {
      return null;
   }

   @Override
   public String getFormulaValue() {
      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public List<String> getTableNames() {
      return getSelectionVSAssemblyInfo().getTableNames();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setTableNames(List<String> tableNames) {
      getSelectionVSAssemblyInfo().setTableNames(tableNames);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public List<String> getSelectionTableNames() {
      return getTableNames().stream()
         .map((tname) -> SELECTION + tname)
         .collect(Collectors.toList());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isSelectionUnion() {
      return getSelectionVSAssemblyInfo().isSelectionUnion();
   }

   /**
    * Change calc type: detail and aggregate.
    */
   @Override
   public void changeCalcType(String refName, CalculateRef ref) {
      if(ref.isBaseOnDetail()) {
         return;
      }

      removeBindingCol(refName);
   }

   /**
    * get the array of selected values defined in javascript
    */
   public Object[] getScriptSelectedValues() {
      return null;
   }

   private Map<String, Object> ahFilterProperty;
}
