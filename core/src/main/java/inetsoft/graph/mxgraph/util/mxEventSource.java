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
package inetsoft.graph.mxgraph.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for objects that dispatch named events.
 */
public class mxEventSource {

   /**
    * Holds the event names and associated listeners in an array. The array
    * contains the event name followed by the respective listener for each
    * registered listener.
    */
   protected transient List<Object> eventListeners = null;
   /**
    * Holds the source object for this event source.
    */
   protected Object eventSource;
   /**
    * Specifies if events can be fired. Default is true.
    */
   protected boolean eventsEnabled = true;

   /**
    * Constructs a new event source using this as the source object.
    */
   public mxEventSource()
   {
      this(null);
   }

   /**
    * Constructs a new event source for the given source object.
    */
   public mxEventSource(Object source)
   {
      setEventSource(source);
   }

   /**
    *
    */
   public Object getEventSource()
   {
      return eventSource;
   }

   /**
    *
    */
   public void setEventSource(Object value)
   {
      this.eventSource = value;
   }

   /**
    *
    */
   public boolean isEventsEnabled()
   {
      return eventsEnabled;
   }

   /**
    *
    */
   public void setEventsEnabled(boolean eventsEnabled)
   {
      this.eventsEnabled = eventsEnabled;
   }

   /**
    * Binds the specified function to the given event name. If no event name
    * is given, then the listener is registered for all events.
    */
   public void addListener(String eventName, mxIEventListener listener)
   {
      if(eventListeners == null) {
         eventListeners = new ArrayList<Object>();
      }

      eventListeners.add(eventName);
      eventListeners.add(listener);
   }

   /**
    * Function: removeListener
    * <p>
    * Removes all occurances of the given listener from the list of listeners.
    */
   public void removeListener(mxIEventListener listener)
   {
      removeListener(listener, null);
   }

   /**
    * Function: removeListener
    * <p>
    * Removes all occurances of the given listener from the list of listeners.
    */
   public void removeListener(mxIEventListener listener, String eventName)
   {
      if(eventListeners != null) {
         for(int i = eventListeners.size() - 2; i > -1; i -= 2) {
            if(eventListeners.get(i + 1) == listener
               && (eventName == null || String.valueOf(
               eventListeners.get(i)).equals(eventName)))
            {
               eventListeners.remove(i + 1);
               eventListeners.remove(i);
            }
         }
      }
   }

   /**
    * Dispatches the given event name with this object as the event source.
    * <code>fireEvent(new mxEventObject("eventName", key1, val1, .., keyN, valN))</code>
    */
   public void fireEvent(mxEventObject evt)
   {
      fireEvent(evt, null);
   }

   /**
    * Dispatches the given event name, passing all arguments after the given
    * name to the registered listeners for the event.
    */
   public void fireEvent(mxEventObject evt, Object sender)
   {
      if(eventListeners != null && !eventListeners.isEmpty()
         && isEventsEnabled())
      {
         if(sender == null) {
            sender = getEventSource();
         }

         if(sender == null) {
            sender = this;
         }

         for(int i = 0; i < eventListeners.size(); i += 2) {
            String listen = (String) eventListeners.get(i);

            if(listen == null || listen.equals(evt.getName())) {
               ((mxIEventListener) eventListeners.get(i + 1)).invoke(
                  sender, evt);
            }
         }
      }
   }

   /**
    * Defines the requirements for an object that listens to an event source.
    */
   public interface mxIEventListener {

      /**
       * Called when the graph model has changed.
       *
       * @param sender Reference to the source of the event.
       * @param evt    Event object to be dispatched.
       */
      void invoke(Object sender, mxEventObject evt);

   }

}
