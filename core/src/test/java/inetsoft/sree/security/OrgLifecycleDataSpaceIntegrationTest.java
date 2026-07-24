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
package inetsoft.sree.security;

/*
 * Scenarios 8b/8c/8d (matrix rows): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
 * section "三、其他机制" / "3.5 Data Space 文件". 8a (copyDataSpace() method-level A/B/C branches) is
 * already covered by AbstractEditableAuthenticationProviderStaticDepTest via mockStatic + reflection;
 * this file is the real-DataSpace integration companion for the three still-pending rows, reusing
 * that class's package-private StubProvider (same package) instead of duplicating it.
 *
 * All three methods under test here are called directly (via their real/reflected signature), not
 * through the full copyOrganizationInternal()/setOrganizationInfo() orchestration -- so, unlike
 * DashboardRegistryOrgLifecycleTest, no PortalThemesManager/DashboardRegistryManager bean overrides
 * are needed. Just BaseTestConfiguration (real DataSpace/BlobStorageManager) + @SreeHome.
 *
 * 8d note: AbstractEditableAuthenticationProvider.copyDataSpace() reaches DataSpace through the
 * static DataSpace.getDataSpace() factory (resolved via ConfigurationContext), while
 * IdentityService.updateOrgScopedDataSpace() uses its own constructor-injected DataSpace field --
 * ConfigurationContextInitializer wires the same Spring-managed DataSpace singleton into both paths,
 * which is what lets this test compare their real on-disk effects directly (same precedent already
 * relied on by DashboardRegistryOrgLifecycleTest's 4d/4e).
 *
 * 8c side discovery (not in the original matrix row, found while building fixtures for "no orphan
 * path shape"): DataSpace.getOrgScopedPaths()'s sixth OR-branch (DataSpace.java:408-409) is
 * effectively dead code for its stated purpose. Real per-user UserEnv files are named
 * "{name}_{orgId}.xml" under "sreeUserData/" (UserEnv.java:237, USER_DIR), with no "~;~"
 * (IdentityID.KEY_DELIMITER) in the file name. IdentityID.getIdentityIDFromKey() only parses an
 * orgID out of a key when that delimiter is present (IdentityID.java:96-105); without it, the method
 * falls to its else branch (IdentityID.java:106-112) and returns whatever org is on the *calling
 * thread's* principal/OrganizationManager context instead -- ignoring the path string entirely. The
 * branch's comparison against `oorg.getId() + ".xml"` can therefore never match a real sreeUserData
 * file's actual org segment; only an implausible orgId that is itself the literal string
 * "{targetOrgId}.xml" could ever satisfy it. Net effect: sreeUserData/ per-user files are never
 * included in getOrgScopedPaths() and are never cleaned up -- or relocated -- by org delete or
 * rename, regardless of which org's data they belong to. Filed as Issue #75763; the rename-path
 * consequence was manually reproduced end-to-end through the EM/Portal UI (a user's Preferences ->
 * History Bar toggle silently reverts to the system default after their organization's ID is
 * changed) before this coverage was added.
 *
 * These tests are deliberately NOT @Disabled even though they document a confirmed, unfixed bug:
 * per this suite's convention (see e.g. DataCycleManagerOrgLifecycleTest scenario 5e), a
 * characterization test for a known defect stays active so it fails loudly -- forcing a conscious
 * update -- the moment someone changes the underlying behavior, whether by fixing it or by
 * regressing it further. @Disabled in this codebase is reserved for flaky test infrastructure
 * (see DashboardRegistryOrgLifecycleTest scenario 4c), not for "this is a known bug we haven't
 * fixed yet."
 */

