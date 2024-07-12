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
package inetsoft.web.viewsheet.command;

import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.UserMessage;
import inetsoft.web.composer.ws.event.AssetEvent;

import javax.annotation.Nullable;
import java.util.HashMap;

/**
 * Command used to instruct the client to display a message to the user.
 *
 * @since 12.3
 */
public class MessageCommand implements ViewsheetCommand {
   public MessageCommand() {
   }

   public static MessageCommand fromUserMessage(UserMessage userMessage) {
      final MessageCommand messageCommand = new MessageCommand();
      final String message = userMessage.getMessage();
      final Type type = Type.fromCode(userMessage.getLevel());
      final String assemblyName = userMessage.getAssemblyName();
      messageCommand.setMessage(message);
      messageCommand.setType(type);
      messageCommand.setAssemblyName(assemblyName);
      return messageCommand;
   }

   public String getMessage() {
      return message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public Type getType() {
      return type;
   }

   public void setType(Type type) {
      this.type = type;
   }

   /**
    * Get the events.
    * @return events.
    */
   public HashMap<String, AssetEvent> getEvents() {
      return events;
   }

   /**
    * Set events.
    * @param events the events.
    */
   public void setEvents(HashMap<String, AssetEvent> events) {
      this.events = events;
   }

   /**
    * Add event.
    */
   public void addEvent(String url, AssetEvent event) {
      events.put(url, event);
   }

   public HashMap<String, AssetEvent> getNoEvents() {
      return noEvents;
   }

   @Nullable
   public void setNoEvents(HashMap<String, AssetEvent> noEvents) {
      this.noEvents = noEvents;
   }

   /**
    * Add event.
    */
   public void addNoEvent(String url, AssetEvent event) {
      noEvents.put(url, event);
   }

   /**
    * Get the assembly name associated with this message command
    * @return assembly name
    */
   public String getAssemblyName() {
      return assemblyName;
   }

   /**
    * Set the assembly name associated with this message command
    * @param assemblyName assembly name
    */
   public void setAssemblyName(String assemblyName) {
      this.assemblyName = assemblyName;
   }

   private String message;
   private Type type = Type.INFO;
   private HashMap<String, AssetEvent> events = new HashMap<>();
   private HashMap<String, AssetEvent> noEvents = new HashMap<>();
   private String assemblyName;

   public enum Type {
      OK(ConfirmException.OK),
      TRACE(ConfirmException.TRACE),
      DEBUG(ConfirmException.DEBUG),
      INFO(ConfirmException.INFO),
      WARNING(ConfirmException.WARNING),
      ERROR(ConfirmException.ERROR),
      CONFIRM(ConfirmException.CONFIRM),
      PROGRESS(ConfirmException.PROGRESS),
      OVERRIDE(ConfirmException.OVERRIDE);

      private final int code;

      Type(int code) {
         this.code = code;
      }

      public int code() {
         return code;
      }

      public static Type fromCode(int code) {
         Type result = null;

         for(Type type : values()) {
            if(type.code == code) {
               result = type;
               break;
            }
         }

         if(result == null) {
            throw new IllegalArgumentException("Invalid message type code: " + code);
         }

         return result;
      }
   }
}
