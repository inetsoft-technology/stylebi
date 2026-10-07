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
package inetsoft.uql.serverfile;

import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #64331: the file or folder of a Text/Excel Directory query must stay in the root folder of
 * its data source. A path that leaves it, set by the query editor or stored in a worksheet, used
 * to be resolved to any file on the server.
 */
class ServerFileQueryPathTest {
   @BeforeEach
   void setUp() throws IOException {
      root = Files.createDirectories(temp.resolve("data"));
      Files.writeString(root.resolve("sales.csv"), "a,b\n1,2\n");
      outside = Files.writeString(temp.resolve("secret.csv"), "x\n");

      ServerFileDataSource ds = new ServerFileDataSource();
      ds.setName("files");
      ds.setFile(root.toFile());
      query = new ServerFileQuery();
      query.setDataSource(ds);
   }

   @Test
   void fileUnderTheRootFolderIsKept() {
      File file = root.resolve("sales.csv").toFile();
      query.setFileFolder(file);

      assertEquals(file.getAbsoluteFile().toPath().normalize(),
                   query.getFileFolder().getAbsoluteFile().toPath().normalize());
   }

   @Test
   void fileOutsideTheRootFolderIsRefused() {
      File inside = root.resolve("sales.csv").toFile();
      query.setFileFolder(inside);
      query.setFileFolder(outside.toFile());

      // the refused path is not stored, the query keeps its file
      assertEquals(inside.getAbsoluteFile().toPath().normalize(),
                   query.getFileFolder().getAbsoluteFile().toPath().normalize());
   }

   @Test
   void storedPathThatLeavesTheRootFolderIsIgnored() throws Exception {
      query.parseContents(queryXml("../secret.csv"));

      assertNull(query.getFileFolder());
      assertFalse(query.isText());
      assertFalse(query.isExcel());
   }

   @Test
   void storedAbsolutePathOutsideTheRootFolderIsIgnored() throws Exception {
      query.parseContents(queryXml(outside.toFile().getAbsolutePath()));

      assertNull(query.getFileFolder());
   }

   @Test
   void storedRelativePathIsResolvedInTheRootFolder() throws Exception {
      query.parseContents(queryXml("sales.csv"));

      assertEquals(root.resolve("sales.csv").toFile(), query.getFileFolder());
   }

   private static org.w3c.dom.Element queryXml(String fileFolder) throws Exception {
      String xml = "<query><fileFolder><![CDATA[" + fileFolder + "]]></fileFolder>" +
         "<headerColumnCount><![CDATA[0]]></headerColumnCount></query>";
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }

   @TempDir
   Path temp;
   private Path root;
   private Path outside;
   private ServerFileQuery query;
}
