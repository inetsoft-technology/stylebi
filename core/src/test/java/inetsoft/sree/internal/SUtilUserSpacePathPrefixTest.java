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
package inetsoft.sree.internal;

/*
 * Bug #77137: with server.save.locations set, addUserSpacePathPrefix matched a location without a
 * segment boundary, glued the user name to a relative folder, and gave a path matching no
 * location a key starting with "/". The filesystem storage resolves such a key outside of its
 * folder, and an absolute location never matched the root relative path it passes.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SUtilUserSpacePathPrefixTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isSecurityOn).thenReturn(true);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      sUtilStatic.when(() -> SUtil.isInternalUser(any())).thenReturn(false);
      sreeEnvStatic = mockStatic(SreeEnv.class);
      sreeEnvStatic.when(() -> SreeEnv.getProperty(anyString()))
         .thenAnswer(i -> "server.save.locations".equals(i.getArgument(0)) ? locations : null);
      locations = null;
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      sUtilStatic.close();
   }

   @ParameterizedTest
   @CsvSource({
      // a path in a location, the key is location/org/user/rest
      "reports|Reports,               reports/sub/f.pdf,      reports/orgA/alice/sub/f.pdf",
      "reports|Reports,               reports/f.pdf,          reports/orgA/alice/f.pdf",
      "archive/x|Archive,             archive/x/sub/f.pdf,    archive/x/orgA/alice/sub/f.pdf",
      // a location is matched on a segment boundary, as the portal does
      "rep|Rep,                       reports/sub/f.pdf,      orgA/alice/reports/sub/f.pdf",
      // a path matching no location gets the same key as without locations
      "reports|Reports,               tmp/x/f.pdf,            orgA/alice/tmp/x/f.pdf",
      "reports|Reports,               /tmp/x/f.pdf,           orgA/alice/tmp/x/f.pdf",
      "reports|Reports,               f.pdf,                  orgA/alice/f.pdf",
      // FTP backup action
      "reports|Reports,               backup/x.zip,           orgA/alice/backup/x.zip",
      // cloud storage passes the path as saved, an absolute location stays absolute
      "/var/reports|Var,              /var/reports/sub/f.pdf, /var/reports/orgA/alice/sub/f.pdf",
      // the filesystem storage on Linux passes the path relative to the root
      "/var/reports|Var,              var/reports/sub/f.pdf,  var/reports/orgA/alice/sub/f.pdf",
      "/mnt/nfs|NFS,                  mnt/nfs/r.xlsx,         mnt/nfs/orgA/alice/r.xlsx",
      // the first location (sorted by label) wins, as before
      "reports|A;reports/archive|B,   reports/archive/f.pdf,  reports/orgA/alice/archive/f.pdf",
      "reports|B;reports/archive|A,   reports/archive/f.pdf,  reports/archive/orgA/alice/f.pdf",
   })
   void withLocations_keyIsInsideItsFolder(String locations, String path, String expected) {
      this.locations = locations;
      assertEquals(expected, SUtil.addUserSpacePathPrefix(ALICE, path));
   }

   @ParameterizedTest
   @CsvSource({
      "reports|Reports, tmp/x/f.pdf",
      "reports|Reports, /tmp/x/f.pdf",
      "/var/reports|Var, var/reports/sub/f.pdf",
      "rep|Rep, reports/sub/f.pdf",
   })
   void relativeOrUnmatchedPath_keyIsNeverAbsolute(String locations, String path) {
      this.locations = locations;
      assertFalse(SUtil.addUserSpacePathPrefix(ALICE, path).startsWith("/"));
   }

   @Test
   void windowsFilesystemPath_keyUnchanged() {
      // the filesystem storage on Windows passes "\" separators, the key stays org/user/path, so
      // existing Windows files are not moved to another layout
      locations = "reports|Reports";
      assertEquals("orgA/alice/reports\\sub\\f.pdf",
                   SUtil.addUserSpacePathPrefix(ALICE, "reports\\sub\\f.pdf"));
      assertEquals("orgA/alice/\\tmp\\x\\f.pdf",
                   SUtil.addUserSpacePathPrefix(ALICE, "\\tmp\\x\\f.pdf"));
   }

   @ParameterizedTest
   @CsvSource({
      "/tmp/x/f.pdf, orgA/alice/tmp/x/f.pdf",
      "tmp/x/f.pdf,  orgA/alice/tmp/x/f.pdf",
   })
   void noLocations_keyUnchanged(String path, String expected) {
      assertEquals(expected, SUtil.addUserSpacePathPrefix(ALICE, path));
   }

   private static final Principal ALICE = () -> new IdentityID("alice", "orgA").convertToKey();
   private String locations;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
}
