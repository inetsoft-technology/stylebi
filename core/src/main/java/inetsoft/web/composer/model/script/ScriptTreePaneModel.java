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
package inetsoft.web.composer.model.script;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.model.TreeNodeModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Class that encapsulates the tree models for the script editor pane.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableScriptTreePaneModel.class)
@JsonDeserialize(as = ImmutableScriptTreePaneModel.class)
public abstract class ScriptTreePaneModel {
   /**
    * Model for the function tree in the editor.
    */
   @Nullable
   public abstract TreeNodeModel functionTree();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableScriptTreePaneModel.Builder {

   }
}
