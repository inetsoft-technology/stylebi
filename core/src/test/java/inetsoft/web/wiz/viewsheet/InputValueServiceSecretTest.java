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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.web.viewsheet.service.VSInputService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug 79261: {@code set_input_value} read the stored value back into {@code values} for every text
 * input, so a Password editor's secret was echoed in clear. The write must still succeed and the
 * count must survive, but the response must never carry the plaintext; a Text editor still echoes.
 */
@Tag("core")
class InputValueServiceSecretTest {
   private static final String SECRET = "s3cr3t-Hunter2";

   private static Map<String, Object> set(ColumnOption option, List<Object> stored,
                                          List<Object> requested) throws Exception
   {
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(option);
      when(info.getSelectedObjects()).thenReturn(stored.toArray());

      TextInputVSAssembly assembly = mock(TextInputVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getDataType()).thenReturn("string");

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Pw")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(any(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         m.run(rvs, "rt", null);
         return List.of();
      });

      return new InputValueService(sessions, mock(VSInputService.class))
         .setValue("tok", null, "Pw", requested, "");
   }

   @Test
   void passwordEditorNeverEchoesTheValue() throws Exception {
      Map<String, Object> r = set(new PasswordColumnOption(), List.of(SECRET), List.of(SECRET));

      assertEquals(1, r.get("valueCount"));
      assertEquals(List.of(InputValueService.SECRET_MASK), r.get("values"));
      assertEquals(true, r.get("masked"));
      assertEquals(false, r.get("adjusted"));
      assertFalse(r.toString().contains(SECRET), r.toString());
   }

   @Test
   void passwordEditorStillReportsAnAdjustmentWithoutTheValue() throws Exception {
      Map<String, Object> r = set(new PasswordColumnOption(), List.of("trimmed"), List.of(SECRET));

      assertEquals(true, r.get("adjusted"));
      assertFalse(r.toString().contains(SECRET), r.toString());
      assertFalse(r.toString().contains("trimmed"), r.toString());
   }

   @Test
   void clearingAPasswordKeepsAnEmptyList() throws Exception {
      Map<String, Object> r = set(new PasswordColumnOption(), List.of(), List.of());

      assertEquals(0, r.get("valueCount"));
      assertEquals(List.of(), r.get("values"));
      assertEquals(true, r.get("masked"));
   }

   @Test
   void textEditorStillEchoes() throws Exception {
      Map<String, Object> r = set(new TextColumnOption(), List.of("hello"), List.of("hello"));

      assertEquals(List.of("hello"), r.get("values"));
      assertNull(r.get("masked"));
      assertNull(r.get("adjusted"));
   }
}
