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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link ImageDescriptorModel}
 */
@Value.Immutable
@JsonTypeName("ImagePropertyEditor")
@JsonSerialize(as = ImmutableImageDescriptorModel.class)
@JsonDeserialize(as = ImmutableImageDescriptorModel.class)
public abstract class ImageDescriptorModel extends PresenterDescriptorModel {
   public abstract ImagePreviewPaneModel value();

   public static ImageDescriptorModel.Builder builder() {
      return new ImageDescriptorModel.Builder();
   }

   public static class Builder extends ImmutableImageDescriptorModel.Builder {
   }
}