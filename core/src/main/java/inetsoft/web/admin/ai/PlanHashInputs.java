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
package inetsoft.web.admin.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Shared building blocks for the canonical plan projections and plan hashes of the admin-ai areas
 * (Bug #79016).
 *
 * <p><b>Separator escaping.</b> A projection is a flat {@code field=value;field=value} string
 * that doubles as the plan-hash input. {@link #escape} backslash-prefixes the four separator
 * characters ({@code \ , ; =}) so a value can never pass for a field or list boundary: two specs
 * that differ only in where a separator sits project, and therefore hash, differently. It is the
 * identity function for a value holding none of them, so ordinary values project exactly as they
 * did before. A value that does contain one now <em>displays</em> with a backslash before it in a
 * plan's current/proposed value and in the audit record (for example a JDBC url {@code
 * jdbc:x;a=b} shows as {@code jdbc:x\;a\=b}, and a Windows path {@code C:\data} as
 * {@code C:\\data}); the data itself is untouched.
 *
 * <p><b>Hidden hash inputs.</b> A secret (a password) must never appear in a projection -- the
 * projection is shown to the reviewer, written to the audit log, and compared against the masked
 * read-back at apply time -- yet two plans that supply different secrets must not hash alike, or
 * the reviewer approves one value and apply writes another. The area's service therefore keeps
 * the projection unchanged and folds a digest of the effective secret into the plan hash only
 * (see {@link #digest} and {@link #addHidden}).
 */
public final class PlanHashInputs {
   private PlanHashInputs() {
   }

   /** Backslash-escapes the projection's separator characters ({@code \ , ; =}). Identity for a
    * value without any of them; {@code null} renders as the empty string. */
   public static String escape(String value) {
      if(value == null) {
         return "";
      }

      StringBuilder out = null;

      for(int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);

         if(c == '\\' || c == ',' || c == ';' || c == '=') {
            if(out == null) {
               out = new StringBuilder(value.length() + 8).append(value, 0, i);
            }

            out.append('\\');
         }

         if(out != null) {
            out.append(c);
         }
      }

      return out == null ? value : out.toString();
   }

   /**
    * The hidden plan-hash input for a secret: {@code "none"} for {@code null}, otherwise the hex
    * SHA-256 of {@code domain} plus the value. {@code "none"} is not 64 hex digits, so it can never
    * collide with a digest, and {@code null} differs from the string {@code "null"}.
    *
    * <p>Plain (unkeyed) SHA-256 on purpose, the same choice the data-source area made (Bug 78819):
    * preview and apply are separate requests that may land on different cluster nodes or straddle
    * a restart, so the input must be deterministic everywhere without shared key state. The digest
    * is never displayed, and the planHash it is folded into goes only back to the caller that sent
    * the secret in the same request (preview response, 409 body, the encrypted task token); it is
    * not logged or audited. If plans or planHashes are ever persisted, logged, or shown to a party
    * that does not already hold the secret, switch this to a keyed digest (HMAC with a
    * cluster-stable secret). A per-area {@code domain} keeps one area's digest from being replayed
    * as another's.
    */
   public static String digest(String domain, String secret) {
      return secret == null ? "none" : sha256Hex(domain + secret);
   }

   /** Records a hidden hash input under {@code key} (a change's identity within the plan). A key
    * that already holds an input keeps both, in call order. */
   public static void addHidden(Map<String, String> hidden, String key, String tag, String digest) {
      hidden.merge(key, tag + ":" + digest, (a, b) -> a + HIDDEN_SEP + b);
   }

   /** Appends the hidden inputs recorded for {@code key}, if any, to a canonical hash string. A
    * change with none appends nothing, so its hash is byte-identical to what it was before. */
   public static void appendHidden(StringBuilder canonical, char sep, Map<String, String> hidden,
                                   String key)
   {
      String value = hidden == null ? null : hidden.get(key);

      if(value != null) {
         canonical.append("hidden:").append(value).append(sep);
      }
   }

   public static String sha256Hex(String value) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for(byte b : digest) {
            hex.append(String.format("%02x", b));
         }

         return hex.toString();
      }
      catch(NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 is required to hash an admin change plan", e);
      }
   }

   private static final char HIDDEN_SEP = (char) 0x1e;
}
