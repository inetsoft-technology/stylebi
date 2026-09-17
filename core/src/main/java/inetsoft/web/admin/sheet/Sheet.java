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
package inetsoft.web.admin.sheet;

import inetsoft.web.admin.security.UserIdentityID;
import inetsoft.sree.security.IdentityID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code Sheet} provides the properties of a viewsheet or worksheet.
 */
@Validated
@Schema(description = "The properties of a viewsheet or worksheet.")
public class Sheet implements Serializable {
   /**
    * Creates a new instance of {@code Sheet}.
    */
   public Sheet() {
   }

   /**
    * Creates a new instance of {@code Sheet}.
    *
    * @param asset      the asset identifier.
    * @param path       the sheet path.
    * @param label      the display label.
    * @param global     {@code true} if global scope; {@code false} if user scope.
    * @param user       the owner.
    * @param identifier the runtime identifier.
    */
   public Sheet(String asset, String path, String label, boolean global, IdentityID user,
                String identifier)
   {
      this.asset = asset;
      this.path = path;
      this.label = label;
      this.global = global;
      this.user = user;
      this.identifier = identifier;
   }

   /**
    * Gets the asset identifier for the sheet.
    *
    * @return the asset identifier.
    */
   @NotNull
   @Schema(
      description = "The asset identifier for the sheet.",
      example = "1^128^__NULL__^Examples/Census^host-org")
   public String getAsset() {
      return asset;
   }

   /**
    * Sets the asset identifier for the sheet.
    *
    * @param asset the asset identifier.
    */
   public void setAsset(String asset) {
      this.asset = asset;
   }

   /**
    * Gets the path to the sheet.
    *
    * @return the sheet path.
    */
   @NotNull
   @Schema(description = "The path to the sheet.", example = "Examples/Census")
   public String getPath() {
      return path;
   }

   /**
    * Sets the path to the sheet.
    *
    * @param path the sheet path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Gets the display name for the sheet.
    *
    * @return the sheet label.
    */
   @NotNull
   @Schema(description = "The display name for the sheet.", example = "Census")
   public String getLabel() {
      return label;
   }

   /**
    * Sets the display name for the sheet.
    *
    * @param label the sheet label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Gets a flag that indicates if the sheet is global or owned by a user.
    *
    * @return {@code true} if global; {@code false} if owned by a user.
    */
   @NotNull
   @Schema(
      description = "A flag that indicates if the sheet is global (true) or owned by a user (false)",
      example = "true")
   public boolean isGlobal() {
      return global;
   }

   /**
    * Sets a flag that indicates if the sheet is global or owned by a user.
    *
    * @param global {@code true} if global; {@code false} if owned by a user.
    */
   public void setGlobal(boolean global) {
      this.global = global;
   }

   /**
    * Gets the user, if any, that owns the sheet.
    *
    * @return the owner.
    */
   @Schema(
      description = "The user, if any, that owns the sheet.",
      implementation = UserIdentityID.class,
      example = "{\"name\":\"user0\",\"orgID\":\"organization0\"}"
   )
   public IdentityID getUser() {
      return user;
   }

   /**
    * Sets the user, if any, that owns the sheet.
    *
    * @param user the owner.
    */
   public void setUser(IdentityID user) {
      this.user = user;
   }

   /**
    * Gets the runtime identifier of the opened sheet.
    *
    * @return the runtime identifier.
    */
   @Schema(
      description = "The runtime identifier of the opened sheet. This field is only used when listing open sheets.",
      example = "0"
   )
   public String getIdentifier() {
      return identifier;
   }

   /**
    * Sets the runtime identifier of the opened sheet.
    *
    * @param identifier the runtime identifier.
    */
   public void setIdentifier(String identifier) {
      this.identifier = identifier;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      Sheet sheet = (Sheet) o;
      return global == sheet.global &&
         Objects.equals(asset, sheet.asset) &&
         Objects.equals(path, sheet.path) &&
         Objects.equals(label, sheet.label) &&
         Objects.equals(user, sheet.user) &&
         Objects.equals(identifier, sheet.identifier);
   }

   @Override
   public int hashCode() {
      return Objects.hash(asset, path, label, global, user, identifier);
   }

   @Override
   public String toString() {
      return "Sheet{" +
         "asset='" + asset + '\'' +
         ", path='" + path + '\'' +
         ", label='" + label + '\'' +
         ", global=" + global +
         ", user='" + user + '\'' +
         ", identifier='" + identifier + '\'' +
         '}';
   }

   private String asset;
   private String path;
   private String label;
   private boolean global;
   private IdentityID user;
   private String identifier;
}
