/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link VSBookmarkInfoModel}
 */
@Value.Immutable
@JsonSerialize(as = ImmutableVSBookmarkInfoModel.class)
@JsonDeserialize(as = ImmutableVSBookmarkInfoModel.class)
public abstract class VSBookmarkInfoModel {
   @Value.Default
   public boolean readOnly() {
      return true;
   }

   @Nullable
   public abstract String name();

   @Value.Default
   public int type() {
      return VSBookmarkInfo.PRIVATE;
   }

   @Nullable
   public abstract IdentityID owner();

   @Nullable
   public abstract String label();

   @Nullable
   public abstract Boolean defaultBookmark();

   @Nullable
   public abstract Boolean currentBookmark();

   @Nullable
   @Value.Default
   public String tooltip() {
      return "";
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableVSBookmarkInfoModel.Builder {
   }
}
