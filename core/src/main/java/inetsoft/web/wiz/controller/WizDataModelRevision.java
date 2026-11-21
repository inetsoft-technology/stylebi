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
package inetsoft.web.wiz.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * The revision of a data model definition: the lowercase hex SHA-256 of a canonical JSON of the
 * definition (map keys sorted recursively, lists in order, null members dropped, the top-level
 * {@code revision} key ignored, numbers and strings written one way). A caller that sends the
 * revision of the definition it read as {@code baseRevision} has its write refused when the
 * stored model no longer has that revision, so an edit planned from a stale read is never applied
 * over another writer's change. All three kinds use this one helper.
 */
public final class WizDataModelRevision {
   private WizDataModelRevision() {
   }

   /** The revision of the definition (the {@code definition} map of a get result). */
   public static String of(Map<String, Object> definition) {
      StringBuilder json = new StringBuilder();
      write(json, definition, true);

      try {
         byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(json.toString().getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder();

         for(byte b : hash) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(java.security.NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   private static void write(StringBuilder out, Object value, boolean top) {
      if(value == null) {
         out.append("null");
      }
      else if(value instanceof Map<?, ?> map) {
         TreeMap<String, Object> sorted = new TreeMap<>();

         for(Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());

            if(entry.getValue() != null && !(top && "revision".equals(key))) {
               sorted.put(key, entry.getValue());
            }
         }

         out.append('{');
         boolean first = true;

         for(Map.Entry<String, Object> entry : sorted.entrySet()) {
            if(!first) {
               out.append(',');
            }

            first = false;
            string(out, entry.getKey());
            out.append(':');
            write(out, entry.getValue(), false);
         }

         out.append('}');
      }
      else if(value instanceof Iterable<?> list) {
         out.append('[');
         boolean first = true;

         for(Object item : list) {
            if(!first) {
               out.append(',');
            }

            first = false;
            write(out, item, false);
         }

         out.append(']');
      }
      else if(value instanceof Number number) {
         out.append(new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
      }
      else if(value instanceof Boolean) {
         out.append(value);
      }
      else {
         string(out, value.toString());
      }
   }

   private static void string(StringBuilder out, String text) {
      out.append('"');

      for(int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);

         if(c == '"') {
            out.append("\\\"");
         }
         else if(c == '\\') {
            out.append("\\\\");
         }
         else if(c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
         }
         else {
            out.append(c);
         }
      }

      out.append('"');
   }
}
