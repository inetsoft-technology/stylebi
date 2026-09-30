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
 * Bug #77307: renaming or cloning an organization rewrote its data space paths with
 * String.replace, so an org id occurring elsewhere in the path was replaced too, e.g. id "rt"
 * turned portal/rt/probe into port9al/rt9/probe, and id "ta" turned sreeUserData into
 * sreeUserDaorganization0. Only the org segment of a path may be rewritten.
 */

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class OrgScopedPathsTest {
   @ParameterizedTest
   @CsvSource({
      // portal/<id>
      "portal/rt, rt, rt9, portal/rt9",
      // portal/<id>/X, X kept even when it contains the id
      "portal/rt/probe, rt, rt9, portal/rt9/probe",
      "portal/rt/rtuser/dashboard-registry.xml, rt, rt9, portal/rt9/rtuser/dashboard-registry.xml",
      "portal/rt/theme/art.jar, rt, rt9, portal/rt9/theme/art.jar",
      "portal/ta/probe, ta, organization0, portal/organization0/probe",
      // <id>
      "rt, rt, rt9, rt9",
      // <id>/X
      "rt/repository.xml, rt, rt9, rt9/repository.xml",
      "rt/rt/fs.xml, rt, rt9, rt9/rt/fs.xml",
      // <id>__X
      "rt__rt, rt, rt9, rt9__rt",
      // sreeUserData/<user>_<id>.xml, only the org suffix
      "sreeUserData/rt9user_rt9.xml, rt9, b77307y, sreeUserData/rt9user_b77307y.xml",
      "sreeUserData/tauser_ta.xml, ta, organization0, sreeUserData/tauser_organization0.xml",
      // a legacy id with "_" is matched as a whole suffix, not after the last "_"
      "sreeUserData/alice_a_b.xml, a_b, c, sreeUserData/alice_c.xml",
   })
   void rewrite_onlyOrgSegment(String path, String oldId, String newId, String expected) {
      assertTrue(OrgScopedPaths.isOrgScopedPath(path, oldId), "precondition: org scoped path");
      assertEquals(expected, OrgScopedPaths.rewrite(path, oldId, newId));
   }

   @Test
   void rewrite_portalBeforeTopLevelFolder() {
      // a legacy id equal to a global folder matches two shapes, the portal shape wins
      assertEquals("portal/x/portal", OrgScopedPaths.rewrite("portal/portal/portal", "portal", "x"));
      // other tenants' portal files are claimed by the top-level shape of such an id
      assertEquals("x/rt/a", OrgScopedPaths.rewrite("portal/rt/a", "portal", "x"));
   }

   @Test
   void rewrite_userDataBeforeTopLevelFolder() {
      assertEquals("sreeUserData/u_x.xml",
                   OrgScopedPaths.rewrite("sreeUserData/u_sreeUserData.xml", "sreeUserData", "x"));
   }

   @ParameterizedTest
   @CsvSource({
      "portal/rt9/probe, rt",
      "portal/art/probe, rt",
      "rt9/repository.xml, rt",
      "art, rt",
      "sreeUserData/rt.xml, rt",
      "sreeUserData/u_art.xml, rt",
      "other/rt/x, rt",
      "portal/RT/x, rt",
   })
   void rewrite_otherPathsUnchanged(String path, String oldId) {
      assertFalse(OrgScopedPaths.isOrgScopedPath(path, oldId));
      assertEquals(path, OrgScopedPaths.rewrite(path, oldId, "new"));
   }

   @Test
   void rewrite_sameIdOrNull_unchanged() {
      assertEquals("portal/rt/x", OrgScopedPaths.rewrite("portal/rt/x", "rt", "rt"));
      assertNull(OrgScopedPaths.rewrite(null, "rt", "rt9"));
   }
}
