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
package inetsoft.sree.portal;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonDeserialize(as = ImmutableFontFaceModel.class)
@JsonSerialize(as = ImmutableFontFaceModel.class)
public interface FontFaceModel {
   String fontName();
   String identifier();

   @Nullable String fontWeight();
   @Nullable String fontStyle();

   @JsonIgnore
   default String getFileNamePrefix() {
      if(identifier().equals(EMPTY_IDENTIFIER)) {
         return fontName();
      }

      return fontName() + "^" + identifier();
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableFontFaceModel.Builder {
      public Builder emptyFontFace(String fontName) {
         return fontName(fontName)
            .identifier(EMPTY_IDENTIFIER);
      }
   }

   String EMPTY_IDENTIFIER = "";
}
