/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.security;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableCustomSSOAttributesModel.class)
@JsonDeserialize(as = ImmutableCustomSSOAttributesModel.class)
public interface CustomSSOAttributesModel {
   @Value.Default
   default boolean useJavaClass() {
      return false;
   }

   @Value.Default
   default boolean useInlineGroovy() {
      return false;
   }

   @Nullable String javaClassName();
   @Nullable String inlineGroovyClass();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableCustomSSOAttributesModel.Builder {
   }
}
