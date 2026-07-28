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
package inetsoft.web.admin.ai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminAiControllerTest {
   @Mock private AdminChangeService changeService;
   @Mock private Principal principal;
   private AdminAiController controller;

   @BeforeEach void setup() { controller = new AdminAiController(changeService); }

   @Test void changeDelegatesToService() {
      AdminChangeRequest req = new AdminChangeRequest();
      req.setProperty("max.rows");
      AdminChangeResult expected = new AdminChangeResult();
      expected.setStatus("verified");
      when(changeService.applyChange(req, principal)).thenReturn(expected);

      AdminChangeResult actual = controller.change(req, principal);

      assertEquals("verified", actual.getStatus());
      verify(changeService).applyChange(req, principal);
   }
}
