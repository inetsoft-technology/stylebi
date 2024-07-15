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
package inetsoft.web.composer.vs.objects.command;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link ForceEditModeCommand}
 */
@Value.Immutable
@JsonSerialize(as = ImmutableForceEditModeCommand.class)
@JsonDeserialize(as = ImmutableForceEditModeCommand.class)
public abstract class ForceEditModeCommand implements ViewsheetCommand {
   public abstract boolean select();
   public abstract boolean editMode();

   public static ForceEditModeCommand.Builder builder() {
      return new ForceEditModeCommand.Builder();
   }

   public static class Builder extends ImmutableForceEditModeCommand.Builder {
   }
}
