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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CurrentSelectionVSAssemblyInfo;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.awt.*;

/**
 * The selection container viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SelectionContainerVSAScriptable extends VSAScriptable {
   /**
    * Create a selection tree viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SelectionContainerVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "SelectionContainerVSA";
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      CurrentSelectionVSAssemblyInfo info = getInfo();

      addProperty("title", "getTitle", "setTitle",
                  String.class, info.getClass(), info);
      addProperty("titleVisible", "isTitleVisible", "setTitleVisible",
                  boolean.class, info.getClass(), info);
      addProperty("showCurrentSelection", "isShowCurrentSelection",
                  "setShowCurrentSelection", boolean.class,
                  info.getClass(), info);
      addProperty("adhocEnabled", "isAdhocEnabled", "setAdhocEnabled",
                  boolean.class, info.getClass(), info);
      addProperty("empty", "isEmpty", null, boolean.class, info.getClass(), info);
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof CurrentSelectionVSAssemblyInfo)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }
   /**
    * Get the assembly info of CurrentSelection.
    */
   private CurrentSelectionVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof CurrentSelectionVSAssemblyInfo) {
         return (CurrentSelectionVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new CurrentSelectionVSAssemblyInfo();
   }

   /**
    * Set the Size.
    * @param dim the dimension of size.
    */
   @Override
   public void setSize(Dimension dim) {
      CurrentSelectionVSAssemblyInfo info = getInfo();
      super.setSize(dim);
      updateChildSize(dim);
   }

   /**
    *  Update the children Size.
    */
   protected void updateChildSize(Dimension dim) {
      if(!box.isRuntime()) {
         return;
   	}

      CurrentSelectionVSAssemblyInfo info = getInfo();
      String[] children = info.getAssemblies();
      Viewsheet vs = box.getViewsheet();

      for(int i = 0; i < children.length; i++) {
         Assembly child = vs.getAssembly(children[i]);

         if(child == null) {
            continue;
         }

         AssemblyInfo childinfo = child.getInfo();
         dim.height = childinfo.getPixelSize().height;
         childinfo.setPixelSize(dim);
      }
   }
}
