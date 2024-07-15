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
package inetsoft.web.cluster;

import inetsoft.sree.internal.cluster.MessageEvent;

import java.io.Serializable;

public abstract class ServerClusterMessage implements Serializable {
   public final boolean isCompletedBy(MessageEvent event) {
      if(event.getMessage() instanceof ServerClusterCompleteMessage) {
         ServerClusterCompleteMessage message = (ServerClusterCompleteMessage) event.getMessage();
         return getCompletionAction().equals(message.getAction());
      }

      return false;
   }

   protected abstract String getCompletionAction();
}
