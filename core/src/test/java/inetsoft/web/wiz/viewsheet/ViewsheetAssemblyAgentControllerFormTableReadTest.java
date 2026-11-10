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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug #80478: the form-table/read endpoint defaults offset to 0 and limit to 50 and hands both,
 * with the assembly, to {@link FormTableRowService#read}.
 */
@Tag("core")
class ViewsheetAssemblyAgentControllerFormTableReadTest {
   private final FormTableRowService rows = mock(FormTableRowService.class);
   private final ViewsheetAssemblyAgentController controller = controller(rows);
   private final Principal user = () -> "admin";

   @Test
   void passesOffsetAndLimitThrough() throws Exception {
      when(rows.read(any(), any(), any(), eq(120), eq(30), any())).thenReturn(Map.of("ok", true));

      controller.formTableRead("tok", new ViewsheetAssemblyAgentController.FormTableReadRequest(
         "Table1", 120, 30), "", user);

      verify(rows).read("tok", user, "Table1", 120, 30, "");
   }

   @Test
   void defaultsToTheFirstFiftyRows() throws Exception {
      controller.formTableRead("tok", new ViewsheetAssemblyAgentController.FormTableReadRequest(
         "Table1", null, null), "", user);

      verify(rows).read("tok", user, "Table1", 0, 50, "");
   }

   @Test
   void anAbsentFieldDeserializesToNull() throws Exception {
      ViewsheetAssemblyAgentController.FormTableReadRequest request = new ObjectMapper().readValue(
         "{\"assembly\":\"Table1\"}", ViewsheetAssemblyAgentController.FormTableReadRequest.class);

      assertEquals("Table1", request.assembly());
      assertNull(request.offset());
      assertNull(request.limit());
   }

   private static ViewsheetAssemblyAgentController controller(FormTableRowService service) {
      try {
         Constructor<?> constructor =
            ViewsheetAssemblyAgentController.class.getConstructors()[0];
         Class<?>[] types = constructor.getParameterTypes();
         Object[] args = new Object[types.length];

         for(int i = 0; i < types.length; i++) {
            args[i] = types[i] == FormTableRowService.class ? service : mock(types[i]);

            if(types[i] == SheetAgentFeature.class) {
               when(((SheetAgentFeature) args[i]).isEnabled()).thenReturn(true);
            }
         }

         return (ViewsheetAssemblyAgentController) constructor.newInstance(args);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
