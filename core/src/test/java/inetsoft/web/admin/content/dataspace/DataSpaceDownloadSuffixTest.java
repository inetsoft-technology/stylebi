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
package inetsoft.web.admin.content.dataspace;

/*
 * Test strategy (Bug #78486)
 *
 * DataSpaceContentSettingsService.downloadFile computes a suffix from the download name and
 * guards it against header injection before echoing it in the "extension" header. A name with
 * no dot, a dot only at index 0 or a trailing dot has an empty suffix, which is not an
 * injection and must download as application/octet-stream without an "extension" header.
 * A non-empty suffix carrying a line break, and a name carrying CR, are still rejected.
 *
 * The service runs against a mocked DataSpace that serves the file bytes.
 */

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSpaceDownloadSuffixTest {
   @BeforeEach
   void setUp() throws Exception {
      dataSpace = mock(DataSpace.class);
      when(dataSpace.getInputStream(isNull(), anyString()))
         .thenAnswer(inv -> new ByteArrayInputStream(BYTES));
      service = new DataSpaceContentSettingsService(mock(CustomThemesManager.class), dataSpace);
   }

   @ParameterizedTest
   @ValueSource(strings = { "README", ".gitignore", "notes." })
   void aNameWithAnEmptySuffixDownloadsAsOctetStream(String name) throws Exception {
      MockHttpServletResponse response = download(DIR + "/" + name, name);

      assertEquals(200, response.getStatus());
      assertEquals("application/octet-stream", response.getContentType());
      assertNull(response.getHeader("extension"));
      assertEquals("attachment; filename*=utf-8'en-us'" + name,
                   response.getHeader("Content-disposition"));
      assertArrayEquals(BYTES, response.getContentAsByteArray());
   }

   @Test
   void anExtensionCarryingAnEncodedLineBreakIsStillRejected() {
      Exception ex = assertThrows(Exception.class, () -> download(DIR + "/a.x%0ab", "a.x%0ab"));

      assertEquals("extension is invalid: x%0ab", ex.getMessage());
   }

   @Test
   void aNameWithoutADotCarryingCrIsRejectedByTheNameCheck() {
      Exception ex = assertThrows(
         Exception.class, () -> download(DIR + "/README", "README\rSet-Cookie: x=1"));

      assertTrue(ex.getMessage().startsWith("name is invalid"), ex.getMessage());
   }

   @Test
   void anOrdinaryExtensionKeepsItsHeader() throws Exception {
      MockHttpServletResponse response = download(DIR + "/pack.zip", "pack.zip");

      assertEquals("zip", response.getHeader("extension"));
      assertEquals("application/zip", response.getContentType());
   }

   private MockHttpServletResponse download(String path, String name) throws Exception {
      MockHttpServletResponse response = new MockHttpServletResponse();
      service.downloadFile(path, name, response, new MockHttpServletRequest());
      return response;
   }

   private static final String DIR = "v78486";
   private static final byte[] BYTES = "plain".getBytes(StandardCharsets.UTF_8);
   private DataSpace dataSpace;
   private DataSpaceContentSettingsService service;
}
