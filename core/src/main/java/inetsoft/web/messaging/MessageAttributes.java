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
package inetsoft.web.messaging;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstraction for accessing attribute objects associated with a message.
 *
 * @since 12.3
 */
public class MessageAttributes {
   /**
    * Name of the standard reference to the message object: "message".
    *
    * @see #resolveReference(String)
    */
   public static final String REFERENCE_MESSAGE = "message";

   /**
    * Name of the standard reference to the message headers object: "headers".
    *
    * @see #resolveReference(String)
    */
   public static final String REFERENCE_HEADERS = "headers";

   /**
    * Creates a new instance of <tt>MessageAttributes</tt>.
    *
    * @param message the underlying message object.
    */
   public MessageAttributes(Message<?> message) {
      this.message = message;
      this.headerAccessor = StompHeaderAccessor.wrap(message);
      this.attributes = new ConcurrentHashMap<>();
      this.destructionCallbacks = new ConcurrentHashMap<>();
   }

   /**
    * Gets the underlying message object.
    *
    * @return the message.
    */
   public Message<?> getMessage() {
      return message;
   }

   /**
    * Gets the message header accessor.
    *
    * @return the header accessor.
    */
   public StompHeaderAccessor getHeaderAccessor() {
      return headerAccessor;
   }

   /**
    * Gets the value of an attribute.
    *
    * @param name the attribute name.
    *
    * @return the attribute value or <tt>null</tt> if it is not set.
    */
   public Object getAttribute(String name) {
      return attributes.get(name);
   }

   /**
    * Sets the value of an attribute.
    *
    * @param name  the attribute name.
    * @param value the attribute value.
    *
    * @return the previous value of the attribute or <tt>null</tt> if not set.
    */
   public Object setAttribute(String name, Object value) {
      return attributes.put(name, value);
   }

   /**
    * Removes an attribute.
    *
    * @param name the attribute name.
    *
    * @return the previous value of the attribute or <tt>null</tt> if not set.
    */
   public Object removeAttribute(String name) {
      removeDestructionCallback(name);
      return attributes.remove(name);
   }

   /**
    * Resolve the contextual reference for the given key, if any.
    *
    * @param key the contextual key.
    *
    * @return the corresponding object, or <tt>null</tt> if none found.
    */
   public Object resolveReference(String key) {
      Object value = null;

      if(REFERENCE_MESSAGE.equals(key)) {
         value = message;
      }
      else if(REFERENCE_HEADERS.equals(key)) {
         value = getHeaderAccessor();
      }

      return value;
   }

   /**
    * Register the given callback as to be executed after the message is handled.
    *
    * @param name     the name of the attribute to register the callback for.
    * @param callback the callback to be executed for destruction.
    */
   void registerDestructionCallback(String name, Runnable callback) {
      destructionCallbacks.put(name, callback);
   }

   /**
    * Remove the request destruction callback for the specified attribute, if any.
    *
    * @param name the name of the attribute to remove the callback for.
    */
   void removeDestructionCallback(String name) {
      destructionCallbacks.remove(name);
   }

   /**
    * Invoked when the underlying message has been handled.
    */
   void messageHandled() {
      for(Runnable runnable : destructionCallbacks.values()) {
         runnable.run();
      }

      destructionCallbacks.clear();
   }

   private final Message<?> message;
   private final StompHeaderAccessor headerAccessor;
   private final Map<String, Object> attributes;
   private final Map<String, Runnable> destructionCallbacks;
}
