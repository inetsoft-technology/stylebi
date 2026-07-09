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
package inetsoft.web.wiz.pairing;

/**
 * Thrown when a wiz agent API operation cannot be completed.
 *
 * <p>The {@link Kind} classifies the failure so that callers (and the exception handler)
 * can map it to an appropriate HTTP status code and {@code errorCode} field in the response.
 * The plugin distinguishes, for example, a session-expired response (→ re-join) from a
 * bad-argument response (→ fix the request).</p>
 */
public class PairingException extends Exception {

   /**
    * Semantic classification of the failure.
    *
    * <ul>
    *   <li>{@link #INVALID_ARGUMENT} — malformed or missing request field → HTTP 400</li>
    *   <li>{@link #SESSION_EXPIRED}  — session or runtime not found / expired → HTTP 404</li>
    *   <li>{@link #USER_MISMATCH}    — pairing code belongs to a different user → HTTP 403</li>
    *   <li>{@link #FEATURE_DISABLED} — feature flag off → HTTP 403</li>
    *   <li>{@link #RATE_LIMITED}     — too many failed join attempts → HTTP 429</li>
    *   <li>{@link #INTERNAL}         — unexpected server error → HTTP 500</li>
    * </ul>
    */
   public enum Kind {
      /** Malformed or missing request field. Maps to HTTP 400. */
      INVALID_ARGUMENT,
      /** Session token or runtime not found or expired. Maps to HTTP 404. */
      SESSION_EXPIRED,
      /** Pairing code does not belong to the requesting user. Maps to HTTP 403. */
      USER_MISMATCH,
      /** Feature is administratively disabled. Maps to HTTP 403. */
      FEATURE_DISABLED,
      /** Too many failed join attempts from the same caller in a short window. Maps to HTTP 429. */
      RATE_LIMITED,
      /** Unexpected internal error. Maps to HTTP 500. */
      INTERNAL
   }

   public PairingException(String message) {
      super(message);
      this.kind = Kind.INVALID_ARGUMENT;
   }

   public PairingException(String message, Throwable cause) {
      super(message, cause);
      this.kind = Kind.INVALID_ARGUMENT;
   }

   public PairingException(Kind kind, String message) {
      super(message);
      this.kind = kind;
   }

   public PairingException(Kind kind, String message, Throwable cause) {
      super(message, cause);
      this.kind = kind;
   }

   public Kind getKind() {
      return kind;
   }

   private final Kind kind;
}
