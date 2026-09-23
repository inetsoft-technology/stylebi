/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.asset;

import inetsoft.test.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetEntryTests {
   @Test
   void idsShouldBeUnique() {
      Set<Integer> ids = new HashSet<>();

      for(AssetEntry.Type type : AssetEntry.Type.values()) {
         assertFalse(ids.contains(type.id()));
         ids.add(type.id());
      }
   }

   /**
    * Regression test for Bug #76924: an identifier missing its trailing "^orgID" segment
    * (e.g. one built by the client for a same-org viewer link) does not fail to parse or leave
    * the org unset - it silently defaults to the caller-supplied org, exactly as it does for a
    * genuinely same-org identifier. This is intentional/load-bearing for the many callers that
    * construct a short, org-less identifier expecting "caller's own org" - but it also means a
    * malformed cross-org identifier (one that should have carried a different org) is
    * indistinguishable from a legitimate same-org one at this layer. The actual fix for bug
    * #76924 closes the client-side race that produced the malformed identifier in the first
    * place (ShareService.getViewsheetLink()); this test only pins down the parser's existing,
    * still-in-place defaulting contract so a future change to it is made deliberately.
    */
   @Test
   void missingOrgSegmentDefaultsToCallerOrgInsteadOfFailing() {
      String malformedIdentifier = "1^128^__NULL__^Folder/VS";
      AssetEntry entry = AssetEntry.createAssetEntry(malformedIdentifier, "tenant-A");
      assertEquals("tenant-A", entry.getOrgID());
      assertEquals("Folder/VS", entry.getPath());

      String wellFormedIdentifier = "1^128^__NULL__^Folder/VS^host-org";
      AssetEntry entry2 = AssetEntry.createAssetEntry(wellFormedIdentifier, "tenant-A");
      assertEquals("host-org", entry2.getOrgID());
      assertEquals("Folder/VS", entry2.getPath());
   }
}
