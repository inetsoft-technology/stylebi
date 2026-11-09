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
package inetsoft.util.dep;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80501 item 2: the asset import is the only Script Library writer that takes the entry name
 * from untrusted input, so it must apply the same name rule as rename / save-as (#80303). Uses the
 * real LibManager and the real import entry point ScriptAsset.parseContent; DeployManagerService
 * collects an exception thrown there into its failed list and continues with the next asset, which
 * the mixed-package test reproduces.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScriptAssetImportTest {
   private static void importEntry(String name, String body) throws Exception {
      ScriptAsset asset = new ScriptAsset();
      asset.parseIdentifier(ScriptAsset.class.getName() + "^" + name);
      asset.parseContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                         null, true, true);
   }

   private static LibManager manager() {
      return LibManagerProvider.getInstance().getManager();
   }

   @Test
   void validNamesStillImport() throws Exception {
      for(String name : new String[] { "impOkFn", "_impOk", "$impOk", "impOk1", "let" }) {
         importEntry(name, "function " + name + "() { return 1; }\n");
         assertNotNull(manager().getScript(name), name);
      }
   }

   @Test
   void hyphenatedNameIsRefusedAndNamed() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> importEntry("a-b", "function a-b() { return 1; }\n"));
      assertTrue(ex.getMessage().contains("\"a-b\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("not imported"), ex.getMessage());
      assertNull(manager().getScript("a-b"));
   }

   @Test
   void reservedWordAndLeadingDigitAreRefused() {
      for(String name : new String[] { "class", "return", "1abc", "a b" }) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> importEntry(name, "x\n"), name);
         assertTrue(ex.getMessage().contains("\"" + name + "\""), ex.getMessage());
         assertNull(manager().getScript(name), name);
      }
   }

   @Test
   void mixedPackageImportsTheValidEntriesAndFailsTheInvalidByName() {
      String[][] pkg = {
         { "mixOne", "function mixOne() { return 1; }\n" },
         { "mix-bad", "function mix-bad() { return 2; }\n" },
         { "mixTwo", "function mixTwo() { return 3; }\n" },
         { "class", "function class() { return 4; }\n" },
      };
      List<String> failed = new ArrayList<>();

      // the same per-asset try/catch DeployManagerService.importAsset applies
      for(String[] entry : pkg) {
         try {
            importEntry(entry[0], entry[1]);
         }
         catch(Exception e) {
            failed.add(e.getMessage());
         }
      }

      assertNotNull(manager().getScript("mixOne"));
      assertNotNull(manager().getScript("mixTwo"));
      assertNull(manager().getScript("mix-bad"));
      assertNull(manager().getScript("class"));
      assertEquals(2, failed.size(), failed.toString());
      assertTrue(failed.get(0).contains("\"mix-bad\""), failed.get(0));
      assertTrue(failed.get(1).contains("\"class\""), failed.get(1));
   }
}
