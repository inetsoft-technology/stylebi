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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78047: a viewsheet whose combo box has the default value "R&D" is saved to and
 * reopened from the indexed storage, which parses the stored XML with StAX.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ComboBoxDefaultValueStorageTest {
   @Autowired
   private IndexedStorage indexedStorage;

   @AfterEach
   void tearDown() {
      indexedStorage.remove(KEY);
   }

   @Test
   void viewsheetWithAmpersandDefaultValueCanBeReopenedFromStorage() throws Exception {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "ComboBox1");
      ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo();
      ListData data = new ListData();
      data.setValues(new Object[] { "R&D", "Sales" });
      data.setLabels(new String[] { "R&D", "Sales" });
      info.setListData(data);
      info.setDefaultValue("R&D");
      vs.addAssembly(combo);

      indexedStorage.putXMLSerializable(KEY, vs);
      Viewsheet reopened = (Viewsheet) indexedStorage.getXMLSerializable(KEY, null);

      assertNotNull(reopened);
      ComboBoxVSAssembly reloaded = (ComboBoxVSAssembly) reopened.getAssembly("ComboBox1");
      assertNotNull(reloaded);
      ComboBoxVSAssemblyInfo reloadedInfo = (ComboBoxVSAssemblyInfo) reloaded.getVSAssemblyInfo();
      assertEquals("R&D", reloadedInfo.getDefaultValue());
      assertArrayEquals(new Object[] { "R&D", "Sales" }, reloadedInfo.getListData().getValues());
   }

   private static final String ORG = "host-org";
   private static final String KEY =
      new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "bug78047",
                     (IdentityID) null, ORG).toIdentifier(true);
}
