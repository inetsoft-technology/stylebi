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
package inetsoft.web.wiz.pairing;

import inetsoft.web.viewsheet.service.CommandDispatcherService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test doubles for {@link CommandDispatcherService}. */
final class TestDispatchers {
   private TestDispatchers() {
   }

   /**
    * A dispatcher mock whose every browser session is live, so a broadcast reaches
    * {@code convertAndSendToUser}; a bare mock reports every session gone and the broadcast then
    * sends nothing.
    */
   static CommandDispatcherService live() {
      CommandDispatcherService dispatcher = mock(CommandDispatcherService.class);
      when(dispatcher.isSessionLive(any())).thenReturn(true);
      return dispatcher;
   }
}
