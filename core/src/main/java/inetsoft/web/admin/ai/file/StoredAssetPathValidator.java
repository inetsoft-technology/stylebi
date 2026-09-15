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

import java.util.regex.Pattern;

/**
 * Path-traversal defense for the stored-asset admin-chat area (Redmine #76603 Track 1,
 * 01-design.md section 3.3/6.5). {@code DataSpace.sanitizePathComponent} never rejects a {@code
 * ".."} segment (confirmed by direct read), so this validator is the ONLY layer standing between a
 * caller-supplied path/name and every {@code DataSpace} call in this area -- it must run before
 * {@code exists}/{@code list}/{@code delete}/{@code rename}/{@code getInputStream}/
 * {@code withOutputStream} ever see a raw caller value.
 */
public final class StoredAssetPathValidator {
   private StoredAssetPathValidator() {
   }

   /**
    * Validates and normalizes a DataSpace-relative path: {@code /}-or-{@code \}-delimited, no
    * leading/trailing separator, no empty/{@code .}/{@code ..} segment, no drive-letter or
    * {@code ~}-prefixed segment, no NUL byte.
    *
    * @return the normalized ({@code "/"}-separated, no leading/trailing slash) path -- {@code ""}
    *         for the DataSpace root (a bare {@code "/"} or {@code "."} is accepted as an alias for
    *         the root).
    * @throws IllegalArgumentException naming {@code field} on any violation.
    */
   public static String requirePath(String raw, String field) {
      if(raw == null) {
         throw new IllegalArgumentException(field + ": required");
      }

      requireNoNulByte(raw, field);
      String normalized = raw.replace('\\', '/').trim();

      if(normalized.equals("/") || normalized.equals(".") || normalized.isEmpty()) {
         return "";
      }

      if(normalized.startsWith("/")) {
         throw new IllegalArgumentException(
            field + ": must be relative to the DataSpace root, not start with \"/\" (got \"" +
            raw + "\")");
      }

      if(normalized.endsWith("/")) {
         throw new IllegalArgumentException(
            field + ": must not end with \"/\" (got \"" + raw + "\")");
      }

      for(String segment : normalized.split("/", -1)) {
         requireSafeSegment(field, raw, segment);
      }

      return normalized;
   }

   /**
    * Validates a single-segment sibling name (e.g. a {@code rename} verb's {@code newName}) -- it
    * must not itself relocate the entry to a different parent folder, so any {@code /} or {@code
    * \} is refused outright rather than treated as a nested path.
    */
   public static String requireSiblingName(String raw, String field) {
      if(raw == null) {
         throw new IllegalArgumentException(field + ": required");
      }

      requireNoNulByte(raw, field);

      if(raw.indexOf('/') >= 0 || raw.indexOf('\\') >= 0) {
         throw new IllegalArgumentException(
            field + ": must be a bare name, not a path (got \"" + raw + "\")");
      }

      requireSafeSegment(field, raw, raw);
      return raw;
   }

   private static void requireNoNulByte(String raw, String field) {
      if(raw.indexOf('\0') >= 0) {
         throw new IllegalArgumentException(field + ": must not contain a NUL byte");
      }
   }

   private static void requireSafeSegment(String field, String raw, String segment) {
      if(segment.isEmpty()) {
         throw new IllegalArgumentException(
            field + ": must not contain an empty path segment (got \"" + raw + "\")");
      }

      if(segment.equals(".") || segment.equals("..")) {
         throw new IllegalArgumentException(
            field + ": must not contain a \".\" or \"..\" segment (got \"" + raw + "\")");
      }

      if(DRIVE_LETTER.matcher(segment).matches()) {
         throw new IllegalArgumentException(
            field + ": must not contain a drive-letter segment (got \"" + raw + "\")");
      }

      if(segment.startsWith("~")) {
         throw new IllegalArgumentException(
            field + ": must not contain a segment starting with \"~\" (got \"" + raw + "\")");
      }
   }

   /**
    * The DataSpace root itself can never be a delete target -- there is no EM UI affordance to
    * delete the root tree node, so this tool does not support it either, unconditionally (even
    * with {@code acknowledgeIrreversibleDelete: true}), per 01-design.md section 6.5.
    */
   public static void requireNotRoot(String normalizedPath, String field) {
      if(normalizedPath.isEmpty()) {
         throw new IllegalArgumentException(
            field + ": the DataSpace root itself cannot be deleted through this tool");
      }
   }

   private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:$");
}
