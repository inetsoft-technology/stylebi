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
package inetsoft.web.admin.content.repository.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Describes a bookmark name conflict between an imported viewsheet and the existing one.
 * Both the existing (PROD) and imported (JAR) bookmark carry creation and modification
 * timestamps so the admin can make an informed choice.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableBookmarkConflict.class)
@JsonDeserialize(as = ImmutableBookmarkConflict.class)
public interface BookmarkConflict {
   /** Path of the viewsheet asset (e.g. "Reports/Sales/Dashboard"). */
   String viewsheetPath();
   /** IdentityID key of the user that owns this bookmark (used as the resolution map key). */
   String user();
   /**
    * Display name for {@link #user()} — the plain username without org suffix.
    * Falls back to {@link #user()} when not set.
    */
   @Value.Default
   default String userLabel() { return user(); }
   /** Name of the conflicting bookmark. */
   String bookmarkName();
   /** VSBookmarkInfo.getCreateTime() for the existing (PROD) bookmark. */
   long existingCreated();
   /** VSBookmarkInfo.getLastModified() for the existing (PROD) bookmark. */
   long existingModified();
   /** Formatted display string for existingModified. */
   @Value.Default
   default String existingModifiedLabel() { return "—"; }
   /** VSBookmarkInfo.getCreateTime() for the imported bookmark. */
   long importedCreated();
   /** VSBookmarkInfo.getLastModified() for the imported bookmark. */
   long importedModified();
   /** Formatted display string for importedModified. */
   @Value.Default
   default String importedModifiedLabel() { return "—"; }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableBookmarkConflict.Builder {
   }
}
