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
package inetsoft.uql.asset.sync;

import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77808: renaming the viewsheet of a dashboard on import (the asset-file branch of
 * {@link DashboardAssetDependencyTransformer}) must write a dashboard file that the dashboard
 * reader parses again and gives back the new identifier and path exactly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardAssetDependencyTransformerControlCharTest {
   @ParameterizedTest
   @ValueSource(strings = {
      "Census\u001fA",   // control char: the serializer threw and left a 0-byte file
      "Census\u0000A",
      "a&b<c\"d'e",
      "cd]]>x",
      "é中",
      "My[1f]x",         // read back as My\u001fx when the path was written raw
      "Census A",
      "CensusA"
   })
   void renamedDashboardReadsBack(String name) throws Exception {
      String newPath = "Imported/" + name;
      String newId = viewsheetId(newPath);

      File file = transform(newId);
      ViewsheetEntry read = readBack(file);

      assertEquals(newId, read.getIdentifier());
      assertEquals(newPath, read.getPath());
   }

   @Test
   void identifierBytesUnchangedForOrdinaryNames() throws Exception {
      for(String name : new String[] { "Census", "F/Census A", "a&b<c\"d'e", "My[1f]x",
                                       "é中" })
      {
         String newId = viewsheetId("Imported/" + name);
         File file = transform(newId);
         Element entry = Tool.getChildNodeByTagName(
            Tool.getChildNodeByTagName(parse(file), "dashboard"), "entry");

         // the attribute value is what the transformer wrote before the fix
         assertEquals(Tool.byteEncode(newId), entry.getAttribute("identifier"), name);
      }
   }

   private File transform(String newId) throws Exception {
      File file = tempDir.resolve("dashboard" + (count++) + ".xml").toFile();
      writeDashboard(file);
      // sanity: the old file reads back before the transform
      assertEquals(OLD_ID, readBack(file).getIdentifier());

      DashboardAssetDependencyTransformer transformer = new DashboardAssetDependencyTransformer(
         new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.DASHBOARD, "Dash1",
                        new IdentityID("admin", "host-org")));
      transformer.setAssetFile(file);
      transformer.process(List.of(new RenameInfo(OLD_ID, newId, RenameInfo.VIEWSHEET)));
      return file;
   }

   // the shape DashboardAsset.writeContent produces
   private static void writeDashboard(File file) throws IOException {
      ViewsheetEntry vs = new ViewsheetEntry("Census");
      vs.setIdentifier(OLD_ID);
      VSDashboard dashboard = new VSDashboard();
      dashboard.setViewsheet(vs);

      try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(
         new FileOutputStream(file), StandardCharsets.UTF_8)))
      {
         writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
         writer.println("<dashboardAsset>");
         dashboard.writeXML(writer);
         writer.println("</dashboardAsset>");
      }
   }

   private static Element parse(File file) throws Exception {
      assertTrue(Files.size(file.toPath()) > 0, "dashboard file is empty");

      try(InputStream input = new FileInputStream(file)) {
         return Tool.parseXML(input).getDocumentElement();
      }
   }

   // the reader chain of DashboardAsset.parseContent
   private static ViewsheetEntry readBack(File file) throws Exception {
      VSDashboard dashboard = new VSDashboard();
      dashboard.parseXML(Tool.getChildNodeByTagName(parse(file), "dashboard"));
      return dashboard.getViewsheet();
   }

   private static String viewsheetId(String path) {
      return "1^128^__NULL__^" + path + "^host-org";
   }

   private static final String OLD_ID = viewsheetId("Census");
   private static int count;

   @TempDir
   Path tempDir;
}
