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
package inetsoft.web.viewsheet.controller;

import inetsoft.web.service.BinaryTransferService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug #79794: PresenterService returns null when there is no presenter image
 * (e.g. row/col out of bounds); the controller must answer with an empty 200
 * instead of throwing an NPE from BinaryTransferService.isEmpty. The code path
 * returns before any Spring bean (CacheFS) is reached, so no Spring context is needed.
 */
@Tag("core")
class PresenterControllerNullImageTest {
   @Test
   void isEmptyTreatsNullAsEmpty() {
      assertTrue(new BinaryTransferService(null).isEmpty(null));
   }

   @Test
   void tablePresenterWithNullImageWritesNothing() throws Exception {
      PresenterServiceProxy proxy = mock(PresenterServiceProxy.class);
      when(proxy.getPresenterImage(anyString(), anyString(), anyInt(), anyInt(), anyInt(),
                                   anyInt(), any(Principal.class)))
         .thenReturn(null);
      PresenterController controller =
         new PresenterController(proxy, new BinaryTransferService(null));
      HttpServletResponse response = mock(HttpServletResponse.class);

      controller.getPresenterImage("Table1", 99, 0, 10, 10, "rid", response,
                                   mock(Principal.class));

      verify(response, never()).getOutputStream();
   }

   @Test
   void textPresenterWithNullImageWritesNothing() throws Exception {
      PresenterServiceProxy proxy = mock(PresenterServiceProxy.class);
      when(proxy.getPresenterImage(anyString(), anyString(), anyInt(), anyInt(),
                                   org.mockito.ArgumentMatchers.anyBoolean(), anyInt(),
                                   any(Principal.class)))
         .thenReturn(null);
      PresenterController controller =
         new PresenterController(proxy, new BinaryTransferService(null));
      HttpServletResponse response = mock(HttpServletResponse.class);

      controller.getPresenterImage("Text1", 10, 10, false, 0, "rid", response,
                                   mock(Principal.class));

      verify(response, never()).getOutputStream();
   }
}
