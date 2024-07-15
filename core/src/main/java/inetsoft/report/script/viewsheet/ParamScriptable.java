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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The param scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ParamScriptable extends ScriptableObject {
   /**
    * Create a param scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public ParamScriptable(ViewsheetSandbox box) {
      super();

      this.box = box;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "Param";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

      if(!(assembly instanceof InputVSAssembly)) {
         return Undefined.instance;
      }

      try {
         return box.getData(name);
      }
      catch(Exception ex) {
         LOG.error("Failed to get parameter data", ex);
      }

      return Undefined.instance;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly assembly = (VSAssembly) vs.getAssembly(name);
      return assembly instanceof InputVSAssembly;
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      List list = new ArrayList();
      Viewsheet vs = box.getViewsheet();
      Assembly[] assemblies = vs.getAssemblies();

      for(int i = 0; i < assemblies.length; i++) {
         if(assemblies[i] instanceof InputVSAssembly) {
            list.add(assemblies[i].getName());
         }
      }

      String[] names = new String[list.size()];
      list.toArray(names);

      return names;
   }

   private ViewsheetSandbox box;

   private static final Logger LOG =
      LoggerFactory.getLogger(ParamScriptable.class);
}
