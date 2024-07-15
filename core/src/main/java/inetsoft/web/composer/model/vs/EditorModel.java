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

import com.fasterxml.jackson.annotation.*;
import inetsoft.uql.viewsheet.ColumnOption;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   use = JsonTypeInfo.Id.NAME,
   property = "type"
)
@JsonSubTypes({
   @JsonSubTypes.Type(value = DateEditorModel.class, name = ColumnOption.DATE),
   @JsonSubTypes.Type(value = TextEditorModel.class, name = ColumnOption.TEXT),
   @JsonSubTypes.Type(value = IntegerEditorModel.class, name = ColumnOption.INTEGER),
   @JsonSubTypes.Type(value = FloatEditorModel.class, name = ColumnOption.FLOAT),
   @JsonSubTypes.Type(value = ComboBoxEditorModel.class, name = ColumnOption.COMBOBOX)
})
public class EditorModel {
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   private String type;
}
