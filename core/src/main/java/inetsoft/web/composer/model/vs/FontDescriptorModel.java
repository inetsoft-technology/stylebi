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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.adhoc.model.FontInfo;
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link FontDescriptorModel}
 */
@Value.Immutable
@JsonTypeName("FontPropertyEditor")
@JsonSerialize(as = ImmutableFontDescriptorModel.class)
@JsonDeserialize(as = ImmutableFontDescriptorModel.class)
public abstract class FontDescriptorModel extends PresenterDescriptorModel {
   public abstract FontInfo value();

   public static FontDescriptorModel.Builder builder() {
      return new FontDescriptorModel.Builder();
   }

   public static class Builder extends ImmutableFontDescriptorModel.Builder {
   }
}