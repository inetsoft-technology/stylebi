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
package inetsoft.util;

/*
 * Bug #81508: renaming a user rewrote a viewsheet that other viewsheets depend on once as itself
 * and once more for each dependent, all on different threads. The blob of a document written by
 * several threads at the same time can be lost, so the viewsheet then opened as "Sheet not found".
 * Every viewsheet must be rewritten by exactly one task.
 */

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependenciesInfo;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.migrate.MigrateViewsheetTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BlobIndexedStorageViewsheetRenameOnceTest {
   @Autowired
   private IndexedStorage indexedStorage;

   @AfterEach
   void tearDown() throws Exception {
      for(String key : seededKeys) {
         indexedStorage.remove(key);
      }

      dependencies.clear();

      seededKeys.clear();
   }

   @Test
   void userRename_rewritesAViewsheetOtherViewsheetsDependOnOnce() throws Exception {
      String embedded = seedViewsheet("Examples/Hurricane");
      String left = seedViewsheet("Examples/HurricaneL");
      String right = seedViewsheet("Examples/HurricaneR");
      seedDependents(left, embedded);
      seedDependents(right, embedded);
      // the dependency index also lists the viewsheets that embed the viewsheet itself
      seedDependents(embedded, left, right);

      Map<String, Integer> counts = new ConcurrentHashMap<>();

      DependencyStorageService service = Mockito.mock(DependencyStorageService.class);
      Mockito.when(service.get(Mockito.anyString()))
         .thenAnswer(inv -> dependencies.get(inv.<String>getArgument(0)));

      try(MockedStatic<DependencyStorageService> ignoredService =
             Mockito.mockStatic(DependencyStorageService.class);
          MockedConstruction<MigrateViewsheetTask> ignored =
             Mockito.mockConstruction(MigrateViewsheetTask.class, (task, context) -> {
                AssetEntry entry = (AssetEntry) context.arguments().get(0);
                counts.merge(entry.toIdentifier(), 1, Integer::sum);
             }))
      {
         ignoredService.when(DependencyStorageService::getInstance).thenReturn(service);
         indexedStorage.migrateStorageData(new IdentityID("alice", ORG),
                                           new IdentityID("alice2", ORG), Identity.USER);
      }

      assertEquals(1, counts.get(embedded), "the embedded viewsheet must be rewritten once");
      assertEquals(1, counts.get(left));
      assertEquals(1, counts.get(right));
   }

   private String seedViewsheet(String path) throws Exception {
      String key = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path,
                                  null, ORG).toIdentifier();
      Document document = Tool.parseXML(new StringReader("<assetSheet/>"));
      indexedStorage.putDocument(key, document, Viewsheet.class.getName(), ORG);
      seededKeys.add(key);
      return key;
   }

   /** Registers the dependents of the sheet in the dependency index. */
   private void seedDependents(String key, String... dependents) throws Exception {
      List<AssetObject> list = new ArrayList<>();

      for(String dependent : dependents) {
         list.add(AssetEntry.createAssetEntry(dependent));
      }

      DependenciesInfo info = new DependenciesInfo();
      info.setDependencies(list);
      dependencies.put(key, info);
   }

   private static final String ORG = "host-org";
   private final Map<String, DependenciesInfo> dependencies = new HashMap<>();
   private final List<String> seededKeys = new ArrayList<>();
}
