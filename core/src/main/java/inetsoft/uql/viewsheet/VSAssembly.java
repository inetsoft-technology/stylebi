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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.VariableProvider;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

/**
 * VSAssembly represents one assembly contained in a <tt>Viewsheet</tt>.
 * In its <tt>Viewsheet</tt>, it will be laid out by its position and size and
 * painted in the region.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public interface VSAssembly extends Assembly, VariableProvider {
   /**
    * None changed.
    */
   int NONE_CHANGED = 0;
   /**
    * Input data changed.
    */
   int INPUT_DATA_CHANGED = 1;
   /**
    * Output data changed.
    */
   int OUTPUT_DATA_CHANGED = 2;
   /**
    * View changed.
    */
   int VIEW_CHANGED = 4;
   /**
    * Detail input data changed.
    */
   int DETAIL_INPUT_DATA_CHANGED = 8;
   /**
    * Binding changed.
    */
   int BINDING_CHANGED = 16;

   /**
    * Always show.
    */
   int ALWAYS_SHOW = 1;
   /**
    * Always hide.
    */
   int ALWAYS_HIDE = 2;
   /**
    * Hide on print.
    */
   int HIDE_ON_PRINT = 4;

   /**
    * Get the viewsheet assembly info.
    * @return the viewsheet assembly info.
    */
   VSAssemblyInfo getVSAssemblyInfo();

   /**
    * Check if the VSAssembly is resizable.
    * @return <tt>true</tt> of resizable, <tt>false</tt> otherwise.
    */
   boolean isResizable();

   /**
    * Set the parent viewsheet.
    * @param vs the specified viewsheet.
    */
   void setViewsheet(Viewsheet vs);

   /**
    * Get the parent viewsheet.
    * @return the parent viewsheet.
    */
   Viewsheet getViewsheet();

   /**
    * Check if is a primary assembly.
    * @return <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   boolean isPrimary();

   /**
    * Set whether is a primary assembly.
    * @param primary <tt>true</tt> if primary, <tt>false</tt> otherwise.
    */
   void setPrimary(boolean primary);

   /**
    * Check if this assembly is in an embedded viewsheet.
    */
   boolean isEmbedded();

   /**
    * Check if the assembly is temporary added for vs wizard,
    * and will be removed after exit vs wizard.
    * @return <tt>true</tt> if temporary, <tt>false</tt> otherwise.
    */
   boolean isWizardTemporary();

   /**
    * Set if the assembly is temporary added for vs wizard,
    * and will be removed after exit vs wizard.
    */
   void setWizardTemporary(boolean temp);

   /**
    * Check if this assembly is being editing in vs object wizard.
    */
   default boolean isWizardEditing() {
      return false;
   }

   /**
    * Set this assembly is being editing status in vs object wizard,
    * and will be removed after exit vs object wizard.
    */
   default void setWizardEditing(boolean wizardEditing) {
      // no op
   }

   /**
    * Check if the assembly is enabled.
    * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   boolean isEnabled();

   /**
    * Get the container.
    * @return the container if any, <tt>null</tt> otherwise.
    */
   VSAssembly getContainer();

   /**
    * Check if supports container.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a container, <tt>false</tt> otherwise.
    */
   boolean supportsContainer();

   /**
    * Copy the assembly.
    * @param name the specified new assembly name.
    * @return the copied assembly.
    */
   VSAssembly copyAssembly(String name);

   /**
    * Get the worksheet.
    * @return the worksheet if any.
    */
   Worksheet getWorksheet();

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   AssemblyRef[] getDependedWSAssemblies();

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   AssemblyRef[] getDependingWSAssemblies();

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   List<DynamicValue> getDynamicValues();

   /**
    * Get the dynamic property values for output options.
    * @return the dynamic values.
    */
   List<DynamicValue> getOutputDynamicValues();

   /**
    * Get the view dynamic values.
    * @param all true to include all view dynamic values. Otherwise only the
    * dynamic values need to be executed are returned.
    * @return the view dynamic values.
    */
   List<DynamicValue> getViewDynamicValues(boolean all);

   /**
    * Get the hyperlink dynamic values.
    * dynamic values need to be executed are returned.
    * @return the view dynamic values.
    */
   List<DynamicValue> getHyperlinkDynamicValues();

   /**
    * Set the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, input data or output data.
    */
   int setVSAssemblyInfo(VSAssemblyInfo info);

   /**
    * Get the assemblies depended on by its output values.
    * @param set the set stores the assemblies depended on.
    */
   void getOutputDependeds(Set<AssemblyRef> set);

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   void getViewDependeds(Set<AssemblyRef> set, boolean self);

   /**
    * Update the assembly to fill in runtime value.
    * @param columns the specified column selection.
    */
   void update(ColumnSelection columns) throws Exception;

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   void writeState(PrintWriter writer, boolean runtime);

   /**
    * Check if contains script.
    */
   boolean containsScript();

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   void parseState(Element elem) throws Exception;

   /**
    * Get the format info.
    * @return the format info of this assembly info.
    */
   FormatInfo getFormatInfo();

   /**
    * Set the format info to this assembly info.
    * @param info the specified format info.
    */
   void setFormatInfo(FormatInfo info);

   /**
    * Check if supports tab.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a tab, <tt>false</tt> otherwise.
    */
   boolean supportsTab();

   /**
    * Initialize the default format.
    */
   void initDefaultFormat();

   /**
    * Check if this data assembly only depends on selection assembly.
    * @return <tt>true</tt> if it is only changed by the selection assembly,
    * <tt>false</tt> otherwise.
    */
   boolean isStandalone();

   /**
    * Return stack order.
    */
   int getZIndex();

   /**
    * Set stack order.
    */
   void setZIndex(int zIndex);

   /**
    * Get the bound table.
    */
   String getTableName();

   /**
    * Set the tip condition list.
    */
   void setTipConditionList(ConditionListWrapper wrapper);

   /**
    * Get the tip condition list.
    */
   ConditionListWrapper getTipConditionList();

   /**
    * Get the binding datarefs.
    */
   DataRef[] getBindingRefs();

   /**
    * Get the binding datarefs.
    *
    * @param sourceColumnSelection binding source columns.
    */
   default DataRef[] getBindingRefs(ColumnSelection sourceColumnSelection) {
      return getBindingRefs();
   }

   /**
    * Get the all binding datarefs,
    * include condition/highlight/hyperlink used columns.
    */
   DataRef[] getAllBindingRefs();

   /**
   * Clear the layout state.
   */
   void clearLayoutState();
}
