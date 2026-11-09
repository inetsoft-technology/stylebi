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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Bug #80482: the selection endpoints hand the request's discardPending to the service, and an
 * absent field stays null (older plugins never send it).
 */
@Tag("core")
class ViewsheetAssemblyAgentControllerDiscardPendingTest {
   private final SelectionRuntimeService selections = mock(SelectionRuntimeService.class);
   private final ViewsheetAssemblyAgentController controller = controller(selections);
   private final Principal user = () -> "admin";

   @Test
   void setSelectionPassesDiscardPendingThrough() throws Exception {
      controller.setSelection("tok", new ViewsheetAssemblyAgentController.SelectionRequest(
         "F", List.of(List.of("a")), null, null, null, null, null, true), "", user);

      verify(selections).setSelection(eq("tok"), eq(user), eq("F"), any(), any(), any(), any(),
                                      any(), any(), eq(true), anyString());
   }

   @Test
   void clearSelectionPassesDiscardPendingThrough() throws Exception {
      controller.clearSelection("tok", new ViewsheetAssemblyAgentController.SelectionRequest(
         "F", null, null, null, null, null, null, true), "", user);

      verify(selections).clearSelection(eq("tok"), eq(user), eq("F"), eq(true), anyString());
   }

   @Test
   void selectSubtreePassesDiscardPendingThrough() throws Exception {
      controller.selectSubtree("tok", new ViewsheetAssemblyAgentController.SubtreeRequest(
         "T", List.of("East"), "select", true), "", user);

      verify(selections).selectSubtree(eq("tok"), eq(user), eq("T"), eq(List.of("East")),
                                       eq("select"), eq(true), anyString());
   }

   @Test
   void anAbsentFieldDeserializesToNullAndIsPassedAsNull() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ViewsheetAssemblyAgentController.SelectionRequest request = mapper.readValue(
         "{\"assembly\":\"F\"}", ViewsheetAssemblyAgentController.SelectionRequest.class);
      assertNull(request.discardPending());

      ViewsheetAssemblyAgentController.SelectionRequest sent = mapper.readValue(
         "{\"assembly\":\"F\",\"discardPending\":true}",
         ViewsheetAssemblyAgentController.SelectionRequest.class);
      assertEquals(Boolean.TRUE, sent.discardPending());

      ViewsheetAssemblyAgentController.SubtreeRequest subtree = mapper.readValue(
         "{\"assembly\":\"T\",\"path\":[\"East\"],\"mode\":\"clear\",\"discardPending\":true}",
         ViewsheetAssemblyAgentController.SubtreeRequest.class);
      assertEquals(Boolean.TRUE, subtree.discardPending());
   }

   private static ViewsheetAssemblyAgentController controller(SelectionRuntimeService service) {
      try {
         Constructor<?> constructor =
            ViewsheetAssemblyAgentController.class.getConstructors()[0];
         Class<?>[] types = constructor.getParameterTypes();
         Object[] args = new Object[types.length];

         for(int i = 0; i < types.length; i++) {
            args[i] = types[i] == SelectionRuntimeService.class ? service : mock(types[i]);
         }

         for(int i = 0; i < types.length; i++) {
            if(types[i] == SheetAgentFeature.class) {
               org.mockito.Mockito.when(((SheetAgentFeature) args[i]).isEnabled()).thenReturn(true);
            }
         }

         return (ViewsheetAssemblyAgentController) constructor.newInstance(args);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }
}
