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
package inetsoft.web.composer.ws.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.composer.ws.assembly.WSAssemblyModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableRefreshWorksheetCommand.class)
public abstract class RefreshWorksheetCommand implements ViewsheetCommand {
   @Value.Default
   public List<WSAssemblyModel> assemblies() {
      return new ArrayList<>();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableRefreshWorksheetCommand.Builder {
   }
}