import inetsoft.mv.fs.internal.AbstractFileSystem;
import inetsoft.mv.fs.internal.DefaultBlockSystem;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecycleDataSpaceIntegrationTest {

   @Autowired
   private DataSpace dataSpace;

   // ── scenario 8b: default-org copy source also copies MV filesystem / block-system metadata ──

   @Test
   void copy_defaultOrgSource_replaceFalse_copiesFsAndBlockSystemFiles() throws Exception {
      String toOrgId = "dataspace_8b_to";
      String defaultOrgId = Organization.getDefaultOrganizationID();

      // AbstractFileSystem/DefaultBlockSystem.getOrgFileName() returns the raw path unchanged when
      // the org id equals the default org id -- so, for a default-org source, the "from" path is
      // just the raw fs.files/fs.bs.files value itself (no org segment inserted).
      String fsPath = AbstractFileSystem.getOrgPaths(null)[0];
      String bsPath = DefaultBlockSystem.getOrgPaths(null)[0];

      dataSpace.withOutputStream(null, fsPath, out -> out.write(bytes("fs-content")));
      dataSpace.withOutputStream(null, bsPath, out -> out.write(bytes("bs-content")));

      AbstractEditableAuthenticationProviderStaticDepTest.StubProvider provider =
         new AbstractEditableAuthenticationProviderStaticDepTest.StubProvider();
      provider.callCopyDataSpace(new Organization(defaultOrgId), new Organization(toOrgId), false);

      String newFsPath = AbstractFileSystem.getOrgFileName(fsPath, toOrgId);
      String newBsPath = DefaultBlockSystem.getOrgFileName(bsPath, toOrgId);

      assertTrue(dataSpace.exists(null, newFsPath),
                 "new org must have received the MV filesystem metadata file: " + newFsPath);
      assertTrue(dataSpace.exists(null, newBsPath),
                 "new org must have received the block-system metadata file: " + newBsPath);
      assertEquals("fs-content", readAll(newFsPath));
      assertEquals("bs-content", readAll(newBsPath));

      // The default org's own (unscoped) files must be untouched -- this is a copy, not a rename.
      assertTrue(dataSpace.exists(null, fsPath), "source fs file must be left in place (copy, not rename)");
      assertTrue(dataSpace.exists(null, bsPath), "source block-system file must be left in place (copy, not rename)");
   }

   // ── scenario 8c: removeOrgScopedDataSpaceElements() -- no orphan across every path shape getOrgScopedPaths() matches ──

   private record PathShape(String orgId, String dir, String file, String expectedPath) {}

   @Test
   void delete_removeOrgScopedDataSpaceElements_allKnownPathShapes_noOrphans() throws Exception {
      String base = "dataspace8c";
      List<PathShape> shapes = List.of(
         // p.equals("portal/" + orgId)
         new PathShape(base + "_portalBare", null, "portal/" + base + "_portalBare",
                       "portal/" + base + "_portalBare"),
         // p.startsWith("portal/" + orgId + "/")
         new PathShape(base + "_portalNested", "portal/" + base + "_portalNested", "theme.css",
                       "portal/" + base + "_portalNested/theme.css"),
         // p.startsWith(orgId + "__")
         new PathShape(base + "_kvShape", null, base + "_kvShape" + "__bucket",
                       base + "_kvShape" + "__bucket"),
         // p.equals(orgId)
         new PathShape(base + "_bare", null, base + "_bare", base + "_bare"),
         // p.startsWith(orgId + "/")
         new PathShape(base + "_bareNested", base + "_bareNested", "file.txt",
                       base + "_bareNested" + "/file.txt")
      );

      IdentityService identityService = newIdentityServiceWithRealDataSpace();

      for(PathShape shape : shapes) {
         dataSpace.withOutputStream(shape.dir(), shape.file(), out -> out.write(bytes("content")));
         assertTrue(dataSpace.exists(null, shape.expectedPath()),
                    "precondition: seeded path must exist: " + shape.expectedPath());

         identityService.removeOrgScopedDataSpaceElements(new Organization(shape.orgId()));

         assertFalse(dataSpace.exists(null, shape.expectedPath()),
                     "no orphan expected for path shape: " + shape.expectedPath());
      }
   }

   // Documents a gap found while building the fixture above -- see class-level comment for the
   // full mechanism. Pins current (likely unintended) behavior; not asserting it is correct.
   @Test
   void delete_removeOrgScopedDataSpaceElements_sreeUserDataFile_survivesAsOrphan() throws Exception {
      String orgId = "dataspace_8c_sreeuserdata";
      String userFile = "sreeUserData/alice_" + orgId + ".xml";

      dataSpace.withOutputStream(null, userFile, out -> out.write(bytes("user-env-content")));
      assertTrue(dataSpace.exists(null, userFile), "precondition: seeded per-user file must exist");

      IdentityService identityService = newIdentityServiceWithRealDataSpace();
      identityService.removeOrgScopedDataSpaceElements(new Organization(orgId));

      assertTrue(dataSpace.exists(null, userFile),
                 "documents a real gap: sreeUserData/ per-user UserEnv files are never matched by "
                 + "getOrgScopedPaths() (its org-id parse falls back to the current thread's org "
                 + "context, not the file name), so they survive org deletion as orphans");
   }

   // Issue #75763, rename-path counterpart of the delete-path test above. This is the code-level
   // mechanism behind the manually-reproduced UI symptom: after an org's ID is changed, a user's
   // Preferences -> History Bar setting (UserEnv.getProperty(principal, "historyBarEnable", null),
   // PreferencesDialogController.java:60) silently reverts to the system default, because the
   // per-user file was never relocated to the new org id -- both copyDataSpace(replace=true) (the
   // copyOrganizationInternal() entry point) and updateOrgScopedDataSpace() (the
   // setOrganizationInfo() entry point) drive their file moves off the same defective
   // getOrgScopedPaths(), so neither one moves it. Deliberately not @Disabled -- see class header.
   @Test
   void rename_sreeUserDataFile_neitherEntryPointRelocatesIt_currentBuggyBehaviorBaseline()
      throws Exception
   {
      // Path A: copyOrganizationInternal()'s copyDataSpace(replace=true).
      String fromA = "dataspace_8c_sreeuserdata_copyds_from";
      String toA = "dataspace_8c_sreeuserdata_copyds_to";
      String userFileA = "sreeUserData/alice_" + fromA + ".xml";
      String expectedRelocatedFileA = "sreeUserData/alice_" + toA + ".xml";

      dataSpace.withOutputStream(null, userFileA, out -> out.write(bytes("history-bar-setting")));

      AbstractEditableAuthenticationProviderStaticDepTest.StubProvider provider =
         new AbstractEditableAuthenticationProviderStaticDepTest.StubProvider();
      provider.callCopyDataSpace(new Organization(fromA), new Organization(toA), true);

      assertTrue(dataSpace.exists(null, userFileA),
                 "current buggy behavior: copyDataSpace(replace=true) never moves the sreeUserData "
                 + "file away from the old org id -- it is left behind, not relocated");
      assertFalse(dataSpace.exists(null, expectedRelocatedFileA),
                  "current buggy behavior: the new org id never receives the per-user file, which "
                  + "is exactly why a subsequent UserEnv.getProperty() lookup under the new org id "
                  + "misses and falls back to the property's default (the reproduced History Bar "
                  + "reset)");

      // Path B: setOrganizationInfo()'s own updateOrgScopedDataSpace() -- same root cause, same
      // outcome, via the independent implementation.
      String fromB = "dataspace_8c_sreeuserdata_updateds_from";
      String toB = "dataspace_8c_sreeuserdata_updateds_to";
      String userFileB = "sreeUserData/bob_" + fromB + ".xml";
      String expectedRelocatedFileB = "sreeUserData/bob_" + toB + ".xml";

      dataSpace.withOutputStream(null, userFileB, out -> out.write(bytes("history-bar-setting")));

      IdentityService identityService = newIdentityServiceWithRealDataSpace();
      invokeUpdateOrgScopedDataSpace(identityService, new Organization(fromB), new Organization(toB));

      assertTrue(dataSpace.exists(null, userFileB),
                 "current buggy behavior: updateOrgScopedDataSpace() never moves the sreeUserData "
                 + "file away from the old org id either -- same getOrgScopedPaths() gap, "
                 + "independent implementation");
      assertFalse(dataSpace.exists(null, expectedRelocatedFileB),
                  "current buggy behavior: the new org id never receives the per-user file here "
                  + "either");
   }

   // ── scenario 8d: setOrganizationInfo()'s independent updateOrgScopedDataSpace() must behave
   //    like copyOrganizationInternal()'s copyDataSpace(replace=true) -- no behavioral fork ──

   @Test
   void rename_updateOrgScopedDataSpaceEntry_consistentWithCopyDataSpaceEntry() throws Exception {
      // Path A: the copyOrganizationInternal(replace=true) entry point.
      String fromA = "dataspace_8d_copyds_from";
      String toA = "dataspace_8d_copyds_to";
      dataSpace.withOutputStream("portal/" + fromA, "theme.css", out -> out.write(bytes("css")));

      AbstractEditableAuthenticationProviderStaticDepTest.StubProvider provider =
         new AbstractEditableAuthenticationProviderStaticDepTest.StubProvider();
      provider.callCopyDataSpace(new Organization(fromA), new Organization(toA), true);

      // Path B: the setOrganizationInfo() entry point's own updateOrgScopedDataSpace().
      String fromB = "dataspace_8d_updateds_from";
      String toB = "dataspace_8d_updateds_to";
      dataSpace.withOutputStream("portal/" + fromB, "theme.css", out -> out.write(bytes("css")));

      IdentityService identityService = newIdentityServiceWithRealDataSpace();
      invokeUpdateOrgScopedDataSpace(identityService, new Organization(fromB), new Organization(toB));

      // Both entry points must produce the identical net effect: source path gone, destination
      // path present with content intact -- no divergence between the two independent implementations.
      assertFalse(dataSpace.exists("portal/" + fromA, "theme.css"),
                  "copyDataSpace(replace=true): source path must be gone after rename");
      assertTrue(dataSpace.exists("portal/" + toA, "theme.css"),
                 "copyDataSpace(replace=true): destination path must exist after rename");
      assertFalse(dataSpace.exists("portal/" + fromB, "theme.css"),
                  "updateOrgScopedDataSpace(): source path must be gone after rename");
      assertTrue(dataSpace.exists("portal/" + toB, "theme.css"),
                 "updateOrgScopedDataSpace(): destination path must exist after rename");

      assertEquals("css", readAll("portal/" + toA + "/theme.css"));
      assertEquals("css", readAll("portal/" + toB + "/theme.css"));
   }

   // ── fixture helpers ──

   private static byte[] bytes(String s) {
      return s.getBytes(StandardCharsets.UTF_8);
   }

   private String readAll(String path) throws Exception {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         assertNotNull(in, "expected a readable file at " + path);
         return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private IdentityService newIdentityServiceWithRealDataSpace() {
      // Positional constructor -- see IdentityService.java:76-103 for the full 29-parameter list.
      // Only position 24 (dataSpace) is real; everything else is null/Optional.empty() because
      // none of the three methods under test in this file touch any other dependency.
      return new IdentityService(
         null, null, null, null, null, null, null, null, null, null, null, null, null, null, // 1-14
         Optional.empty(),                                                                   // 15
         null, null, null, null, null, null, null, null,                                     // 16-23
         dataSpace,                                                                           // 24
         null, null, null, null,                                                              // 25-28
         Optional.empty());                                                                   // 29
   }

   private static void invokeUpdateOrgScopedDataSpace(IdentityService service, Organization oorg,
                                                      Organization norg) throws Exception
   {
      Method m = IdentityService.class.getDeclaredMethod(
         "updateOrgScopedDataSpace", Organization.class, Organization.class);
      m.setAccessible(true);
      m.invoke(service, oorg, norg);
   }
}
