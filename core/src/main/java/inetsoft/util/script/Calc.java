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
package inetsoft.util.script;

import inetsoft.util.script.graal.ScriptScope;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;

/**
 * Mark a class as an array. Used in autocompletion only.
 */
public class Calc implements ScriptScope {
   public Calc() {
      Class[] calcs = new Class[]{
         CalcDateTime.class,
         CalcFinancial.class,
         CalcLogic.class,
         CalcMath.class,
         CalcStat.class,
         CalcTextData.class,
      };

      try {
         for(int i = 0; i < calcs.length; i++) {
            Method[] methods = calcs[i].getMethods();

            for(int j = 0; j < methods.length; j++) {
               if(methods[j].getDeclaringClass() != calcs[i]) {
                  continue;
               }

               // NOTE (Feature #75423): FunctionObject is Rhino substrate,
               // replaced by the native-binding mechanism at the Milestone 4
               // cutover. Calc no longer extends Rhino's ScriptableObject, so
               // 'this' can no longer be passed as the Rhino scope; pass null
               // until the M4 cutover rewires it.
               FunctionObject func = new FunctionObject(methods[j].getName(), methods[j], (Scriptable) null);
               String name = methods[j].getName().toLowerCase();
               funcmap.put(name, func);

               // add int() to match excel
               if(name.equals("integer")) {
                  funcmap.put("int", func);
               }
               // add char() to match excel
               else if(name.equals("character")) {
                  funcmap.put("char", func);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to init functions", ex);
      }
   }

   public String getClassName() {
      return "Calc";
   }

   @Override
   public boolean hasMember(String id) {
      return getMember(id) != null;
   }

   @Override
   public Object getMember(String id) {
      // getMember may be called before the constructor is completed
      if(funcmap != null) {
         return funcmap.get(id.toLowerCase());
      }

      return null;
   }

   @Override
   public void putMember(String id, Object value) {
      // not supported
   }

   @Override
   public Object[] getMemberKeys() {
      return funcmap.keySet().toArray();
   }

   /**
    * Get the parent scope of the object.
    */
   @Override
   public ScriptScope getParentScope() {
      return parent;
   }

   /**
    * Set the parent scope of the object.
    */
   public void setParentScope(ScriptScope parent) {
      this.parent = parent;
   }

   private ScriptScope parent;
   private Map<String, FunctionObject> funcmap = new Hashtable<>(); // name -> FunctionObject

   private static final Logger LOG = LoggerFactory.getLogger(Calc.class);
}
