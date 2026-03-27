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
package inetsoft.web.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpSessionScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension of {@link SimpSessionScope} that gracefully handles the race condition where a
 * WebSocket-scoped bean is first accessed after the session has already completed (e.g., a
 * SUBSCRIBE message is still in the executor queue when the session ends). Rather than throwing
 * {@link IllegalStateException} from {@link #registerDestructionCallback}, this implementation
 * defers the callback and runs it at the end of message processing via
 * {@link MessageScopeInterceptor}, ensuring proper cleanup without leaking listeners or other
 * resources registered by the handler method.
 */
public class SafeSimpSessionScope extends SimpSessionScope {
   @Override
   public void registerDestructionCallback(String name, Runnable callback) {
      try {
         super.registerDestructionCallback(name, callback);
      }
      catch(IllegalStateException e) {
         LOG.debug(
            "WebSocket session already completed for bean '{}'; deferring destruction callback",
            name);
         DEFERRED_CALLBACKS.get().add(callback);
      }
   }

   /**
    * Returns all destruction callbacks deferred for the current thread and clears the list.
    * Called by {@link MessageScopeInterceptor} at the end of message processing.
    */
   static List<Runnable> getAndClearDeferredCallbacks() {
      List<Runnable> callbacks = new ArrayList<>(DEFERRED_CALLBACKS.get());
      DEFERRED_CALLBACKS.remove();
      return callbacks;
   }

   private static final ThreadLocal<List<Runnable>> DEFERRED_CALLBACKS =
      ThreadLocal.withInitial(ArrayList::new);
   private static final Logger LOG = LoggerFactory.getLogger(SafeSimpSessionScope.class);
}
