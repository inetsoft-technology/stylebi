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
package inetsoft.sree.internal.cluster.ignite;

import java.io.Serializable;

/**
 * Sent by the service executor back to the originating node via Ignite messaging to complete
 * the caller's {@code CompletableFuture}.
 */
class ServiceTaskResult implements Serializable {
   static ServiceTaskResult success(String taskId, Serializable result) {
      return new ServiceTaskResult(taskId, result, null);
   }

   static ServiceTaskResult failure(String taskId, Exception exception) {
      // Ensure the exception itself is serializable so Ignite messaging can carry it.
      // Wrap in RuntimeException as a fallback if it is not.
      Exception toSend = exception instanceof Serializable
         ? exception
         : new RuntimeException(exception.getMessage());
      return new ServiceTaskResult(taskId, null, toSend);
   }

   private ServiceTaskResult(String taskId, Serializable result, Exception exception) {
      this.taskId = taskId;
      this.result = result;
      this.exception = exception;
   }

   String getTaskId() {
      return taskId;
   }

   Serializable getResult() {
      return result;
   }

   Exception getException() {
      return exception;
   }

   boolean isSuccess() {
      return exception == null;
   }

   private final String taskId;
   private final Serializable result;
   private final Exception exception;
}
