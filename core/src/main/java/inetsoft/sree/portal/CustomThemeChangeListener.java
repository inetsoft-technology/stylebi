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

package inetsoft.sree.portal;

import inetsoft.web.admin.security.IdentityService;

import java.util.EventListener;

/**
 * Listener that is notified when a message has been received from a cluster
 * node.
 */
public interface CustomThemeChangeListener extends EventListener {
   /**
    * Called when a message has been received from a cluster node.
    *
    * @param event the event.
    */
   void handle(IdentityService.ThemeChangeEvent event);
}

