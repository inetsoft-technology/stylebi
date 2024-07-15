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
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.awt.*;
import java.util.Arrays;

/**
 * The tab assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class TabVSAScriptable extends VSAScriptable {
   /**
    * Create an input viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public TabVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TabVSA";
   }

   @Override
   protected boolean hasActions() {
      return false;
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof TabVSAssembly)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      TabVSAssemblyInfo info = getInfo();

      addProperty("labels", "getLabels", "setLabels", String[].class,
                  info.getClass(), info);

      addProperty("selectedObject", "getSelected", "setSelected", String.class,
         TabVSAScriptable.class, this);
      addProperty("selectedIndex", "getSelectedIndex", "setSelectedIndex", int.class,
         TabVSAScriptable.class, this);
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("labels".equals(prop))
      {
         return "[]";
      }

      return super.getSuffix(prop);
   }

   public String getSelected() {
      return getInfo().getSelectedValue();
   }

   public void setSelected(String selected) {
      int index = selected == null ? -1 : selected.lastIndexOf(".");
      selected = index == -1 ? selected : selected.substring(index + 1);
      getInfo().setSelected(selected);
   }

   public void setSelectedValue(String selected) {
      setSelected(selected);
      getInfo().setSelectedValue(getInfo().getSelected());
   }

   public int getSelectedIndex() {
      String label = getInfo().getSelected();
      String[] labels = getInfo().getAssemblies();
      int idx = -1;

      for(int i = 0; label != null && labels != null && i < labels.length; i++) {
         if(label.equals(labels[i])) {
            idx = i;
            break;
         }
      }

      return idx;
   }

   public void setSelectedIndex(int idx) {
      idx = Math.max(0, idx);
      String[] labels = getInfo().getAssemblies();
      setSelected(labels[idx]);
   }

   public void setSelectedIndexValue(int idx) {
      idx = Math.max(0, idx);
      String[] labels = getInfo().getAssemblies();
      setSelectedValue(labels[idx]);
   }

   /**
    * Get the assembly info of current tab.
    */
   private TabVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof TabVSAssemblyInfo) {
         return (TabVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new TabVSAssemblyInfo();
   }

   @Override
   public void setSize(Dimension size) {
      Dimension originalSize = getInfo().getPixelSize();
      super.setSize(size);

      if(!box.isRuntime()) {
         return;
      }

      int ychange = size.height - originalSize.height;
      String[] children = getInfo().getAssemblies();

      Arrays.stream(children)
         .map(c -> getVSAssembly(c))
         .filter(c -> c != null)
         .map(c -> c.getVSAssemblyInfo())
         .forEach(c -> {
            Point pos = c.getPixelOffset();

            if(pos != null) {
               setPosition(c, new Point(pos.x, pos.y + ychange));
            }
         });
   }
}
