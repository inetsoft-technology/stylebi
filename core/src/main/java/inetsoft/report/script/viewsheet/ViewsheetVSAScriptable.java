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

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.Scriptable;

import java.util.*;

/**
 * The viewsheet scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ViewsheetVSAScriptable extends VSAScriptable {
   /**
    * Create a viewsheet assembly scriptable.
    *
    * @param box the specified viewsheet sandbox.
    */
   public ViewsheetVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ViewsheetVSA";
   }

   /**
    * Initialize the assembly properties.
    */
   @Override
   protected void addProperties() {
      // add to ids
      addProperty("updateTime", null);
      addProperty("viewsheetName", null);
      addProperty("viewsheetPath", null);
      addProperty("viewsheetAlias", null);

      if(!ViewsheetScope.VIEWSHEET_SCRIPTABLE.equals(assembly)) {
         String vsName =
            getVSAssembly().getViewsheet() == null ? assembly : getVSAssembly().getAbsoluteName();
         ViewsheetSandbox myBox = box.getSandbox(vsName);
         // thisParameter points to parameters in the embedded vs instead of the containing vs
         addProperty("thisParameter", new VariableScriptable(myBox.getVariableTable()));
      }

      if(getInfo() != null && getInfo().isEmbedded()) {
         addProperty("visible", "isVisible", "setVisible", String.class, getClass(), this);
      }

      addFunctionProperty(getClass(), "refresh");
      addFunctionProperty(getClass(), "setInputSelectedObject", String.class, Object.class);
   }

   /**
    * Get the assembly info of current element.
    */
   private ViewsheetVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof ViewsheetVSAssemblyInfo) {
         return (ViewsheetVSAssemblyInfo) getVSAssemblyInfo();
      }

      return null;
   }

   @Override
   protected VSAssembly getVSAssembly() {
      Viewsheet vs = box.getViewsheet();

      // For top-level viewsheet, assembly is "thisViewsheet"
      // For embedded viewsheets, assembly is the viewsheet's name (e.g., "Viewsheet2")
      // In both cases, return the viewsheet from the box
      if(ViewsheetScope.VIEWSHEET_SCRIPTABLE.equals(assembly) ||
         (vs != null && assembly != null && assembly.equals(vs.getName())))
      {
         return vs;
      }

      return super.getVSAssembly();
   }

   /**
    * Get a property value.
    */
   @Override
   public Object get(String id, Scriptable start) {
      if("updateTime".equals(id)) {
         return getUpdateTime();
      }
      else if("viewsheetName".equals(id)) {
         return getName();
      }
      else if("viewsheetPath".equals(id)) {
         return getPath();
      }
      else if("viewsheetAlias".equals(id)) {
         return getAlias();
      }
      else if("taskName".equals(id)) {
         String taskName = getTaskName();

         return StringUtils.isEmpty(taskName) ? super.get(id, start) : taskName;
      }
      else if("currentBookmark".equals(id)) {
         return getCurrentBookmark();
      }

      return super.get(id, start);
   }

   /**
    * Get the query execution or MV creation time.
    */
   private Date getUpdateTime() {
      ViewsheetSandbox box = this.box;
      Viewsheet vs = box.getViewsheet();

      // if called from a parent on an embedded viewsheet
      if(!Tool.equals(vs.getAbsoluteName(), assembly)) {
         VSAssembly vsAssembly = vs.getAssembly(assembly);

         if(vsAssembly instanceof Viewsheet) {
            vs = (Viewsheet) vsAssembly;
            box = box.getSandbox(vs.getAbsoluteName());
         }
      }

      if(box.isMVEnabled()) {
         AssetEntry ventry = box.getAssetEntry();
         final String vsId = ventry.toIdentifier();
         MVManager manager = MVManager.getManager();
         List<String> parentVsIds = box.getParentVsIds();

         MVDef[] list = manager.list(false,
                                     def -> !def.isWSMV() && def.getMetaData().isRegistered(vsId) &&
                                        (def.getParentVsIds() == null || def.getParentVsIds().equals(parentVsIds)));

         if(list.length > 0) {
            long last = 0;
            List<MVDef> withParent = new ArrayList<>();
            List<MVDef> withoutParent = new ArrayList<>();

            for(MVDef def : list) {
               if(def.getParentVsIds() != null) {
                  withParent.add(def);
               }
               else {
                  withoutParent.add(def);
               }
            }

            List<MVDef> selectedList = withParent.isEmpty() ? withoutParent : withParent;

            for(MVDef def : selectedList) {
               last = Math.max(last, def.lastModified());
            }

            return new Date(last);
         }
      }

      return new Date(box.getLastExecutionTime());
   }

   /**
    * Get the Viewsheet name.
    */
   private String getName() {
      return getEntry().getName();
   }

   /**
    * Get the Viewsheet path.
    */
   private String getPath() {
      return getEntry().getUser() != null ?
         Tool.MY_DASHBOARD + "/" + getEntry().getPath() : getEntry().getPath();
   }

   /**
    * Get the Viewsheet alias.
    */
   private String getAlias() {
      return getEntry().getAlias();
   }

   /**
    * get the viewsheet task name
    */
   private String getTaskName() {
      return getEntry().getProperty("taskName");
   }

   private String getCurrentBookmark() {
      VSBookmarkInfo bookmark = box.getOpenedBookmark();
      String currentBookmark = bookmark == null ? null : bookmark.getName();
      return currentBookmark == null ? VSBookmark.HOME_BOOKMARK : currentBookmark;
   }

   private AssetEntry getEntry() {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null : vs.getAssembly(assembly);
      return vassembly instanceof Viewsheet ? ((Viewsheet) vassembly).getEntry()
         : box.getAssetEntry();
   }

   public void setInputSelectedObject(String assemblyName, Object value) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vsAssembly = assembly == null ? null : vs.getAssembly(assembly);
      VSAssembly assembly;

      if(vsAssembly instanceof Viewsheet) {
         assembly = ((Viewsheet) vsAssembly).getAssembly(assemblyName);
      }
      else {
         assembly = vs.getAssembly(assemblyName);
      }

      if(assembly instanceof InputVSAssembly) {
         InputVSAssemblyInfo info = (InputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         Object typedValue = Tool.getData(info.getDataType(), value);
         info.setSelectedObject(typedValue);

         // Bug #72320: also update the embedded viewsheet sandbox's variable table so that
         // script references to parameter.<assemblyName> reflect the new value immediately,
         // without requiring the embedded radio button's submit event to fire.
         if(vsAssembly instanceof Viewsheet && this.assembly != null) {
            ViewsheetSandbox embeddedBox = box.getSandbox(this.assembly);

            if(embeddedBox != null) {
               embeddedBox.getVariableTable().put(assemblyName, typedValue);
            }
         }
      }
   }

   /**
    * Refresh this viewsheet and all the viewsheets under this one.
    * For example, if A, B, and C are all viewsheets, where A embeds B, B embeds C,
    * and this function is invoked on B, then B and C will be refreshed.
    * <p>
    * This method can only be used inside an onClick script.
    */
   public void refresh() {
      // if not executing an onClick script then do nothing
      if(!JavaScriptEngine.isOnClickScript()) {
         return;
      }

      // box.reset() will execute assembly scripts so make sure this method won't be called again
      JavaScriptEngine.setOnClickScript(false);

      try {
         ViewsheetSandbox box = this.box;
         Viewsheet vs = box.getViewsheet();

         // if called from a parent on an embedded viewsheet
         if(!Tool.equals(vs.getAbsoluteName(), assembly)) {
            VSAssembly vsAssembly = vs.getAssembly(assembly);

            if(vsAssembly instanceof Viewsheet) {
               vs = (Viewsheet) vsAssembly;
               box = box.getSandbox(vs.getAbsoluteName());
            }
         }

         long timestamp = System.currentTimeMillis();

         box.setTouchTimestamp(timestamp);
         box.resetRuntime();
         box.reset(new ChangedAssemblyList());

         // update the touch timestamp of all the nested viewsheets under this one
         List<Viewsheet> nestedViewsheets = getNestedViewsheets(vs);

         for(Viewsheet nestedVS : nestedViewsheets) {
            ViewsheetSandbox nestedBox = box.getSandbox(nestedVS.getAbsoluteName());
            nestedBox.setTouchTimestamp(timestamp);
            nestedBox.resetRuntime();
            nestedBox.reset(new ChangedAssemblyList());
         }
      }
      finally {
         JavaScriptEngine.setOnClickScript(true);
      }
   }

   private static List<Viewsheet> getNestedViewsheets(Viewsheet vs) {
      List<Viewsheet> result = new ArrayList<>();
      collectNestedViewsheets(vs, result);
      return result;
   }

   private static void collectNestedViewsheets(Viewsheet vs, List<Viewsheet> result) {
      for(Assembly assembly : vs.getAssemblies()) {
         if(!(assembly instanceof Viewsheet nestedVS)) {
            continue;
         }

         result.add(nestedVS);
         collectNestedViewsheets(nestedVS, result);
      }
   }
}
