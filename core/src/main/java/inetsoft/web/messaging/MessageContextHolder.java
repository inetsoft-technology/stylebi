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
package inetsoft.web.messaging;

/**
 * Class that exposes the web socket message as a thread-bound {@link MessageAttributes}
 * object.
 */
public final class MessageContextHolder {
   private MessageContextHolder() {
      // prevent instantiation
   }

   /**
    * Gets the message attributes bound to the current thread.
    *
    * @return the message attributes.
    */
   public static MessageAttributes getMessageAttributes() {
      return attributes.get();
   }

   public static MessageAttributes currentMessageAttributes() {
      MessageAttributes messageAttributes = getMessageAttributes();

      if(messageAttributes == null) {
         throw new IllegalStateException(
            "No thread-bound message found: Are you referring to message attributes " +
            "outside of an actual message processing thread?");
      }

      return messageAttributes;
   }

   /**
    * Sets the message attributes that are bound to the current thread.
    *
    * @param messageAttributes the message attributes or <tt>null</tt> to clear the
    *                          currently bound attributes.
    */
   public static void setMessageAttributes(MessageAttributes messageAttributes) {
      if(messageAttributes == null) {
         attributes.remove();
      }
      else {
         attributes.set(messageAttributes);
      }
   }

   private static final ThreadLocal<MessageAttributes> attributes = new ThreadLocal<>();
}
