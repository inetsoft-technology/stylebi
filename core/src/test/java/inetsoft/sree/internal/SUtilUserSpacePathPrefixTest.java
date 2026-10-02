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
 *
 * Bug #77160: the org id and user name from the security provider and the path from the user were
 * used unchecked, so ".." segments stepped out of the user's folder, a user or org named after a
 * system folder (backup, heapdump, status, the S3 base) wrote into it, and the "already
 * prefixed" test had no segment boundary, so the user "b" wrote into "backup/" unprefixed.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

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
      storage = mock(ExternalStorageConfig.class, withSettings().lenient());
      when(storage.getType()).thenReturn("filesystem");
      InetsoftConfig config = mock(InetsoftConfig.class, withSettings().lenient());
      when(config.getExternalStorage()).thenReturn(storage);
      configStatic = mockStatic(InetsoftConfig.class, withSettings().strictness(Strictness.LENIENT));
      configStatic.when(InetsoftConfig::getInstance).thenReturn(config);
   }

   @AfterEach
   void tearDown() {
      configStatic.close();
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

   @ParameterizedTest
   @ValueSource(strings = { "../bob/r.pdf", "alice/../bob/r.pdf", "a/../../orgB/u/x",
                            "../../orgB/u/x", "..\\bob\\r.pdf", "sub/..", "reports/../x" })
   void parentSegmentInPath_rejected(String path) {
      assertRejected(ALICE, path);
      singleTenant();
      assertRejected(ALICE, path);
   }

   @Test
   void parentSegmentInLocationPath_rejected() {
      locations = "reports|Reports";
      assertRejected(ALICE, "reports/../../orgB/u/x");
      assertRejected(ALICE, "reports/sub/../../x");
   }

   @ParameterizedTest
   @ValueSource(strings = { "backup", "Heapdump", "STATUS" })
   void singleTenant_userNamedAfterSystemFolder_rejected(String name) {
      singleTenant();
      assertRejected(user(name, "host-org"), "f.pdf");
      // also when the path matches no save location, the user name is still the first segment
      locations = "reports|Reports";
      assertRejected(user(name, "host-org"), "tmp/f.pdf");
   }

   @Test
   void singleTenant_userNamedAfterSystemFolder_inSaveLocation_allowed() {
      // the key is location/user/..., the user name is not a top-level folder
      singleTenant();
      locations = "reports|Reports";
      assertEquals("reports/backup/x.pdf",
                   SUtil.addUserSpacePathPrefix(user("backup", "host-org"), "reports/x.pdf"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "heapdump", "Backup", "status" })
   void multiTenant_orgNamedAfterSystemFolder_rejected(String org) {
      assertRejected(user("alice", org), "r.pdf");
   }

   @Test
   void multiTenant_userNamedAfterSystemFolder_allowed() {
      assertEquals("orgA/backup/r.pdf",
                   SUtil.addUserSpacePathPrefix(user("backup", "orgA"), "r.pdf"));
   }

   @Test
   void s3BaseFirstSegment_rejectedAsFirstKeySegment() {
      useS3Base("data");
      assertRejected(user("alice", "data"), "r.pdf");
      assertRejected(user("alice", "DATA"), "r.pdf");
      assertEquals("dat/alice/r.pdf", SUtil.addUserSpacePathPrefix(user("alice", "dat"), "r.pdf"));
      singleTenant();
      assertRejected(user("data", "host-org"), "r.pdf");
      assertEquals("dat/r.pdf", SUtil.addUserSpacePathPrefix(user("dat", "host-org"), "r.pdf"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "..", ".", "a/b", "a\\b", "x/../backup" })
   void unsafeUserName_rejected(String name) {
      assertRejected(user(name, "orgA"), "r.pdf");
      singleTenant();
      assertRejected(user(name, "host-org"), "r.pdf");
      locations = "reports|Reports";
      assertRejected(user(name, "host-org"), "reports/r.pdf");
   }

   @ParameterizedTest
   @ValueSource(strings = { "..", ".", "a/b", "a\\b" })
   void unsafeOrgId_rejected(String org) {
      assertRejected(user("alice", org), "r.pdf");
      locations = "reports|Reports";
      assertRejected(user("alice", org), "reports/r.pdf");
   }

   @ParameterizedTest
   @CsvSource({
      // a user whose name is a string prefix of a folder is still prefixed
      "b,  backup/x.zip,  b/backup/x.zip",
      "al, alice/r.pdf,   al/alice/r.pdf",
      // a path already under the user's folder is kept
      "alice, alice/x.pdf,  alice/x.pdf",
      "alice, alice\\x.pdf, alice\\x.pdf",
   })
   void singleTenant_prefixOnSegmentBoundary(String name, String path, String expected) {
      singleTenant();
      assertEquals(expected, SUtil.addUserSpacePathPrefix(user(name, "host-org"), path));
   }

   @Test
   void multiTenant_prefixOnSegmentBoundary() {
      assertEquals("orgA/al/orgA/alice/r.pdf",
                   SUtil.addUserSpacePathPrefix(user("al", "orgA"), "orgA/alice/r.pdf"));
      assertEquals("orgA/alice/x.pdf",
                   SUtil.addUserSpacePathPrefix(ALICE, "orgA/alice/x.pdf"));
      assertEquals("host-org/alice/r.pdf",
                   SUtil.addUserSpacePathPrefix(user("alice", "host-org"), "r.pdf"));
   }

   @Test
   void systemWriter_nullPrincipal_unchanged() {
      assertEquals("backup/data-20260101000000.zip",
                   SUtil.addUserSpacePathPrefix(null, "backup/data-20260101000000.zip"));
   }

   private void assertRejected(Principal principal, String path) {
      MessageException thrown = assertThrows(
         MessageException.class, () -> SUtil.addUserSpacePathPrefix(principal, path),
         () -> principal.getName() + " " + path);
      assertEquals(Catalog.getCatalog().getString("schedule.saveToServer.userSpaceRejected"),
                   thrown.getMessage());
      assertFalse(thrown.getMessage().contains(path), "the message must not contain the path");
   }

   private void singleTenant() {
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(false);
   }

   private void useS3Base(String path) {
      S3Config s3 = mock(S3Config.class, withSettings().lenient());
      when(s3.getPath()).thenReturn(path);
      when(storage.getType()).thenReturn("s3");
      when(storage.getS3()).thenReturn(s3);
   }

   private static Principal user(String name, String org) {
      return () -> new IdentityID(name, org).convertToKey();
   }

   private static final Principal ALICE = () -> new IdentityID("alice", "orgA").convertToKey();
   private String locations;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<InetsoftConfig> configStatic;
   private ExternalStorageConfig storage;
}
