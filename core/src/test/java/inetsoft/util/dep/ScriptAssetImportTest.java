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
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.*;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80501 item 2: the asset import is the only Script Library writer that takes the entry name
 * from untrusted input. An entry with an invalid name (#80303 rule) is refused only when its text
 * does not compile (it could never be loaded); an invalid name with a working text still imports as
 * before. Uses the real LibManager and script engine, the real ScriptAsset.parseContent, and for
 * the package case the real DeployManagerService.importAsset.
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

   private static final String DEAD_A_B = "function a-b() { return 1; }\n";

   @Test
   void validNamesStillImport() throws Exception {
      for(String name : new String[] { "impOkFn", "_impOk", "$impOk", "impOk1", "let" }) {
         importEntry(name, "function " + name + "() { return 1; }\n");
         assertNotNull(manager().getScript(name), name);
      }
   }

   @Test
   void invalidNameWithNonCompilingTextIsRefusedAndNamed() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> importEntry("a-b", DEAD_A_B));
      assertTrue(ex.getMessage().contains("\"a-b\""), ex.getMessage());
      assertTrue(ex.getMessage().contains("was not imported"), ex.getMessage());
      assertTrue(ex.getMessage().contains("does not compile"), ex.getMessage());
      assertNull(manager().getScript("a-b"));
   }

   @Test
   void reservedWordWithNonCompilingDeclarationIsRefused() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> importEntry("class", "function class() { return 1; }\n"));
      assertTrue(ex.getMessage().contains("\"class\""), ex.getMessage());
      assertNull(manager().getScript("class"));
   }

   @Test
   void leadingDigitAndSpaceNamesWithNonCompilingTextAreRefused() {
      for(String name : new String[] { "1abc", "a b" }) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> importEntry(name, "function " + name + "() {}\n"), name);
         assertTrue(ex.getMessage().contains("\"" + name + "\""), ex.getMessage());
         assertNull(manager().getScript(name), name);
      }
   }

   // a legacy entry with a non-identifier name whose text declares working functions: the
   // functions are callable today, so it is still imported
   @Test
   void invalidNameWithWorkingTextIsStillImported() throws Exception {
      String body = "function legacyFmt() { return 1; }\n";
      importEntry("legacy-utils", body);
      assertEquals(body, manager().getScript("legacy-utils"));
      importEntry("function", "function legacyFmt2() { return 2; }\n");
      assertNotNull(manager().getScript("function"));
   }

   @Test
   void packageThroughRealImportAssetRefusesOnlyTheDeadEntry() throws Exception {
      DeployManagerService service = new DeployManagerService(
         Mockito.mock(SecurityEngine.class, inv -> inv.getMethod().getName().startsWith("checkPermission") ? Boolean.TRUE : Mockito.RETURNS_DEFAULTS.answer(inv)),
         Mockito.mock(DependencyHandler.class),
         Mockito.mock(DataSourceRegistry.class), Mockito.mock(DashboardRegistryManager.class),
         LibManagerProvider.getInstance(), Mockito.mock(DashboardManager.class),
         Mockito.mock(XRepository.class), Mockito.mock(FileSystemService.class),
         Mockito.mock(DataSpace.class), Mockito.mock(EmbeddedTableStorage.class),
         Mockito.mock(RepletRegistryManager.class));
      XPrincipal principal = Mockito.mock(XPrincipal.class);
      Mockito.when(principal.getName()).thenReturn(
         new IdentityID("admin", inetsoft.sree.security.OrganizationManager.getInstance()
            .getCurrentOrgID()).convertToKey());
      Mockito.when(principal.getRoles()).thenReturn(new IdentityID[0]);
      String[][] pkg = {
         { "pkgOne", "function pkgOne() { return 1; }\n" },
         { "pkg-dead", "function pkg-dead() { return 2; }\n" },
         { "pkg-legacy", "function pkgLegacy() { return 3; }\n" },
         { "pkgTwo", "function pkgTwo() { return 4; }\n" },
      };
      List<String> failed = new ArrayList<>();

      for(String[] entry : pkg) {
         ScriptAsset asset = new ScriptAsset();
         asset.parseIdentifier(ScriptAsset.class.getName() + "^" + entry[0]);
         File file = tempDir.resolve("f" + entry[0]).toFile();
         Files.writeString(file.toPath(), entry[1], StandardCharsets.UTF_8);
         Map<String, String> names = new HashMap<>();
         names.put(file.getName(), ScriptAsset.SCRIPT + "_" + ScriptAsset.class.getName() + "^" +
            entry[0]);
         DeploymentInfo info = Mockito.mock(DeploymentInfo.class);
         Mockito.when(info.getNames()).thenReturn(names);
         XAssetConfig config = new XAssetConfig();
         config.setOverwriting(true);
         Method method = Arrays.stream(DeployManagerService.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("importAsset") && m.getParameterCount() == 14)
            .findFirst().orElseThrow();
         method.setAccessible(true);
         method.invoke(service, file, asset, new ArrayList<>(), failed, null, new ArrayList<>(),
                       new ArrayList<>(), true, null, info, false, config, null, principal);
      }

      assertNotNull(manager().getScript("pkgOne"), failed.toString());
      assertNotNull(manager().getScript("pkgTwo"));
      assertNotNull(manager().getScript("pkg-legacy"));
      assertNull(manager().getScript("pkg-dead"));
      assertEquals(1, failed.size(), failed.toString());
      assertTrue(failed.get(0).contains("\"pkg-dead\""), failed.get(0));
   }

   @TempDir
   Path tempDir;
}
