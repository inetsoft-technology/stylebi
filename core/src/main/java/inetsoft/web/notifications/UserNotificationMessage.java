/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.notifications;

import java.io.Serializable;

/**
 * Cluster message that forwards a notification for one user, sent on one node, to the
 * {@link NotificationService} of the other nodes, so that it reaches the user's web socket on
 * whichever node it is connected to. It is a separate type from {@link NotificationMessage},
 * which every node broadcasts to all connected users.
 */
public final class UserNotificationMessage implements Serializable {
   /**
    * Creates a new instance of <tt>UserNotificationMessage</tt>.
    *
    * @param destination  the user destination name, as resolved by
    *                     <tt>SUtil.getUserDestination(Principal)</tt> on the sending node.
    * @param notification the notification.
    */
   public UserNotificationMessage(String destination, NotificationMessage notification) {
      this.destination = destination;
      this.notification = notification;
   }

   /**
    * Gets the user destination name.
    */
   public String getDestination() {
      return destination;
   }

   /**
    * Gets the notification.
    */
   public NotificationMessage getNotification() {
      return notification;
   }

   @Override
   public String toString() {
      return "UserNotificationMessage{" +
         "destination='" + destination + '\'' +
         ", notification=" + notification +
         '}';
   }

   private final String destination;
   private final NotificationMessage notification;
}
