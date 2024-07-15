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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Data transfer object that represents the {@link PresenterDescriptorModel}
 */

@JsonTypeInfo(
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   use = JsonTypeInfo.Id.NAME,
   property = "editor",
   visible = true
)
@JsonSubTypes({
   @JsonSubTypes.Type(value = LineDescriptorModel.class, name = "LinePropertyEditor"),
   @JsonSubTypes.Type(value = IntDescriptorModel.class, name = "IntPropertyEditor"),
   @JsonSubTypes.Type(value = DoubleDescriptorModel.class, name = "DoublePropertyEditor"),
   @JsonSubTypes.Type(value = BooleanDescriptorModel.class, name = "AsCheckboxPropertyEditor"),
   @JsonSubTypes.Type(value = FontDescriptorModel.class, name = "FontPropertyEditor"),
   @JsonSubTypes.Type(value = ColorDescriptorModel.class, name = "ColorPropertyEditor"),
   @JsonSubTypes.Type(value = SizeDescriptorModel.class, name = "SizePropertyEditor"),
   @JsonSubTypes.Type(value = InsetsDescriptorModel.class, name = "InsetsPropertyEditor"),
   @JsonSubTypes.Type(value = ImageDescriptorModel.class, name = "ImagePropertyEditor"),
   @JsonSubTypes.Type(value = StringDescriptorModel.class, name = "AsTextPropertyEditor")
})
public abstract class PresenterDescriptorModel {
   public abstract String name();
   public abstract String displayName();
   public abstract String editor();
}