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
package inetsoft.web.viewsheet.command;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import org.immutables.value.Value;

import java.util.*;

/**
 * Command that instructs the client to prompt the user for the viewsheet parameters.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonSerialize(as = ImmutableCollectParametersCommand.class)
@JsonDeserialize(builder = CollectParametersCommand.Builder.class)
public abstract class CollectParametersCommand implements ViewsheetCommand {
   public abstract Optional<Boolean> disableParameterSheet();
   public abstract Optional<Boolean> isOpenSheet();

   @Value.Default
   public List<VariableAssemblyModelInfo> variables() {
      return new ArrayList<>();
   }

   @Value.Default
   public List<String> disabledVariables() {
      return new ArrayList<>();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableCollectParametersCommand.Builder {
   }
}
