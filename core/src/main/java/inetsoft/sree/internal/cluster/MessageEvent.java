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
package inetsoft.sree.internal.cluster;

import java.util.EventObject;

/**
 * Event that signals that a message has been received from a cluster node.
 */
public final class MessageEvent extends EventObject {
   /**
    * Creates a new instance of <tt>MessageEvent</tt>.
    *
    * @param source  the source of the event.
    * @param sender  the address of the server that sent the message.
    * @param local   <tt>true</tt> if the message originated from this node.
    * @param message the message object.
    */
   public MessageEvent(Object source, String sender, boolean local, Object message) {
      super(source);
      this.sender = sender;
      this.local = local;
      this.message = message;
   }

   /**
    * Gets the address of the server that sent the message.
    *
    * @return the sender.
    */
   public String getSender() {
      return sender;
   }

   /**
    * Gets the flag that indicates if this message was sent from this cluster node.
    *
    * @return <tt>true</tt> if the message originated from this node; <tt>false</tt>
    *         otherwise.
    */
   public boolean isLocal() {
      return local;
   }

   /**
    * Gets the message object.
    *
    * @return the message.
    */
   public Object getMessage() {
      return message;
   }

   @Override
   public String toString() {
      return "MessageEvent{" +
         "sender='" + sender + '\'' +
         ", local=" + local +
         ", message=" + message +
         '}';
   }

   private final String sender;
   private final boolean local;
   private final Object message;
}
