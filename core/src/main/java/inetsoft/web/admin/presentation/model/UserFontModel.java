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
package inetsoft.web.admin.presentation.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.portal.FontFaceModel;
import inetsoft.web.admin.model.FileData;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableUserFontModel.class)
@JsonDeserialize(as = ImmutableUserFontModel.class)
public interface UserFontModel {
   String name();
   String identifier();
   FileData ttf();
   @Nullable() FileData eot();
   @Nullable() FileData svg();
   @Nullable() FileData woff();

   @Nullable() String fontWeight();
   @Nullable() String fontStyle();

   @JsonIgnore
   default String getFileNamePrefix() {
      return FontFaceModel.builder()
         .fontName(name())
         .identifier(identifier())
         .build()
         .getFileNamePrefix();
   }

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableUserFontModel.Builder {
   }
}
