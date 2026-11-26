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
package inetsoft.web.admin.ai.schedule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.internal.Util;

/**
 * The password that encrypts the zip attached to an email delivery, for one viewsheet action of a
 * task being created or updated. It lives beside the public schedule action DTO, which has no
 * field for it and is also the public REST body.
 *
 * <p>{@code actionIndex} names the action in the task's action list after the change. A non-empty
 * {@code password} sets it, exactly {@link Util#PLACEHOLDER_PASSWORD} keeps the stored one, and a
 * blank or {@code null} {@code password} without a {@code secretId} clears it. With
 * {@code useCredential} the password comes from the vault entry {@code secretId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailZipPasswordSpec {
   public int getActionIndex() { return actionIndex; }
   public void setActionIndex(int v) { this.actionIndex = v; }

   public String getPassword() { return password; }
   public void setPassword(String v) { this.password = v; }

   public Boolean getUseCredential() { return useCredential; }
   public void setUseCredential(Boolean v) { this.useCredential = v; }

   public String getSecretId() { return secretId; }
   public void setSecretId(String v) { this.secretId = v; }

   /** Whether this entry uses the secret id instead of a password. */
   public boolean usesCredential() {
      return Boolean.TRUE.equals(useCredential);
   }

   /** Whether the entry keeps the stored password: the masked placeholder. */
   public boolean keepsStored() {
      return Util.PLACEHOLDER_PASSWORD.equals(password);
   }

   /** Whether the entry sets a new password value. */
   public boolean setsPassword() {
      return !keepsStored() && password != null && !password.isEmpty();
   }

   /** Whether the entry clears the password, credential flag and secret id. */
   public boolean clears() {
      return !usesCredential() && !setsPassword() && !keepsStored() &&
         (secretId == null || secretId.isBlank());
   }

   /** Never prints the password value. */
   @Override
   public String toString() {
      String state = keepsStored() ? "kept" : setsPassword() ? "set" : "cleared";
      return "EmailZipPasswordSpec{actionIndex=" + actionIndex + ", password=" + state +
         ", useCredential=" + usesCredential() + ", secretId=" + secretId + "}";
   }

   private int actionIndex;
   private String password;
   private Boolean useCredential;
   private String secretId;
}
