/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.file;

import inetsoft.sree.SreeEnv;
import inetsoft.util.DataSpace;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Read-side service for the stored-asset admin-chat area (01-design.md section 6.2). Deliberately
 * does NOT call {@code DataSpaceContentSettingsService.getTree}/{@code getDisplayName} -- those
 * bake in a synthetic-root-node wrapper and single-tenant cosmetic org-suffix hiding this area does
 * not want (01-design.md Flagged Decision 3); this reimplements a plain one-level listing directly
 * against {@link DataSpace#list}/{@link DataSpace#isDirectory}.
 */
@Service
public class AdminFileContentService {
   @Autowired
   public AdminFileContentService(DataSpace dataSpace) {
      this.dataSpace = dataSpace;
   }

   /** One level only (no recursion), per 01-design.md section 6.2. */
   public StoredAssetListResult list(String rawPath) {
      String path = StoredAssetPathValidator.requirePath(rawPath, "path");

      if(!path.isEmpty() && !dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path);
      }

      if(!path.isEmpty() && !dataSpace.isDirectory(path)) {
         throw new IllegalArgumentException(
            "path: \"" + path + "\" is a file, not a folder -- use get_stored_asset");
      }

      String[] names = dataSpace.list(path.isEmpty() ? null : path);
      List<StoredAssetEntry> entries = new ArrayList<>();

      for(String name : names == null ? new String[0] : names) {
         String childPath = path.isEmpty() ? name : path + "/" + name;
         entries.add(new StoredAssetEntry(name, childPath, dataSpace.isDirectory(childPath)));
      }

      entries.sort(Comparator.comparing((StoredAssetEntry e) -> !e.folder())
                      .thenComparing(StoredAssetEntry::name, String.CASE_INSENSITIVE_ORDER));
      return new StoredAssetListResult(path, Collections.unmodifiableList(entries));
   }

   public StoredAssetNode getNode(String rawPath) {
      String path = StoredAssetPathValidator.requirePath(rawPath, "path");

      if(!path.isEmpty() && !dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path);
      }

      boolean folder = path.isEmpty() || dataSpace.isDirectory(path);
      String name = path.isEmpty() ? "" : baseName(path);

      if(folder) {
         return new StoredAssetNode(path, name, true, null, null, null);
      }

      long size = dataSpace.getFileLength(null, path);
      String lastModified = formatTimestamp(dataSpace.getLastModified(null, path));
      return new StoredAssetNode(path, name, false, size, lastModified, probeEditableAsText(path));
   }

   public StoredAssetContent getContent(String rawPath, boolean preview) {
      String path = StoredAssetPathValidator.requirePath(rawPath, "path");

      if(path.isEmpty()) {
         throw new IllegalArgumentException(
            "path: the DataSpace root is a folder -- use list_stored_assets");
      }

      if(!dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path);
      }

      if(dataSpace.isDirectory(path)) {
         throw new IllegalArgumentException(
            "path: \"" + path + "\" is a folder -- use list_stored_assets or " +
            "download_stored_asset_zip");
      }

      if(!probeEditableAsText(path)) {
         throw new IllegalArgumentException(
            "path: \"" + path + "\" is not text-editable (binary, or not valid UTF-8) -- use " +
            "download_stored_asset instead");
      }

      long size = dataSpace.getFileLength(null, path);

      if(!preview && size > READ_CONTENT_CAP_BYTES) {
         throw new IllegalArgumentException(
            "path: \"" + path + "\" is " + size + " bytes, over the " + READ_CONTENT_CAP_BYTES +
            "-byte inline read cap -- use download_stored_asset instead, or pass preview: true " +
            "for a truncated excerpt");
      }

      return new StoredAssetContent(path, readText(path, preview), true);
   }

   /** Used by {@link AdminFileContentController#download} before delegating to {@code
    * DataSpaceContentSettingsService.downloadFile}. */
   void requireExistingFile(String path) {
      if(!dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path);
      }

      if(dataSpace.isDirectory(path)) {
         throw new IllegalArgumentException(
            "path: \"" + path + "\" is a folder -- use download_stored_asset_zip");
      }
   }

   /** Used by {@link AdminFileContentController#downloadFolder}. An empty ({@code ""}) path is the
    * DataSpace root, always a valid folder target. */
   void requireExistingFolder(String path) {
      if(path.isEmpty()) {
         return;
      }

      if(!dataSpace.exists(null, path)) {
         throw new StoredAssetNotFoundException(path);
      }

      if(!dataSpace.isDirectory(path)) {
         throw new IllegalArgumentException(
            "path: \"" + path + "\" is a file -- use download_stored_asset");
      }
   }

   private String readText(String path, boolean preview) {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         byte[] bytes = IOUtils.toByteArray(in);
         String full = new String(bytes, StandardCharsets.UTF_8);

         if(!preview || full.length() <= PREVIEW_CHAR_LIMIT) {
            return full;
         }

         String truncated = full.substring(0, PREVIEW_CHAR_LIMIT);
         int lastLine = truncated.lastIndexOf('\n');
         return lastLine >= 0 ? truncated.substring(0, lastLine) : truncated;
      }
      catch(IOException e) {
         throw new IllegalStateException("Failed to read \"" + path + "\": " + e.getMessage(), e);
      }
   }

   /**
    * Reimplements {@code DataSpaceFileSettingsController.probeContentType} (private on that class)
    * -- decodes the file as UTF-8, treating a decode failure as "not editable as text" (01-design.md
    * section 6.2). Not a security-sensitive duplication (01-design.md section 6.2's own framing) --
    * just a capability probe.
    */
   boolean probeEditableAsText(String path) {
      if(path.endsWith("db") || path.endsWith("dat")) {
         return false;
      }

      try {
         if(!dataSpace.exists(null, path) || dataSpace.isDirectory(path)) {
            return false;
         }

         try(InputStream in = dataSpace.getInputStream(null, path)) {
            Charset.availableCharsets().get("UTF-8").newDecoder()
               .decode(ByteBuffer.wrap(IOUtils.toByteArray(in)));
         }

         return true;
      }
      catch(CharacterCodingException e) {
         return false;
      }
      catch(IOException e) {
         return false;
      }
   }

   private static String formatTimestamp(long epochMillis) {
      SimpleDateFormat format = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));
      return format.format(new Date(epochMillis));
   }

   static String baseName(String path) {
      int idx = path.lastIndexOf('/');
      return idx < 0 ? path : path.substring(idx + 1);
   }

   static final int READ_CONTENT_CAP_BYTES = 2_000_000;
   private static final int PREVIEW_CHAR_LIMIT = 512;
   private final DataSpace dataSpace;
}
