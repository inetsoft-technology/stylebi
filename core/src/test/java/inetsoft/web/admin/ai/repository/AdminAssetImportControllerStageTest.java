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
package inetsoft.web.admin.ai.repository;

import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogManager;
import inetsoft.web.GlobalExceptionHandler;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.model.FileData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.xml.stream.XMLStreamException;
import java.io.EOFException;
import java.io.IOException;
import java.security.Principal;
import java.util.zip.ZipException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Controller-level tests for {@code AdminAssetImportController.stage}: an upload that cannot be
 * parsed as a repository export answers 400 {@code {status:"failed", error:"file: ..."}} naming
 * the reason, while a server fault stays a 500. Both advices are registered, {@link
 * GlobalExceptionHandler} first, as in the running app, where its bodiless IOException handler
 * outranks {@link AdminExceptionHandler}.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminAssetImportControllerStageTest {
   @Mock private ImportAssetServiceProxy importService;
   @Mock private RepositoryImportChangePlanService planService;
   @Mock private AdminAssetImportApplyService applyService;
   @Mock private OrganizationManager orgManager;
   @Mock private LogManager logManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockMvc mvc;

   private static final Principal TEST_PRINCIPAL = () -> "test-user";

   @BeforeEach void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(TEST_PRINCIPAL)).thenReturn(true);

      mvc = standaloneSetup(
         new AdminAssetImportController(importService, planService, applyService))
         .setControllerAdvice(new GlobalExceptionHandler(), new AdminExceptionHandler(logManager))
         .setMessageConverters(new MappingJackson2HttpMessageConverter())
         .build();
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
   }

   private ResultActions stage(byte[] content) throws Exception {
      return mvc.perform(multipart("/api/wiz/v1/admin/repository/import/stage")
            .file(new MockMultipartFile("file", "export.zip", "application/zip", content))
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"));
   }

   private void stubSetJarFileThrows(Exception e) throws Exception {
      when(importService.setJarFile(anyString(), any(FileData.class), any())).thenThrow(e);
   }

   private void assertRejectedAs400(ResultActions result, String... reasonFragments)
      throws Exception
   {
      result.andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.status").value("failed"))
         .andExpect(jsonPath("$.error").value(containsString("file: not a repository export")));

      for(String fragment : reasonFragments) {
         result.andExpect(jsonPath("$.error").value(containsString(fragment)));
      }
   }

   @Test void zipWithoutManifestAnswers400NamingTheManifest() throws Exception {
      stubSetJarFileThrows(new DeployManagerService.JarFileInfoMissingException());

      assertRejectedAs400(stage(new byte[] { 1 }), "JarFileInfo.xml missing");
   }

   @Test void truncatedZipAnswers400() throws Exception {
      stubSetJarFileThrows(new ZipException("unexpected EOF"));

      assertRejectedAs400(stage(new byte[] { 1 }), "unexpected EOF");
   }

   @Test void corruptZipStreamAnswers400() throws Exception {
      stubSetJarFileThrows(new EOFException("Unexpected end of ZLIB input stream"));

      assertRejectedAs400(stage(new byte[] { 1 }), "Unexpected end of ZLIB input stream");
   }

   @Test void malformedManifestXmlAnswers400() throws Exception {
      // CoreTool.parseXML wraps the StAX failure in a RuntimeException
      stubSetJarFileThrows(new RuntimeException(new XMLStreamException("Unexpected EOF in prolog")));

      assertRejectedAs400(stage(new byte[] { 1 }), "JarFileInfo.xml is not well-formed XML",
                          "Unexpected EOF in prolog");
   }

   @Test void serverIoFaultStays500() throws Exception {
      stubSetJarFileThrows(new IOException("No space left on device"));

      stage(new byte[] { 1 }).andExpect(status().isInternalServerError());
   }

   @Test void messageExceptionStays500() throws Exception {
      stubSetJarFileThrows(new MessageException("autosave entries present"));

      stage(new byte[] { 1 }).andExpect(status().isInternalServerError());
   }

   @Test void unrelatedRuntimeExceptionStays500() throws Exception {
      stubSetJarFileThrows(new IllegalStateException(
         "Current session is missing the imported jar info."));

      stage(new byte[] { 1 }).andExpect(status().isInternalServerError());
   }

   @Test void emptyFileAnswers400WithoutCallingTheService() throws Exception {
      stage(new byte[0])
         .andExpect(status().isBadRequest())
         .andExpect(jsonPath("$.status").value("failed"))
         .andExpect(jsonPath("$.error").value("file: required and must not be empty"));

      verifyNoInteractions(importService);
   }

   @Test void validExportPassesThrough() throws Exception {
      ExportedAssetsModel model =
         ExportedAssetsModel.builder().importId("imp-1").name("exp").build();
      when(importService.setJarFile(anyString(), any(FileData.class), any())).thenReturn(model);

      stage(new byte[] { 1 })
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.name").value("exp"));
   }
}
