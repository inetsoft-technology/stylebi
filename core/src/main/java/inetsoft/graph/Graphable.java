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
package inetsoft.graph;

import com.inetsoft.build.tern.TernMethod;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

/**
 * This is the common base class for items that can be added to EGraph
 * for graphing (e.g. GraphElement, GraphForm).
 *
 * @version 11.3
 * @author InetSoft Technology Corp
 */
public abstract class Graphable implements Cloneable, Serializable {
   /**
    * Set whether this element should be kept inside the plot area. If set to
    * true and the element extends outside of the plot, the plot area is scaled
    * to push the object inside. The default is true.
    */
   public abstract void setInPlot(boolean inside);

   /**
    * Check if the element should be kept inside the plot area.
    */
   public abstract boolean isInPlot();

   /**
    * Set the hint for how the element should be rendered. Hint may not apply
    * to all element types.
    */
   @TernMethod
   public void setHint(String hint, Object val) {
      if(val == null) {
         hintmap.remove(hint);
      }
      else {
         hintmap.put(hint, val);
      }
   }

   /**
    * Get the rendering hint.
    */
   @TernMethod
   public Object getHint(String hint) {
      return hintmap.get(hint);
   }

   /**
    * Get the rendering hints.
    */
   @TernMethod
   public Map<String, Object> getHints() {
      return hintmap;
   }

   /**
    * Copy the hints.
    */
   @TernMethod
   public void setHints(Map<String, Object> hints) {
      this.hintmap.putAll(hints);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      String cls = getClass().getSimpleName() + '@' +
         Integer.toHexString(this.hashCode());
      Iterator<Map.Entry> it = hintmap.entrySet().iterator();
      String hintList = "";

      // @by davidd 2009-01-23 the "overlay" and "overlaid" hints create
      // a loop when printing the hintmap.
      while (it.hasNext()) {
         Map.Entry<String,Object> pairs = it.next();
         String key = pairs.getKey();
         Object val = pairs.getValue();

         if(val instanceof Graphable) {
            hintList += key + "=" + val.getClass().getSimpleName() +
               '@' + Integer.toHexString(val.hashCode());
         }
         else {
            hintList += key + "=" + val.toString();
         }

         if(it.hasNext()) {
            hintList += ",";
         }
      }

      return cls + "[" + hintList + "]";
   }

   @Override
   public Graphable clone() {
      try {
         Graphable obj = (Graphable) super.clone();
         obj.hintmap = hintmap.clone();
         return obj;
      }
      catch(CloneNotSupportedException e) {
         // ignore, shouldn't happen
         return this;
      }
   }

   /**
    * Check if equals another objects in structure.
    */
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof Graphable)) {
         return false;
      }

      Graphable gobj = (Graphable) obj;
      return hintmap.equals(gobj.hintmap);
   }

   private Object2ObjectOpenHashMap hintmap = new Object2ObjectOpenHashMap<>();
}
