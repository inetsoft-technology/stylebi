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

package inetsoft.web.admin.server;

import inetsoft.report.LibManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@RestController
public class DebugMonitoringController {

   @GetMapping("/em/monitoring/server/debug/key-value-storage-dump")
   public void getKVDump(HttpServletResponse response) throws Exception
   {
      writeKVDumpResponse(response);
   }

   public void writeKVDumpResponse(HttpServletResponse response)
   {
      try {
         String fileName = "kvDump.txt";
         String header = "attachment; filename=\"" + fileName + "\"";

         if(SUtil.isHttpHeadersValid(header)) {
            response.setHeader("Content-Disposition", StringUtils.normalizeSpace(header));
         }

         response.setContentType("text/plain; charset=\"UTF-8\"");

         try(PrintWriter writer = response.getWriter()) {
            Stream<String> idStream = KeyValueEngine.getInstance().idStream();

            idStream.forEach((identifier) -> {
               writer.println(identifier);
               Stream<KeyValuePair<Object>> pairs = KeyValueEngine.getInstance().stream(identifier);

               pairs.forEach((pair) -> {
                  writer.println("key='" + pair.getKey() + '\'' + ", value=" + pair.getValue());
               });

               writer.println();
            });
         }

      }
      catch(Exception e) {
         LOG.error("Failed to get key value storage dump", e);
      }
   }

   @GetMapping("/em/monitoring/server/debug/blob-path-dump")
   public void getBlobPathsDump(HttpServletResponse response) throws Exception
   {
      writeBlobPathsDumpResponse(response);
   }

   public void writeBlobPathsDumpResponse(HttpServletResponse response)
   {
      try {
         String fileName = "blobPaths.txt";
         String header = "attachment; filename=\"" + fileName + "\"";

         if(SUtil.isHttpHeadersValid(header)) {
            response.setHeader("Content-Disposition", StringUtils.normalizeSpace(header));
         }

         response.setContentType("text/plain; charset=\"UTF-8\"");
         Path tempFile = Files.createTempFile("blobPaths", ".dat");
         DataSpace.getDataSpace().listBlobs(tempFile.toString());
         String[] orgs = SecurityEngine.getSecurity().getOrganizations();
         orgs = orgs.length == 0 ? new String[]{Organization.getDefaultOrganizationID()} : orgs;


         for(String orgID : orgs) {
            IndexedStorage indexedStorage = IndexedStorage.getIndexedStorage();

            if(indexedStorage instanceof BlobIndexedStorage) {
               ((BlobIndexedStorage) indexedStorage).listBlobs(tempFile.toString(), orgID);
            }

            LibManager.getManager().listBlobs(tempFile.toString(), orgID);
            EmbeddedTableStorage.getInstance().listBlobs(tempFile.toString(), orgID);
         }

         try(OutputStream out = response.getOutputStream()) {
            Files.copy(tempFile, out);
         }

         Files.delete(tempFile);
      }
      catch(Exception e) {
         LOG.error("Failed to get key value storage dump", e);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(DebugMonitoringController.class);
}
