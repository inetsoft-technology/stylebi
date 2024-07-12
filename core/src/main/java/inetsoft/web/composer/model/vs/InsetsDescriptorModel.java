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
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link InsetsDescriptorModel}
 */
@Value.Immutable
@JsonTypeName("InsetsPropertyEditor")
@JsonSerialize(as = ImmutableInsetsDescriptorModel.class)
@JsonDeserialize(as = ImmutableInsetsDescriptorModel.class)
public abstract class InsetsDescriptorModel extends PresenterDescriptorModel {
   public abstract int top();
   public abstract int bottom();
   public abstract int left();
   public abstract int right();

   public static InsetsDescriptorModel.Builder builder() {
      return new InsetsDescriptorModel.Builder();
   }

   public static class Builder extends ImmutableInsetsDescriptorModel.Builder {
   }
}