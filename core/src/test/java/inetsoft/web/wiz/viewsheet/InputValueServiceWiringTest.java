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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.viewsheet.controller.OnClickService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.wiz.dispatch.CommandErrorException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug 80465: a second public constructor that passed a null OnClickService let a stale Spring AOT
 * bean definition (generated before the 4-arg constructor existed) instantiate the service with no
 * onClick service, so submit:true failed as a generic 500. There must be exactly one constructor,
 * the autowired one that takes OnClickService; and a null one must be a named refusal.
 *
 * <p>No Spring context is started here: constructor selection is asserted by reflection (what
 * both Spring and the AOT generator read), not through a mock of the choice.
 */
@Tag("core")
class InputValueServiceWiringTest {
   @Test
   void hasExactlyOneConstructorAndItIsAutowiredWithOnClickService() {
      Constructor<?>[] ctors = InputValueService.class.getDeclaredConstructors();

      assertEquals(1, ctors.length, "a second constructor invites a stale AOT/Spring choice");
      assertTrue(java.lang.reflect.Modifier.isPublic(ctors[0].getModifiers()));
      assertNotNull(ctors[0].getAnnotation(Autowired.class));
      assertTrue(List.of(ctors[0].getParameterTypes()).contains(OnClickService.class),
                 List.of(ctors[0].getParameterTypes()).toString());
   }

   @Test
   void submitWithoutAnOnClickServiceIsANamedRefusalBeforeAnyWrite() throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      VSInputService inputs = mock(VSInputService.class);
      InputValueService service =
         new InputValueService(sessions, inputs, mock(CoreLifecycleService.class), null);

      CommandErrorException ex = assertThrows(CommandErrorException.class,
         () -> service.setValue("tok", null, "T1", List.of("x"), "", true));

      assertTrue(ex.getErrors().get(0).contains("'submit' is unavailable"), ex.getErrors().toString());
      assertTrue(ex.getErrors().get(0).contains("Nothing was changed"));
      verifyNoInteractions(sessions, inputs);
   }
}
