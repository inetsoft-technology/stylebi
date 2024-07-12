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
package inetsoft.report.script.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
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
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null : vs.getAssembly(assembly);

      if(!(vassembly instanceof OutputVSAssembly)) {
         return Undefined.instance;
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
               return Undefined.instance;
            }
            catch(Exception ex) {
               LOG.error("Failed to get output property '{}', could not get data", name, ex);
               return Undefined.instance;
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

      return super.get(name, start);
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

   /**
    * This function is called if the referenced is used without any indexing,
    * e.g. name + 2
    */
   @Override
   public Object getDefaultValue(Class hint) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof OutputVSAssembly)) {
         return Undefined.instance;
      }

      Object data = null;

      // by calling getData, we have chance to update the output assembly,
      // including script execution
      try {
         data = box.getData(assembly);
      }
      catch(Exception ex) {
         LOG.error("Failed to get output data", ex);
      }

      // @by larryl, if the value of a output is set through script, calling
      // getData() may not get it since the value is cached in box. At this
      // time the query should already be executed so if the value is from
      // a query, getValue() should return the correct value.
      if(data == null) {
         data = ((OutputVSAssembly) vassembly).getValue();
      }

      return data;
   }

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
