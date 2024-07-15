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

import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;

/**
 * Mark a class as an array. Used in autocompletion only.
 */
public class Calc extends ScriptableObject {
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

               FunctionObject func = new FunctionObject(methods[j].getName(), methods[j], this);
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

   @Override
   public String getClassName() {
      return "Calc";
   }

   @Override
   public boolean has(String id, Scriptable start) {
      return get(id, start) != null;
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return super.has(index, start);
   }

   @Override
   public Object get(String id, Scriptable start) {
      // get is called before the constructor is completed
      if(funcmap != null) {
         Object val = funcmap.get(id.toLowerCase());

         if(val != null) {
            return val;
         }
      }

      return super.get(id, start);
   }

   @Override
   public Object get(int index, Scriptable start) {
      return super.get(index, start);
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      super.put(id, start, value);
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
      super.put(index, start, value);
   }

   @Override
   public Object getDefaultValue(Class hint) {
      if(hint == ScriptRuntime.BooleanClass) {
         return Boolean.TRUE;
      }
      else if(hint == ScriptRuntime.NumberClass) {
         return ScriptRuntime.NaNobj;
      }

      return this;
   }

   @Override
   public Object[] getIds() {
      return funcmap.keySet().toArray();
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   private Map<String, FunctionObject> funcmap = new Hashtable<>(); // name -> FunctionObject

   private static final Logger LOG = LoggerFactory.getLogger(Calc.class);
}
