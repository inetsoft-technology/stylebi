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
package inetsoft.sree;

import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.test.*;
import inetsoft.util.Tool;
import inetsoft.util.dep.ImportedAssetProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77603: a repository entry (e.g. the viewsheet entry of a dashboard) whose XML has no
 * {@code <path>} loaded with a null path, and the dashboard then could not be saved
 * ({@code writeContents} -> {@code getLabel} -> {@code isRoot} NPE). Such XML is only produced by
 * hand editing, so it is refused when it is parsed. Every entry the product writes, including a
 * root entry and an empty path, must keep loading.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryEntryMissingPathTest {
   // ---- the fuzzer-found dashboards (Redmine attachments 116337, 116338) ----

   /**
    * The seeds are not in {@code inetsoft/util/dep/asset-seeds}: {@code AssetSeedTest} calls
    * {@code ImportedAssetProperties.check(seed, true)}, which fails any seed that does not parse,
    * so it cannot express "must be refused". The refusal is asserted directly, and the fuzzer
    * mode of the same harness ({@code requireParse=false}) must accept the seed as a rejected
    * input instead of failing on the write.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("seeds")
   void attachmentIsRefusedOnImport(String name, byte[] seed) throws Exception {
      Exception ex = assertThrows(Exception.class, () -> parseDashboard(seed), name);
      assertTrue(ex.getMessage().startsWith("ViewsheetEntry "), ex.getMessage());
      assertTrue(ex.getMessage().endsWith(" is missing <path>"), ex.getMessage());
      assertTrue(ImportedAssetProperties.check(seed, false), name);
   }

   static Stream<Arguments> seeds() throws Exception {
      return SeedCorpus.load(RepositoryEntryMissingPathTest.class, "missing-path-seeds");
   }

   // parse a dashboard the way import (DashboardAsset.parseContent) does
   private static void parseDashboard(byte[] content) throws Exception {
      Element root = Tool.parseXML(new ByteArrayInputStream(content)).getDocumentElement();
      new VSDashboard().parseXML(Tool.getChildNodeByTagName(root, "dashboard"), false);
   }

   // ---- unit cases ----

   @Test
   void missingPathIsRefused() {
      String xml = "<entry class=\"inetsoft.sree.ViewsheetEntry\" type=\"64\" " +
         "identifier=\"1^128^__NULL__^Examples~_2f_~Census^host-org\"></entry>";

      for(boolean siteAdminImport : new boolean[] { false, true }) {
         Exception ex = assertThrows(Exception.class,
            () -> new ViewsheetEntry().parseXML(element(xml), siteAdminImport));
         assertEquals("ViewsheetEntry \"1^128^__NULL__^Examples/Census^host-org\" is missing " +
                      "<path>", ex.getMessage());
      }

      // an entry without an identifier attribute is named by its class only
      Exception ex = assertThrows(Exception.class,
         () -> new DefaultFolderEntry().parseXML(element("<entry type=\"1\"></entry>")));
      assertEquals("DefaultFolderEntry is missing <path>", ex.getMessage());
   }

   @Test
   void normalPathLoads() throws Exception {
      ViewsheetEntry entry = new ViewsheetEntry("Examples/Census");
      entry.setIdentifier("1^128^__NULL__^Examples/Census^host-org");

      ViewsheetEntry read = new ViewsheetEntry();
      read.parseXML(element(write(entry)));

      assertEquals("Examples/Census", read.getPath());
      assertEquals("Census", read.getName());
      assertEquals(write(entry), write(read));
   }

   @Test
   void rootEntryLoads() throws Exception {
      DefaultFolderEntry root = new DefaultFolderEntry("/");
      String xml = write(root);
      // "/" is byte-encoded
      assertTrue(xml.contains("<path><![CDATA[~_2f_~]]></path>"), xml);

      DefaultFolderEntry read = new DefaultFolderEntry();
      read.parseXML(element(xml));

      assertTrue(read.isRoot());
      assertEquals(xml, write(read));
   }

   /**
    * writeContents always writes {@code <path>}, so an empty path is written as an empty CDATA
    * section, which Tool.getValue reads as null. Only a missing element is refused; an empty one
    * loads as the "" that was written.
    */
   @Test
   void emptyPathStillLoads() throws Exception {
      ViewsheetEntry entry = new ViewsheetEntry("");
      entry.setIdentifier("1^128^__NULL__^Examples/Census^host-org");
      String xml = write(entry);
      assertTrue(xml.contains("<path><![CDATA[]]></path>"), xml);

      ViewsheetEntry read = new ViewsheetEntry();
      read.parseXML(element(xml));
      assertEquals("", read.getPath());
      assertEquals(xml, write(read));

      read = new ViewsheetEntry();
      read.parseXML(element(xml.replace("<path><![CDATA[]]></path>", "<path></path>")));
      assertEquals("", read.getPath());
   }

   private static String write(RepositoryEntry entry) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         entry.writeXML(writer);
      }

      return buffer.toString();
   }

   private static Element element(String xml) throws Exception {
      return Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
