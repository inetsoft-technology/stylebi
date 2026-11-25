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

import inetsoft.web.wiz.pairing.SheetAgentFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Bug #82316: input Spring cannot bind gets the contract's {@code error}/{@code errorCode} body. */
@Tag("core")
class ViewsheetConsoleControllerTest {
   private final Principal user = () -> "agent";
   private SheetAgentFeature feature;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      mvc = MockMvcBuilders.standaloneSetup(
         new ViewsheetConsoleController(feature, mock(ViewsheetConsoleService.class)))
         .setMessageConverters(new MappingJackson2HttpMessageConverter()).build();
   }

   @Test
   void aLimitThatIsNotANumberIsTheContractErrorShape() throws Exception {
      mvc.perform(get("/api/wiz/v1/agent/viewsheet/tok/console?limit=abc").principal(user))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
         .andExpect(jsonPath("$.error").value("The value of the \"limit\" parameter is not valid"));
   }

   @Test
   void aBodyThatIsNotJsonIsTheContractErrorShape() throws Exception {
      mvc.perform(post("/api/wiz/v1/agent/viewsheet/tok/console/levels").principal(user)
                     .contentType(MediaType.APPLICATION_JSON).content("not json"))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"))
         .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("The request body")));
   }

   @Test
   void aDisabledFeatureIsForbiddenWithTheContractErrorShape() throws Exception {
      when(feature.isEnabled()).thenReturn(false);

      mvc.perform(post("/api/wiz/v1/agent/viewsheet/tok/console/clear").principal(user))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.errorCode").value("FEATURE_DISABLED"));
   }
}
