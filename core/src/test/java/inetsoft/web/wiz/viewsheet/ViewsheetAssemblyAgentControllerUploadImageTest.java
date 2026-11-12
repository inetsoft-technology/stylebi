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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bug 81224: the route, the multipart binding and the enabled check of
 * {@code POST /api/wiz/v1/agent/viewsheet/{token}/images}. What the upload refuses and why is
 * {@link ViewsheetImageUploadServiceTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetAssemblyAgentControllerUploadImageTest {
   private static final String ROUTE = "/api/wiz/v1/agent/viewsheet/tok/images";

   private ViewsheetImageUploadService uploadService;
   private SheetAgentFeature feature;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      uploadService = mock(ViewsheetImageUploadService.class);
      ViewsheetAssemblyAgentController controller = new ViewsheetAssemblyAgentController(
         feature,
         null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, uploadService);
      mvc = MockMvcBuilders.standaloneSetup(controller).setPatternParser(null).build();
   }

   private static MockMultipartFile file(byte[] bytes) {
      return new MockMultipartFile("file", "logo.png", "application/octet-stream", bytes);
   }

   @Test
   void usesTheUploadedFilesOwnNameWhenNoNameIsGiven() throws Exception {
      when(uploadService.upload(anyString(), any(), any(), anyString()))
         .thenReturn(Map.of("ok", true));

      mvc.perform(multipart(ROUTE).file(file(new byte[] {1, 2, 3}))
                     .principal((Principal) () -> "agent"))
         .andExpect(status().isOk());

      verify(uploadService).upload(eq("tok"), any(), eq(new byte[] {1, 2, 3}), eq("logo.png"));
   }

   @Test
   void anExplicitNameWinsOverTheFilesOwnName() throws Exception {
      when(uploadService.upload(anyString(), any(), any(), anyString()))
         .thenReturn(Map.of("ok", true));

      mvc.perform(multipart(ROUTE).file(file(new byte[] {1})).param("name", "header.png")
                     .principal((Principal) () -> "agent"))
         .andExpect(status().isOk());

      verify(uploadService).upload(eq("tok"), any(), any(), eq("header.png"));
   }

   @Test
   void aMissingFileIsRefusedWithoutCallingTheService() throws Exception {
      try {
         mvc.perform(multipart(ROUTE).principal((Principal) () -> "agent"));
         fail("expected a refusal");
      }
      catch(Exception e) {
         assertTrue(String.valueOf(e.getCause()).contains("'file' is required"), String.valueOf(e));
      }

      verifyNoInteractions(uploadService);
   }

   @Test
   void isRefusedWhenSheetAgentPairingIsDisabled() throws Exception {
      when(feature.isEnabled()).thenReturn(false);

      mvc.perform(multipart(ROUTE).file(file(new byte[] {1}))
                     .principal((Principal) () -> "agent"))
         .andExpect(status().isForbidden());

      verifyNoInteractions(uploadService);
   }
}
