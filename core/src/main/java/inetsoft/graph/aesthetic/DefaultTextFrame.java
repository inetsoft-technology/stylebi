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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.internal.GTool;
import inetsoft.util.Tool;

import java.util.*;

/**
 * This class extracts value from a dataset to be used as text labels of
 * visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=DefaultTextFrame")
public class DefaultTextFrame extends TextFrame implements CategoricalFrame, Cloneable {
   /**
    * Create a text frame. The field needs to be set by calling setField.
    */
   public DefaultTextFrame() {
   }

   /**
    * Create a text frame.
    * @param field field to get value to map to text labels.
    */
   @TernConstructor
   public DefaultTextFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the text for the specified value.
    */
   @Override
   @TernMethod
   public Object getText(Object val) {
      if(textmap.isEmpty()) {
         return val;
      }

      if(textmap.keySet().contains(val)) {
         return textmap.get(val);
      }

      // when set alias, we use string, so here try string directly
      // fix bug1342433024382
      if(val instanceof Number) {
         String str = val.toString();

         if(textmap.containsKey(str)) {
            return textmap.get(str);
         }
      }

      if(!(val instanceof String)) {
         String str = GTool.toString(val);

         if(textmap.keySet().contains(str)) {
            return textmap.get(str);
         }
      }

      return val;
   }

   /**
    * Set the text for the specified value.
    */
   @TernMethod
   public void setText(Object val, Object text) {
      if(text != null) {
         textmap.put(val, text);
         textmap.put(GTool.toString(val), text);
      }
      else {
         textmap.remove(val);
         textmap.remove(GTool.toString(val));
      }
   }

   /**
    * Get the aliased values.
    */
   @Override
   @TernMethod
   public Collection getKeys() {
      return textmap.keySet();
   }

   /**
    * Check if this frame has been initialized and is ready to be used.
    */
   @Override
   @TernMethod
   public boolean isValid() {
      return true;
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   @TernMethod
   public boolean isStatic(Object val) {
      return textmap.containsKey(val);
   }

   @Override
   @TernMethod
   public Set<Object> getStaticValues() {
      return textmap.keySet();
   }

   @Override
   @TernMethod
   public void clearStatic() {
      textmap.clear();
   }

   @Override
   public Object clone() {
      DefaultTextFrame frame = (DefaultTextFrame) super.clone();
      frame.textmap = Tool.deepCloneMap(textmap);
      return frame;
   }

   private Map textmap = new HashMap();
   private static final long serialVersionUID = 1L;
}
