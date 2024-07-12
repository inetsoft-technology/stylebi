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
package inetsoft.graph.mxgraph.util;

import java.util.Hashtable;
import java.util.Map;

/**
 * Base class for objects that dispatch named events.
 */
public class mxEventObject {

   /**
    * Holds the name of the event.
    */
   protected String name;

   /**
    * Holds the properties of the event.
    */
   protected Map<String, Object> properties;

   /**
    * Holds the consumed state of the event. Default is false.
    */
   protected boolean consumed = false;

   /**
    * Constructs a new event for the given name.
    */
   public mxEventObject(String name)
   {
      this(name, (Object[]) null);
   }

   /**
    * Constructs a new event for the given name and properties. The optional
    * properties are specified using a sequence of keys and values, eg.
    * <code>new mxEventObject("eventName", key1, val1, .., keyN, valN))</code>
    */
   public mxEventObject(String name, Object... args)
   {
      this.name = name;
      properties = new Hashtable<String, Object>();

      if(args != null) {
         for(int i = 0; i < args.length; i += 2) {
            if(args[i + 1] != null) {
               properties.put(String.valueOf(args[i]), args[i + 1]);
            }
         }
      }
   }

   /**
    * Returns the name of the event.
    */
   public String getName()
   {
      return name;
   }

   /**
    *
    */
   public Map<String, Object> getProperties()
   {
      return properties;
   }

   /**
    *
    */
   public Object getProperty(String key)
   {
      return properties.get(key);
   }

   /**
    * Returns true if the event has been consumed.
    */
   public boolean isConsumed()
   {
      return consumed;
   }

   /**
    * Consumes the event.
    */
   public void consume()
   {
      consumed = true;
   }

}
