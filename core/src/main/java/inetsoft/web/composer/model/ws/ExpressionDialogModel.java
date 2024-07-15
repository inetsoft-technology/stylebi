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
package inetsoft.web.composer.model.ws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.composer.model.TreeNodeModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableExpressionDialogModel.class)
@JsonDeserialize(as = ImmutableExpressionDialogModel.class)
public interface ExpressionDialogModel {
   String tableName();

   @Value.Default
   default String oldName() {
      return "";
   }

   @Nullable String newName();

   String dataType();

   @Value.Default
   default String formulaType() {
      return "SQL";
   }

   String expression();

   @Nullable TreeNodeModel columnTree();

   @Nullable TreeNodeModel variableTree();

   @Nullable ObjectNode scriptDefinitions();

   @Value.Default
   default boolean sqlMergeable() {
      return true;
   }

   @JsonIgnore
   default boolean isSQL() {
      return "SQL".equals(formulaType());
   }

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableExpressionDialogModel.Builder {
      public final Builder setSQL(boolean isSQL) {
         super.formulaType(isSQL ? "SQL" : "Script");
         return this;
      }
   }
}
