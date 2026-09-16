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
package inetsoft.web.admin.ai.shapes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A projection of one shape's content at a point in time (01-design.md section 2.3 item 4), used to
 * build {@link inetsoft.web.admin.ai.PlanChange#currentValue()}/{@code proposedValue()} and, via
 * those fields, the plan hash. {@code sha256} (not the base64 blob itself) is what keeps the
 * plan/hash payload small while still letting drift detection catch a same-size concurrent
 * overwrite between preview and apply.
 */
record ShapeProjection(String name, String path, String scope, long byteLength, String sha256) {
   String describe() {
      return "exists=true;size=" + byteLength + ";sha256=" + sha256;
   }

   /** {@code projection == null} means "no shape at this path" -- the absent-value canonical form. */
   static String describe(ShapeProjection projection) {
      return projection == null ? "exists=false" : projection.describe();
   }

   static String sha256Hex(byte[] content) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash shape content", e);
      }
   }
}
