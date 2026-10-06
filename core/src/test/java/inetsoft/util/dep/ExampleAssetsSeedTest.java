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

import inetsoft.test.*;
import inetsoft.uql.DrillPath;
import inetsoft.uql.XDrillInfo;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replays the viewsheets, worksheets and manifest of the example bundle that a new server
 * imports ({@code community-examples/examples.zip}) against {@link ImportedAssetProperties}.
 * The enterprise fuzzer starts from the same entries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ExampleAssetsSeedTest {
   @ParameterizedTest(name = "{0}")
   @MethodSource("entries")
   void exampleSurvivesSaveAndReload(String name, byte[] content) throws Exception {
      assertTrue(ImportedAssetProperties.check(content, true), name + " was not checked");
   }

   /**
    * Every auto-drill shipped in an example logical model must be one the product can follow:
    * a web link, or a viewsheet link to a viewsheet in the same bundle. Legacy report links
    * (link type 0) are emitted as cell hyperlinks but do nothing when clicked (Bug #77889).
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("logicalModels")
   void exampleDrillsAreFollowable(String name, byte[] content, Set<String> viewsheets)
      throws Exception
   {
      XLogicalModel model = new XLogicalModel("");
      model.parseXML(Tool.parseXML(new ByteArrayInputStream(content)).getDocumentElement(), false);

      for(int i = 0; i < model.getEntityCount(); i++) {
         XEntity entity = model.getEntityAt(i);

         for(int j = 0; j < entity.getAttributeCount(); j++) {
            XAttribute attribute = entity.getAttributeAt(j);
            XDrillInfo drills = attribute.getXMetaInfo().getXDrillInfo();

            for(int k = 0; drills != null && k < drills.getDrillPathCount(); k++) {
               DrillPath path = drills.getDrillPath(k);
               String where = name + " " + entity.getName() + "." + attribute.getName() +
                  " drill " + path.getName() + " -> " + path.getLink();

               if(path.getLinkType() == DrillPath.VIEWSHEET_LINK) {
                  AssetEntry target = AssetEntry.createAssetEntry(path.getLink());
                  assertNotNull(target, where + " is not a viewsheet identifier");
                  assertTrue(viewsheets.contains(target.getPath()),
                             where + " targets a viewsheet that is not in the bundle");
               }
               else {
                  assertEquals(DrillPath.WEB_LINK, path.getLinkType(),
                               where + " has an unsupported link type");
               }
            }
         }
      }
   }

   static Stream<Arguments> logicalModels() throws Exception {
      List<Arguments> all = entries().toList();
      Set<String> viewsheets = new HashSet<>();
      String prefix = "VIEWSHEET_" + ViewsheetAsset.class.getName() + "^";

      for(Arguments entry : all) {
         String name = (String) entry.get()[0];

         if(name.startsWith(prefix)) {
            AssetEntry vs = AssetEntry.createAssetEntry(
               name.substring(prefix.length()).replace("^_^", "/"));

            if(vs != null) {
               viewsheets.add(vs.getPath());
            }
         }
      }

      assertFalse(viewsheets.isEmpty(), "no example viewsheets found");
      return all.stream()
         .filter(a -> ((String) a.get()[0]).startsWith("XLOGICALMODEL_"))
         .map(a -> Arguments.of(a.get()[0], a.get()[1], viewsheets));
   }

   static Stream<Arguments> entries() throws Exception {
      // core/target/test-classes -> community/community-examples/examples.zip
      Path classes = Path.of(ExampleAssetsSeedTest.class.getResource("/").toURI());
      Path zip = classes.resolve("../../../community-examples/examples.zip").normalize();
      List<Arguments> entries = new ArrayList<>();

      try(ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
         for(ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
            String name = entry.getName();

            if(name.startsWith("VIEWSHEET_") || name.startsWith("WORKSHEET_") ||
               name.startsWith("XPARTITION_") || name.startsWith("XLOGICALMODEL_") ||
               name.startsWith("DEVICE_") || name.startsWith("DASHBOARD_") ||
               name.equals("JarFileInfo.xml"))
            {
               entries.add(Arguments.of(name, in.readAllBytes()));
            }
         }
      }

      entries.sort(Comparator.comparing(a -> (String) a.get()[0]));
      return entries.stream();
   }
}
