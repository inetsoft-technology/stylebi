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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.mozilla.javascript.FunctionObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The table assembly scriptable in viewsheet scope.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class TableVSAScriptable extends TableDataVSAScriptable {
   /**
    * Create table assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public TableVSAScriptable(ViewsheetSandbox box) {
      super(box);
      addFunctions();
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TableVSA";
   }

   /**
    * Add crosstab properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();
      addProperty("form", "isForm", "setForm", boolean.class, getClass(), this);
      addProperty("insert", "isInsert", "setInsert",
                  boolean.class, TableVSAssemblyInfo.class, tinfo);
      addProperty("del", "isDel", "setDel",
                  boolean.class, TableVSAssemblyInfo.class, tinfo);
      addProperty("edit", "isEdit", "setEdit",
                  boolean.class, TableVSAssemblyInfo.class, tinfo);
      addProperty("query", "getQuery", "setQuery",
                  String.class, getClass(), this);
      addProperty("fields", "getFields", "setFields",
                  Object[].class, getClass(), this);
   }

   /**
    * Add user defined functions to the scope.
    */
   protected void addFunctions() {
      try {
         FunctionObject func = new FunctionObject("applyChanges",
            getClass().getMethod("applyChanges", new Class[] {}), this);
         addProperty("applyChanges", func);

         super.addFunctions();

         if(LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
            addFunctionProperty(getClass(), "setObject", int.class, int.class, Object.class);
            addFunctionProperty(getClass(), "getFormRow", int.class);
            addFunctionProperty(getClass(), "getFormRows", Object.class);
            addFunctionProperty(getClass(), "getHiddenColumnValue", String.class, int.class);
            addFunctionProperty(getClass(), "commit", Object.class);

            // only for internal testing
            addFunctionProperty(getClass(), "appendRow", int.class);
            addFunctionProperty(getClass(), "insertRow", int.class);
            addFunctionProperty(getClass(), "deleteRow", int.class);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to register the table data properties and functions", ex);
      }
   }

   public boolean isForm() {
      return ((TableVSAssemblyInfo) getVSAssemblyInfo()).isForm();
   }

   public void setForm(boolean form) {
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();
      tinfo.setForm(form);

      if(!form) {
         tinfo.setInsert(false);
         tinfo.setDel(false);
         tinfo.setEdit(false);
      }
   }

   public void setFormValue(boolean form) {
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();
      tinfo.setFormValue(form);

      if(!form) {
         tinfo.setInsertValue(false);
         tinfo.setDelValue(false);
         tinfo.setEditValue(false);
      }
   }

   /**
    * Get Query.
    */
   public String getQuery() {
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();
      if(tinfo instanceof TableVSAssemblyInfo){
         return tinfo.getSourceInfo() == null ?
            null : tinfo.getSourceInfo().getSource();
      }

      return null;
   }

   /**
    * Set Query.
    */
   public void setQuery(String source) {
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();
      SourceInfo sinfo = new SourceInfo();
      tinfo.setColumnSelection(null);

      sinfo.setType(SourceInfo.ASSET);
      // use cube:: to separate the viewsheet source and cube source
      sinfo.setSource(source.startsWith("cube::") ?
         Assembly.CUBE_VS + source.substring(6) : source);
      sinfo.setProperty("wsCube","false");
      tinfo.setSourceInfo(sinfo);
   }

   /**
    * Get Fields.
    */
   public List<String> getFields() {
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();

      if(tinfo instanceof TableVSAssemblyInfo) {
         ColumnSelection selection = tinfo.getColumnSelection();

         if(selection != null) {
            Enumeration e = selection.getAttributes();
            return e == null ? null : Collections.list(e);
         }

         return null;
      }

      return null;
   }

   /**
    * Set Fields.
    */
   public void setFields(Object[] fields) {
      if(fields.length > 0) {
         TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) getVSAssemblyInfo();
         ColumnSelection selection = new ColumnSelection();

         for(Object field:fields) {
            ColumnRef colref = new ColumnRef();
            colref.setDataRef(new AttributeRef((String) field));
            selection.addAttribute(colref);
         }

         tinfo.setColumnSelection(selection);
      }
   }

   public void applyChanges() {
      TableVSAssembly assembly = (TableVSAssembly) getVSAssembly();
      box.writeBackFormData(assembly);
   }

   private static final Logger LOG = LoggerFactory.getLogger(TableVSAScriptable.class);
}
