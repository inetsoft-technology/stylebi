/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web;

import inetsoft.web.viewsheet.service.CommandDispatcher;

import java.io.Serializable;
import java.security.Principal;

/**
 * Class that encapsulates the processing performed by an aspect.
 */
public interface AspectTask extends Serializable {
   /**
    * Performs actions that should happen before the called method. When the method is proxied, this
    * is always called on the instance on which the proxied method is invoked.
    *
    * @param dispatcher       the command dispatcher for the client websocket connection.
    * @param contextPrincipal a principal that identifies the current user for the context.
    */
   default void preprocess(CommandDispatcher dispatcher, Principal contextPrincipal) {
   }

   /**
    * Performs actions that should happen after the called method. When the method is proxied, this
    * is always called on the instance on which the proxied method is invoked.
    *
    * @param dispatcher       the command dispatcher for the client websocket connection.
    * @param contextPrincipal a principal that identifies the current user for the context.
    */
   default void postprocess(CommandDispatcher dispatcher, Principal contextPrincipal) {
   }

   /**
    * Performs actions that should happen after the method invocation is complete. This method is
    * invoked after {@link #postprocess()}. It is called regardless if the invoked method completes
    * successfully or throws an exception. When the method is proxied, this is called on the calling
    * instance unless the proxy call is asynchronous, in which case it is called on the instance on
    * which the proxied method is invoked.
    */
   default void apply() {
   }
}
