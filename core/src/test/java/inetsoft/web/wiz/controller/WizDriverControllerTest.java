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
package inetsoft.web.wiz.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.admin.content.plugins.PluginsService;
import inetsoft.web.admin.content.plugins.model.CreateDriverPluginRequest;
import inetsoft.web.admin.content.plugins.model.PluginModel;
import inetsoft.web.admin.content.plugins.model.PluginsModel;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.upload.UploadFilesResponse;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import inetsoft.web.wiz.WizControllerErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Covers {@code WizDriverController} (Bug #76697 AA-002): every endpoint is gated by the native
 * {@code UPLOAD_DRIVERS} permission, denial reaches the client as 403 through
 * {@code WizControllerErrorHandler}, and the work is delegated to the same services the native
 * driver wizard uses.
 */
@Tag("core")
class WizDriverControllerTest {
   private static final String BASE = "/api/wiz/databases/drivers";

   private SecurityEngine securityEngine;
   private UploadService uploadService;
   private PluginsService pluginsService;
   private DatabaseDatasourcesService databaseDatasourcesService;
   private Principal principal;
   private MockMvc mvc;

   @BeforeEach
   void setUp() throws Exception {
      securityEngine = mock(SecurityEngine.class);
      uploadService = mock(UploadService.class);
      pluginsService = mock(PluginsService.class);
      databaseDatasourcesService = mock(DatabaseDatasourcesService.class);
      principal = mock(Principal.class);
      mvc = standaloneSetup(new WizDriverController(
         uploadService, pluginsService, databaseDatasourcesService, securityEngine))
         .setControllerAdvice(new WizControllerErrorHandler())
         .build();
      allow(true);
   }

   private void allow(boolean allowed) throws Exception {
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.UPLOAD_DRIVERS), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(allowed);
   }

   private MockHttpServletRequestBuilder withPrincipal(MockHttpServletRequestBuilder b) {
      return b.principal(principal).accept(MediaType.APPLICATION_JSON);
   }

   private static MockMultipartFile jar(String name) {
      return new MockMultipartFile("uploadedFiles", name, "application/java-archive",
                                   new byte[] { 1, 2, 3 });
   }

   private static MockMultipartFile file(String name, byte[] content) {
      return new MockMultipartFile("uploadedFiles", name, null, content);
   }

   private static String createBody() {
      return "{\"uploadId\":\"up-1\",\"pluginId\":\"my.drv\",\"pluginName\":\"My Driver\"," +
         "\"pluginVersion\":\"1.0.0\",\"drivers\":[\"a.Driver\",\"b.Driver\"]}";
   }

   // ---- permission gate ------------------------------------------------------------------

   @Test
   void everyGatedEndpointRefusesAPrincipalWithoutUploadDriversWith403AndCallsNoService()
      throws Exception
   {
      allow(false);

      mvc.perform(withPrincipal(get(BASE + "/plugins"))).andExpect(status().isForbidden());
      mvc.perform(withPrincipal(multipart(BASE + "/upload").file(jar("a.jar"))))
         .andExpect(status().isForbidden());
      mvc.perform(withPrincipal(post(BASE + "/upload/maven")
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content("{\"gav\":\"g:a:1\"}")))
         .andExpect(status().isForbidden());
      mvc.perform(withPrincipal(get(BASE + "/maven-search").param("q", "ojdbc")))
         .andExpect(status().isForbidden());
      mvc.perform(withPrincipal(get(BASE + "/scan/abc"))).andExpect(status().isForbidden());
      mvc.perform(withPrincipal(post(BASE + "/create")
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(createBody())))
         .andExpect(status().isForbidden());

      verifyNoInteractions(uploadService);
      verifyNoInteractions(pluginsService);
   }

   @Test
   void capabilityIsNotGatedAndMirrorsIsUploadEnabled() throws Exception {
      when(databaseDatasourcesService.isUploadEnabled(principal)).thenReturn(false);
      mvc.perform(withPrincipal(get(BASE + "/capability")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.uploadEnabled").value(false));

      when(databaseDatasourcesService.isUploadEnabled(principal)).thenReturn(true);
      mvc.perform(withPrincipal(get(BASE + "/capability")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.uploadEnabled").value(true));
   }

   // ---- plugins --------------------------------------------------------------------------

   @Test
   void pluginsReturnsIdsOnly() throws Exception {
      PluginsModel model = PluginsModel.builder()
         .supportUploadDriver(true)
         .clustered(false)
         .addPlugins(PluginModel.builder().id("a.b").name("A").version("1.0").readOnly(false).build())
         .addPlugins(PluginModel.builder().id("c.d").name("C").version("2.0").readOnly(true).build())
         .build();
      when(pluginsService.getModel(principal)).thenReturn(model);

      mvc.perform(withPrincipal(get(BASE + "/plugins")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.pluginIds[0]").value("a.b"))
         .andExpect(jsonPath("$.pluginIds[1]").value("c.d"))
         .andExpect(jsonPath("$.plugins").doesNotExist());
   }

   // ---- upload ---------------------------------------------------------------------------

   @Test
   @SuppressWarnings("unchecked")
   void uploadOfSeveralJarsWithoutIdKeepsAllNamesInOneUpload() throws Exception {
      when(uploadService.add(anyList())).thenReturn("upload-1");

      mvc.perform(withPrincipal(multipart(BASE + "/upload")
                                   .file(jar("a.jar")).file(jar("b.JAR"))))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.identifier").value("upload-1"))
         .andExpect(jsonPath("$.files[0]").value("a.jar"))
         .andExpect(jsonPath("$.files[1]").value("b.JAR"));

      ArgumentCaptor<List<UploadedFile>> captor = ArgumentCaptor.forClass(List.class);
      verify(uploadService).add(captor.capture());
      assertEquals(2, captor.getValue().size());
      verify(uploadService, never()).add(anyString(), anyList());
   }

   @Test
   void uploadWithIdAppendsToTheExistingUploadAndReportsAllFiles() throws Exception {
      UploadedFile existing = UploadedFile.builder().fileName("a.jar").build();
      UploadedFile added = UploadedFile.builder().fileName("b.jar").build();
      when(uploadService.add(eq("up-1"), anyList())).thenReturn(List.of(existing, added));

      mvc.perform(withPrincipal(multipart(BASE + "/upload").file(jar("b.jar")).param("id", "up-1")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.identifier").value("up-1"))
         .andExpect(jsonPath("$.files[0]").value("a.jar"))
         .andExpect(jsonPath("$.files[1]").value("b.jar"));
      verify(uploadService, never()).add(anyList());
   }

   @Test
   void uploadWithUnknownIdIs400() throws Exception {
      when(uploadService.add(eq("nope"), anyList())).thenThrow(new FileNotFoundException("nope"));

      mvc.perform(withPrincipal(multipart(BASE + "/upload").file(jar("a.jar")).param("id", "nope")))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").exists());
   }

   @Test
   void uploadRejectsEmptyAndNonJarFilesWithoutCallingTheService() throws Exception {
      mvc.perform(withPrincipal(multipart(BASE + "/upload").file(file("a.jar", new byte[0]))))
         .andExpect(status().isBadRequest());
      mvc.perform(withPrincipal(multipart(BASE + "/upload")
                                   .file(file("plugin.zip", new byte[] { 1 }))))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").exists());
      mvc.perform(withPrincipal(multipart(BASE + "/upload")
                                   .file(jar("ok.jar")).file(file("x.txt", new byte[] { 1 }))))
         .andExpect(status().isBadRequest());

      verifyNoInteractions(uploadService);
   }

   // ---- maven ----------------------------------------------------------------------------

   @Test
   void mavenDelegatesToUploadServiceAdd() throws Exception {
      when(uploadService.add("com.x:drv:1.0")).thenReturn(
         UploadFilesResponse.builder().identifier("m-1").addFiles("drv-1.0.jar").build());

      mvc.perform(withPrincipal(post(BASE + "/upload/maven")
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content("{\"gav\":\"com.x:drv:1.0\"}")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.identifier").value("m-1"))
         .andExpect(jsonPath("$.files[0]").value("drv-1.0.jar"));
   }

   @Test
   void mavenResolutionFailureIs400CarryingTheMessage() throws Exception {
      when(uploadService.add("com.x:drv:9")).thenThrow(
         new RuntimeException("unresolved dependency: com.x#drv;9: not found"));

      mvc.perform(withPrincipal(post(BASE + "/upload/maven")
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content("{\"gav\":\"com.x:drv:9\"}")))
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.error").value("unresolved dependency: com.x#drv;9: not found"));
   }

   @Test
   void mavenSearchDelegates() throws Exception {
      when(uploadService.search("ojdbc")).thenReturn(List.of("com.oracle:ojdbc8:19.3"));

      mvc.perform(withPrincipal(get(BASE + "/maven-search").param("q", "ojdbc")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.results[0]").value("com.oracle:ojdbc8:19.3"));
   }

   // ---- scan / create --------------------------------------------------------------------

   @Test
   void scanDelegatesAndAnEmptyResultIsA200LikeNative() throws Exception {
      when(pluginsService.scanDrivers("up-1", principal)).thenReturn(List.of("a.Driver"));
      when(pluginsService.scanDrivers("up-2", principal)).thenReturn(List.of());

      mvc.perform(withPrincipal(get(BASE + "/scan/up-1")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.drivers[0]").value("a.Driver"));
      mvc.perform(withPrincipal(get(BASE + "/scan/up-2")))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.drivers").isEmpty());
   }

   @Test
   void createBuildsTheExactRequestAndDelegates() throws Exception {
      mvc.perform(withPrincipal(post(BASE + "/create")
                                   .contentType(MediaType.APPLICATION_JSON)
                                   .content(createBody())))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.ok").value(true));

      ArgumentCaptor<CreateDriverPluginRequest> captor =
         ArgumentCaptor.forClass(CreateDriverPluginRequest.class);
      verify(pluginsService).createDriverPlugin(captor.capture(), eq(principal));
      CreateDriverPluginRequest request = captor.getValue();
      assertEquals("up-1", request.uploadId());
      assertEquals("my.drv", request.pluginId());
      assertEquals("My Driver", request.pluginName());
      assertEquals("1.0.0", request.pluginVersion());
      assertEquals(List.of("a.Driver", "b.Driver"), request.drivers());
   }
}
