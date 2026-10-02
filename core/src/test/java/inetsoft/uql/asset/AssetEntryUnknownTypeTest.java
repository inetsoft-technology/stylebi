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
package inetsoft.uql.asset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.test.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77604: an asset entry XML with a numeric type id that matches no {@link AssetEntry.Type}
 * used to parse with a null type, so hashCode, toIdentifier and writeXML threw NPEs later
 * (saving a viewsheet with such an embedded entry, loading a viewsheet whose dependency had it,
 * loading a folder listing that held it). parseXML now maps the id to {@code Type.UNKNOWN},
 * the same rule the constructors and the identifier path already follow, and logs a warning.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetEntryUnknownTypeTest {
   @BeforeEach
   void attachAppender() {
      logger = (Logger) LoggerFactory.getLogger(AssetEntry.class);
      oldLevel = logger.getLevel();
      logger.setLevel(Level.WARN);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void detachAppender() {
      logger.detachAppender(appender);
      logger.setLevel(oldLevel);
   }

   @Test
   void unknownTypeIdParsesAsUnknownAndWarns() throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(
         parse("<assetEntry scope=\"1\" type=\"121\"><path><![CDATA[Examples/Census]]></path></assetEntry>"));

      assertEquals(AssetEntry.Type.UNKNOWN, entry.getType());
      assertEquals("Examples/Census", entry.getPath());
      // the members that used to NPE on a null type
      assertDoesNotThrow(entry::hashCode);
      assertDoesNotThrow(() -> entry.toIdentifier());
      assertFalse(entry.isFolder());
      String xml = write(entry);
      assertTrue(xml.contains("type=\"0\""), xml);

      List<ILoggingEvent> warnings = appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN)
         .toList();
      assertEquals(1, warnings.size(), "expected one warning, got " + appender.list);
      String message = warnings.get(0).getFormattedMessage();
      assertTrue(message.contains("121"), message);
      assertTrue(message.contains("Examples/Census"), message);
   }

   @Test
   void knownTypeIdsAreUnchangedAndDoNotWarn() throws Exception {
      for(AssetEntry.Type type : AssetEntry.Type.values()) {
         AssetEntry entry = AssetEntry.createAssetEntry(
            parse("<assetEntry scope=\"1\" type=\"" + type.id() + "\"><path><![CDATA[a/b]]></path></assetEntry>"));
         assertEquals(type, entry.getType(), "type id " + type.id());
      }

      assertTrue(appender.list.isEmpty(), "unexpected log events " + appender.list);
   }

   /**
    * Refute amendment 2: a viewsheet dependency with an unknown type id already failed at
    * parse time (the dependencies HashSet hashes the entry).
    */
   @Test
   void viewsheetWithUnknownDependencyTypeParsesWritesAndReparses() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addOuterDependency(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.WORKSHEET, "Examples/Dep", null));
      String original = writeViewsheet(vs);
      assertTrue(original.contains("type=\"2\""), original);
      String forged = original.replace("type=\"2\"", "type=\"121\"");

      Viewsheet parsed = parseViewsheet(forged);
      AssetEntry[] deps = parsed.getOuterDependencies(true);
      assertEquals(1, deps.length);
      assertEquals(AssetEntry.Type.UNKNOWN, deps[0].getType());
      assertEquals("Examples/Dep", deps[0].getPath());

      String written = writeViewsheet(parsed);
      Viewsheet reparsed = parseViewsheet(written);
      AssetEntry[] deps2 = reparsed.getOuterDependencies(true);
      assertEquals(1, deps2.length);
      assertEquals(deps[0], deps2[0]);
      assertEquals(written, writeViewsheet(reparsed));
   }

   @Test
   void assetFolderWithUnknownTypeEntryLoads() throws Exception {
      AssetFolder folder = new AssetFolder();
      folder.parseXML(parse(
         "<assetFolder>" +
         "<assetEntry scope=\"1\" type=\"121\"><path><![CDATA[f/unknown]]></path></assetEntry>" +
         "<assetEntry scope=\"1\" type=\"2\"><path><![CDATA[f/ws]]></path></assetEntry>" +
         "</assetFolder>"));

      assertEquals(2, folder.size());
      assertEquals(1, folder.getEntries(AssetEntry.Type.UNKNOWN).size());
      assertEquals(1, folder.getEntries(AssetEntry.Type.WORKSHEET).size());
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }

   private static String write(AssetEntry entry) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      entry.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static String writeViewsheet(Viewsheet vs) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      vs.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static Viewsheet parseViewsheet(String xml) throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.parseXML(parse(xml), false);
      return vs;
   }

   private Logger logger;
   private Level oldLevel;
   private ListAppender<ILoggingEvent> appender;
}
