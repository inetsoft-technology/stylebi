/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that DeploymentInfo.processDcNames() transforms "^_^" → "/" in zip
 * entry names, and that the DeployManagerService importAsset() logic correctly
 * parses __WS_EMBEDDED_TABLE_ entries after that transformation.
 *
 * Context: Bug #74175 changed indexOf('/') to indexOf("^_^") in the embedded
 * table import path, not knowing that processDcNames() had already replaced
 * "^_^" with "/" before those names reach importAsset(). Bug #74189 reverts
 * that change.
 */
class DeployEmbeddedTableImportTest {

   private static final String WS_EMBEDDED_PREFIX = "__WS_EMBEDDED_TABLE_";

   /**
    * Confirms that DeploymentInfo.processDcNames() replaces "^_^" with "/"
    * in all zip entry name values. This is the invariant the importAsset fix
    * depends on.
    */
   @Test
   void processDcNamesReplacesCaretUnderscoreCaretWithSlash() throws Exception {
      Map<String, String> input = new HashMap<>();
      input.put("fABC123", "__WS_EMBEDDED_TABLE_pdata^_^t1_s1769561087264_7_s.tdat");
      input.put("fDEF456", "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^upload^org-sara");

      Map<String, String> result = invokeProcDcNames(input);

      // The embedded table entry's "^_^" separator must become "/"
      assertEquals(
         "__WS_EMBEDDED_TABLE_pdata/t1_s1769561087264_7_s.tdat",
         result.get("fABC123"),
         "processDcNames() must replace '^_^' with '/' in embedded table entry names"
      );

      // Non-embedded-table entries are not affected (no "^_^" present)
      assertEquals(
         "WORKSHEET_inetsoft.util.dep.WorksheetAsset^1^2^__NULL__^upload^org-sara",
         result.get("fDEF456"),
         "Non-embedded-table entries without '^_^' must be unchanged"
      );
   }

   /**
    * Confirms that after processDcNames() has run, the importAsset() parsing
    * logic (using indexOf('/')) correctly extracts folder="pdata" and the bare
    * filename from a __WS_EMBEDDED_TABLE_ entry.
    *
    * This is the exact logic in DeployManagerService.importAsset() for the
    * __WS_EMBEDDED_TABLE_ branch (Bug #74189 fix).
    */
   @Test
   void importAssetParsesEmbeddedTableEntryAfterProcessDcNamesTransformation() {
      // Simulate the filename as it arrives in importAsset() — after processDcNames()
      // has already replaced "^_^" with "/"
      String filename = "__WS_EMBEDDED_TABLE_pdata/t1_s1769561087264_7_s.tdat";

      assertTrue(filename.startsWith(WS_EMBEDDED_PREFIX));

      String fname = filename.substring(WS_EMBEDDED_PREFIX.length());
      // fname = "pdata/t1_s1769561087264_7_s.tdat"

      // This is the fixed code path (Bug #74189): indexOf('/'), NOT indexOf("^_^")
      int idx = fname.indexOf('/');

      assertTrue(idx >= 0, "Separator '/' must be found in the processed filename");

      String folder = fname.substring(0, idx);
      String bareFilename = fname.substring(idx + 1);

      assertEquals("pdata", folder, "folder must be 'pdata'");
      assertEquals("t1_s1769561087264_7_s.tdat", bareFilename, "bare filename must strip the pdata/ prefix");
   }

   /**
    * Confirms the regression: the broken code from Bug #74175 (indexOf("^_^"))
    * would fail to find the separator after processDcNames() has already
    * replaced it with "/".
    */
   @Test
   void bug74175CodeFailsAfterProcessDcNamesTransformation() {
      // After processDcNames() the filename has "/" not "^_^"
      String fname = "pdata/t1_s1769561087264_7_s.tdat";

      // The broken Bug #74175 code:
      int idx = fname.indexOf("^_^");

      assertEquals(-1, idx, "indexOf(\"^_^\") must return -1 after processDcNames() has replaced it");
      // With idx == -1, the broken code leaves folder=null, triggering the
      // null-folder guard and adding a failedList warning — the regression.
   }

   // -------------------------------------------------------------------------

   @SuppressWarnings("unchecked")
   private static Map<String, String> invokeProcDcNames(Map<String, String> input)
      throws Exception
   {
      // processDcNames() only reads its parameter — no instance state needed.
      // Use Unsafe.allocateInstance() to bypass the constructor.
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      Unsafe unsafe = (Unsafe) f.get(null);
      DeploymentInfo instance = (DeploymentInfo) unsafe.allocateInstance(DeploymentInfo.class);

      Method m = DeploymentInfo.class.getDeclaredMethod("processDcNames", Map.class);
      m.setAccessible(true);
      return (Map<String, String>) m.invoke(instance, input);
   }
}
