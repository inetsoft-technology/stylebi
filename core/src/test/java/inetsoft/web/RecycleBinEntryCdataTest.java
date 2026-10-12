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
package inetsoft.web;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77891: the path, original path and name of a recycle bin entry were written raw into
 * CDATA, so an asset whose name holds {@code ]]>} made the exported entry unreadable.
 */
@Tag("core")
class RecycleBinEntryCdataTest {
   @Test
   void pathOriginalPathAndNameRoundTrip() throws Exception {
      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath("Recycle Bin/a]]>b");
      entry.setOriginalPath("folder/a]]>b");
      entry.setName("a]]>b");

      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      entry.writeXML(writer);
      writer.flush();

      RecycleBin.Entry back = new RecycleBin.Entry();
      back.parseXML(Tool.parseXML(new ByteArrayInputStream(
         buf.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement());

      assertEquals("Recycle Bin/a]]>b", back.getPath());
      assertEquals("folder/a]]>b", back.getOriginalPath());
      assertEquals("a]]>b", back.getName());
   }
}
