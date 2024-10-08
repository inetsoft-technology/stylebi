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
package inetsoft.sree.security;

import java.util.EventListener;

/**
 * Interface for classes that are notified when a user logs into or out of the
 * application.
 *
 * @since 12.3
 */
public interface SessionListener extends EventListener {
   /**
    * Invoked when a user logs into the application.
    *
    * @param event the session event.
    */
   void loggedIn(SessionEvent event);

   /**
    * Invoked when a user logs out of the application.
    *
    * @param event the session event.
    */
   void loggedOut(SessionEvent event);
}
