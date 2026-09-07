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

import inetsoft.util.Tool;

/**
 * Issues and verifies the token that pins a plan's reviewed {@code task} narrative to the
 * {@code planHash} a caller must echo at apply. {@code AdminChangePlanService#hash} deliberately
 * excludes {@code task} so a caller paraphrasing it between preview and apply never trips a false
 * conflict - but that also means apply's own {@code task} request field is never required to
 * match what a reviewer actually read at preview. This token closes that gap without reopening
 * the one {@code hash} avoids: a caller is never required to resend a matching {@code task}
 * string, but whatever text is embedded in a valid token is what actually reaches the audit
 * record, not whatever text (if any) the apply request itself carries.
 *
 * <p>Stateless by design: the token embeds both the reviewed {@code task} and the
 * {@code planHash} it was issued for, encrypted with {@link Tool#encryptPassword}, which every
 * node in a cluster can decrypt (the same master key a cluster shares to keep encrypted
 * credentials portable between nodes). No cache, no TTL, no clustering gap.
 */
public final class TaskAuditToken {
   private TaskAuditToken() {
   }

   /** Issues a token binding {@code task} to {@code planHash}, for a preview response. */
   public static String issue(String planHash, String task) {
      return Tool.encryptPassword(planHash + SEP + task);
   }

   /**
    * Recovers the reviewed {@code task} embedded in {@code taskToken}, requiring it to have been
    * issued for exactly {@code planHash}.
    *
    * @throws TaskTokenException if {@code taskToken} is missing, undecryptable, malformed, or was
    *                           issued for a different {@code planHash}.
    */
   public static String verify(String taskToken, String planHash) {
      if(taskToken == null || taskToken.isBlank()) {
         throw new TaskTokenException(
            "taskToken: required - the token returned by preview for this plan");
      }

      String decoded;

      try {
         decoded = Tool.decryptPassword(taskToken);
      }
      catch(Exception e) {
         throw new TaskTokenException(
            "taskToken: does not match the current plan; re-review before applying");
      }

      int idx = decoded == null ? -1 : decoded.indexOf(SEP);

      if(idx < 0 || !planHash.equals(decoded.substring(0, idx))) {
         throw new TaskTokenException(
            "taskToken: does not match the current plan; re-review before applying");
      }

      return decoded.substring(idx + 1);
   }

   /** Thrown by {@link #verify} on a missing, undecryptable, malformed, or foreign token. */
   public static class TaskTokenException extends RuntimeException {
      public TaskTokenException(String message) {
         super(message);
      }
   }

   /**
    * Same unit-separator {@code AdminChangePlanService#hash} uses. {@code indexOf} finds the FIRST
    * separator, which is always the planHash boundary, so even if {@code task} contains a stray
    * separator, it cannot be exploited to forge a different split.
    */
   private static final char SEP = '\u001f';
}
