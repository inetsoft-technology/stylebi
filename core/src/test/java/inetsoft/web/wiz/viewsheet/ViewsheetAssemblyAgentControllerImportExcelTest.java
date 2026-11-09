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

import inetsoft.test.*;
import inetsoft.web.wiz.pairing.SheetAgentFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug 80481: the route, the multipart binding and the enabled check of
 * {@code POST /api/wiz/v1/agent/viewsheet/{token}/form-table/import-excel}. What the import refuses
 * and why is {@link FormTableImportServiceTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetAssemblyAgentControllerImportExcelTest {
   private static final String ROUTE = "/api/wiz/v1/agent/viewsheet/tok/form-table/import-excel";

   private FormTableImportService importService;
   private SheetAgentFeature feature;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      importService = mock(FormTableImportService.class);
      ViewsheetAssemblyAgentController controller = new ViewsheetAssemblyAgentController(
         feature,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, importService, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
      mvc = MockMvcBuilders.standaloneSetup(controller).setPatternParser(null).build();
   }

   private static MockMultipartFile file(byte[] bytes) {
      return new MockMultipartFile("file", "export.xlsx", "application/octet-stream", bytes);
   }

   @Test
   void bindsTheMultipartFileAndTheFormFields() throws Exception {
      when(importService.importExcel(anyString(), any(), any(), any(), anyBoolean(), anyString()))
         .thenReturn(Map.of("ok", true, "applied", false));

      mvc.perform(multipart(ROUTE).file(file(new byte[] {1, 2, 3}))
                     .param("fileType", "XLSX").param("discardPending", "true")
                     .principal((Principal) () -> "agent"))
         .andExpect(status().isOk());

      verify(importService).importExcel(eq("tok"), any(), eq(new byte[] {1, 2, 3}), eq("XLSX"),
                                        eq(true), eq(""));
   }

   @Test
   void discardPendingDefaultsToFalse() throws Exception {
      when(importService.importExcel(anyString(), any(), any(), any(), anyBoolean(), anyString()))
         .thenReturn(Map.of("ok", true));

      mvc.perform(multipart(ROUTE).file(file(new byte[] {1})).param("fileType", "xlsx")
                     .principal((Principal) () -> "agent"))
         .andExpect(status().isOk());

      verify(importService).importExcel(eq("tok"), any(), any(), eq("xlsx"), eq(false), eq(""));
   }

   @Test
   void aMissingOrEmptyFileIsRefusedWithoutCallingTheService() throws Exception {
      try {
         mvc.perform(multipart(ROUTE).param("fileType", "xlsx")
                        .principal((Principal) () -> "agent"));
         org.junit.jupiter.api.Assertions.fail("expected a refusal");
      }
      catch(Exception e) {
         org.junit.jupiter.api.Assertions.assertTrue(
            String.valueOf(e.getCause()).contains("'file' is required"), String.valueOf(e));
      }

      verifyNoInteractions(importService);
   }

   @Test
   void isRefusedWhenSheetAgentPairingIsDisabled() throws Exception {
      when(feature.isEnabled()).thenReturn(false);

      mvc.perform(multipart(ROUTE).file(file(new byte[] {1})).param("fileType", "xlsx")
                     .principal((Principal) () -> "agent"))
         .andExpect(status().isForbidden());

      verifyNoInteractions(importService);
   }
}
