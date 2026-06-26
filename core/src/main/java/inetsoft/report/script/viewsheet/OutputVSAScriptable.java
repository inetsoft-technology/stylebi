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

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The output viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class OutputVSAScriptable extends VSAScriptable {
   /**
    * Create an output viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public OutputVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "OutputVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object getMember(String name) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null : vs.getAssembly(assembly);

      if(!(vassembly instanceof OutputVSAssembly)) {
         return null;
      }

      if("value".equals(name)) {
         if(!executed) {
            executed = true;

            try {
               Object obj = box.getData(assembly);

               if(obj != null) {
                  return obj;
               }

               box.executeView(assembly, false);
            }
            catch(ColumnNotFoundException | ScriptException messageException) {
               LOG.warn("Failed to get output property '{}' due to prior exception", name);
               return null;
            }
            catch(Exception ex) {
               LOG.error("Failed to get output property '{}', could not get data", name, ex);
               return null;
            }
            finally {
               executed = false;
            }
         }

         return ((OutputVSAssembly) vassembly).getValue();
      }
      else if("dataConditions".equals(name)) {
         return getTipConditions();
      }

      return super.getMember(name);
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      addOutputProperties();

      if(getVSAssemblyInfo() != null) {
         setElement(getVSAssemblyInfo());
      }
   }

   @Override
   protected boolean hasActions() {
      return false;
   }

   /**
    * Add output properties.
    */
   protected void addOutputProperties() {
      OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) getVSAssemblyInfo();

      addProperty("hyperlink", "getHyperlink", "setHyperlink",
                  Hyperlink.class, info.getClass(), info);
      addProperty("value", "getValue", "setValue",
                  Object.class, info.getClass(), info);
      addProperty("shadow", "isShadow", "setShadow",
                  boolean.class, info.getClass(), info);
      addProperty("dataConditions", null);
      addProperty("toolTip", "getCustomTooltipString", "setCustomTooltipString",
         String.class, info.getClass(), info);
      addProperty("tooltipVisible", "isTooltipVisible", "setTooltipVisible",
         boolean.class, info.getClass(), info);

      if(!(getInfo() instanceof ImageVSAssemblyInfo)) {
         addProperty("query", "getQuery", "setQuery", String.class,
            getClass(), this);
         addProperty("fields", "getFields", "setFields", Object[].class,
            getClass(), this);
         addProperty("formula", "getFormula", "setFormula", String.class,
            getClass(), this);
      }
   }

   /**
    * Get Query.
    */
   public String getQuery() {
      OutputVSAssemblyInfo info = getInfo();

      if(info != null) {
         ScalarBindingInfo scalarBinding = info.getScalarBindingInfo();
         return scalarBinding == null ? null : scalarBinding.getTableName();
      }

      return null;
   }

   /**
    * Set Query.
    */
   public void setQuery(String table) {
      OutputVSAssemblyInfo info = getInfo();

      if(info != null) {
         ScalarBindingInfo binding = info.getScalarBindingInfo();
         binding = binding != null ? binding : new ScalarBindingInfo();

         binding.setTableName(table);
         info.setScalarBindingInfo(binding);
      }
   }

   /**
    * Get Fields.
    */
   public String getFields() {
      OutputVSAssemblyInfo info = getInfo();

      if(info != null) {
         ScalarBindingInfo scalarBinding = info.getScalarBindingInfo();
         return scalarBinding == null ? null : scalarBinding.getColumnValue();
      }

      return null;
   }

   /**
    * Set Fields.
    * fields[0] the table column
    * fields[1] the column which aggregating with fields[0], optional
    */
   public void setFields(Object[] fields) {
      OutputVSAssemblyInfo info = getInfo();

      if(fields.length > 0 && info != null) {
         ScalarBindingInfo binding = info.getScalarBindingInfo();
         binding = binding != null ? binding : new ScalarBindingInfo();

         binding.setColumnValue((String) fields[0]);
         binding.setColumn2Value(fields.length == 1 ? null : (String) fields[1]);
         info.setScalarBindingInfo(binding);
      }
   }

   /**
    * Set the formula for aggregating
    */
   public void setFormula(String formula) {
      OutputVSAssemblyInfo info = getInfo();

      if(info != null) {
         ScalarBindingInfo binding = info.getScalarBindingInfo();
         binding = binding != null ? binding : new ScalarBindingInfo();
         binding.setAggregateValue(formula);
         info.setScalarBindingInfo(binding);
      }
   }

   /**
    * Get the formula for aggregating
    */
   public String getFormula() {
      OutputVSAssemblyInfo info = getInfo();

      if(info != null) {
         ScalarBindingInfo binding = info.getScalarBindingInfo();
         return binding == null ? null : binding.getAggregateValue();
      }

      return null;
   }

   // NOTE (Feature #75423): Rhino getDefaultValue(Class) — which returned the
   // output's data value when the scriptable was used as a scalar (e.g.
   // name + 2) — has no direct equivalent in the GraalJS ProxyObject model and
   // is removed per the migration recipe. The value is still available via the
   // explicit accessors. Revisit if scalar coercion is required post-cutover.

   /**
    * Get the suffix of a property.
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("highlighted".equals(prop)) {
         return "";
      }

      return super.getSuffix(prop);
   }

   /**
    * Get the assembly info of current output.
    */
   private OutputVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof OutputVSAssemblyInfo) {
         return (OutputVSAssemblyInfo) getVSAssemblyInfo();
      }

      return null;
   }

   private boolean executed = false;
   private static final Logger LOG =
      LoggerFactory.getLogger(OutputVSAScriptable.class);
}
