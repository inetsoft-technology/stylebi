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
package inetsoft.report.script;

import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * This class provides a proxy to allow a javascript object to have properties
 * that are tied to Java object fields.
 */
public class ProxyScriptable extends ScriptableObject {
   /**
    * Create a proxy to a java object.
    * @param target target Java object.
    * @param props property names in the Java object. They must be public
    * variable names.
    */
   public ProxyScriptable(Object target, String[] props) {
      this.target = target;
      this.props = props;
   }

   /**
    * Initialize the object.
    */
   private void init() {
      if(fields == null) {
         fields = new Field[props.length];

         for(int i = 0; i < fields.length; i++) {
            try {
               fields[i] = target.getClass().getField(props[i]);
            }
            catch(Exception ex) {
               LOG.error("Failed to register proxy properties", ex);
            }
         }
      }
   }

   @Override
   public String getClassName() {
      return "ProxyObject";
   }

   /**
    * This method is called after the value of a field is changed.
    */
   protected void update() {
   }

   @Override
   public boolean has(String id, Scriptable start) {
      for(int i = 0; i < props.length; i++) {
         if(id.equals(props[i])) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   @Override
   public Object get(String id, Scriptable start) {
      init();

      try {
         for(int i = 0; i < props.length; i++) {
            if(props[i].equals(id)) {
               return fields[i].get(target);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get proxy property: " + id, ex);
      }

      return Undefined.instance;
   }

   @Override
   public Object get(int index, Scriptable start) {
      return Undefined.instance;
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      init();

      try {
         for(int i = 0; i < props.length; i++) {
            if(props[i].equals(id)) {
               fields[i].set(target,
                  PropertyDescriptor.convert(value, fields[i].getType()));
               update();
               return;
            }
         }

         LOG.error(
            "Proxy property does not exist, could not be set: " + id +
            "=" + value);
      }
      catch(Exception ex) {
         LOG.error("Failed to set proxy property: " + id + "=" + value, ex);
      }
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
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
      return props;
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   Object target;
   String[] props;
   Field[] fields;

   private static final Logger LOG =
      LoggerFactory.getLogger(ProxyScriptable.class);
}

