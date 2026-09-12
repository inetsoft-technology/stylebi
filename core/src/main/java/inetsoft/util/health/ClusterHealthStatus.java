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
package inetsoft.util.health;

import java.io.Serializable;

/**
 * {@code ClusterHealthStatus} contains the health status of the cluster.
 */
public class ClusterHealthStatus implements Serializable {
   public ClusterHealthStatus(boolean ready, String message) {
      this.ready = ready;
      this.message = message;
   }

   /**
    * Returns whether the cluster is ready for operations.
    *
    * @return {@code true} if the cluster is ready, {@code false} otherwise.
    */
   public boolean isReady() {
      return ready;
   }

   /**
    * Returns a message describing the cluster status.
    *
    * @return the status message.
    */
   public String getMessage() {
      return message;
   }

   private final boolean ready;
   private final String message;
}
