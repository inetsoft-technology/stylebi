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
package inetsoft.report.io.viewsheet.ppt;

import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79696: the PPTVSExporter export drew the value of a password text input in clear text.
 */
class TextInputPasswordPPTTest {
   private static final String SECRET = "Zq7secretVXP014";
   private static final String VISIBLE = "Zq7visibleVXP014";

   private static List<String> drawnText(boolean password, String value) throws Exception {
      // a real assembly info needs a bootstrapped server (SreeEnv), so mock it like the other
      // tests in this module and keep the real password logic
      TextInputVSAssemblyInfo info = mock(TextInputVSAssemblyInfo.class);
      when(info.getColumnOption()).thenReturn(
         password ? new PasswordColumnOption() : new TextColumnOption());
      when(info.isPassword()).thenCallRealMethod();
      when(info.getExportText(any())).thenCallRealMethod();
      TextInputVSAssembly assembly = mock(TextInputVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      when(assembly.getSelectedObject()).thenReturn(value);
      when(assembly.getDataType()).thenReturn("string");

      List<String> texts = new ArrayList<>();
      Answer<Object> answer = inv -> {
         if("writeText".equals(inv.getMethod().getName()) && inv.getArguments().length == 2) {
            texts.add((String) inv.getArgument(1));
            return null;
         }

         return inv.callRealMethod();
      };

      PPTVSExporter exporter = mock(PPTVSExporter.class, withSettings().defaultAnswer(answer));
      exporter.writeTextInput(assembly);
      return texts;
   }

   @Test
   void passwordValueIsMasked() throws Exception {
      List<String> texts = drawnText(true, SECRET);
      assertEquals(List.of("******"), texts);
   }

   @Test
   void plainValueIsKept() throws Exception {
      assertEquals(List.of(VISIBLE), drawnText(false, VISIBLE));
   }
}
