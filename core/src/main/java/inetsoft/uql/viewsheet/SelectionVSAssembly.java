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

import inetsoft.uql.ConditionList;
import inetsoft.uql.erm.DataRef;

import java.util.*;

/**
 * SelectionVSAssembly represents one selection assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface SelectionVSAssembly extends BindableVSAssembly {
   /**
    * Prefix for the selection path set name.
    */
   public static final String SELECTION_PATH = "SELECTION_PATH__";
   /**
    * Prefix for the range condition name.
    */
   public static final String RANGE = "RANGE__";

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName();

   /**
    * Get the condition list.
    * @return the condition list.
    */
   public ConditionList getConditionList();

   /**
    * Gets the condition list.
    *
    * @param dataRefs the columns to be used in the generated conditions.
    *
    * @return the condition list.
    */
   public ConditionList getConditionList(DataRef[] dataRefs);

   /**
    * Get the selection.
    * @param map the container contains the selection of this selection
    * viewsheet assembly.
    * @param applied true to include only selections that are not excluded
    * from the filtering.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   public boolean getSelection(Map<String, Map<String, Collection<Object>>> map, boolean applied);

   /**
    * Reset the selection.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   public boolean resetSelection();

   /**
    * Get the data refs.
    * @return the data refs.
    */
   public DataRef[] getDataRefs();

   /**
    * Get the selection table name.
    * @return the selection table name.
    */
   public String getSelectionTableName();

   /**
    * Check if requires reset.
    * @return <tt>true</tt> if requires reset, <tt>false</tt> otherwise.
    */
   public boolean requiresReset();

   /**
    * Copy the state selection from a selection viewsheet assembly.
    * @param assembly the specified selection viewsheet assembly.
    * @return the changed hint.
    */
   public int copyStateSelection(SelectionVSAssembly assembly);

   /**
    * Check if contains selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains selection, <tt>false</tt>
    * otherwise.
    */
   public boolean containsSelection();

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @return the string to represent the selected value.
    */
   public String getDisplayValue(boolean onlyList);

   /**
    * Get the measure column for displaying the value for selection items.
    */
   public String getMeasure();

   /**
    * Get the (design time) measure binding for displaying the value
    * for selection items.
    */
   public String getMeasureValue();

   /**
    * Get the formula for aggregating the measure column.
    */
   public String getFormula();

   /**
    * Get the formula for aggregating the measure column.
    */
   public String getFormulaValue();

   /**
    * Get the list of tables this selection applies to.
    */
   List<String> getTableNames();

   /**
    * Set the list of tables this selection applies to.
    */
   void setTableNames(List<String> tableNames);

   /**
    * Get the selection table names.
    */
   List<String> getSelectionTableNames();

   /**
    * @return <tt>true</tt> if this selection applies to multiple tables, <tt>false</tt> otherwise.
    */
   boolean isSelectionUnion();
}
