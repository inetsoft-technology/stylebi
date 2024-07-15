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
package inetsoft.web.viewsheet.model.dialog;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link MessageDialogModel} for showing
 * the message dialog on the front end.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonSerialize(as = ImmutableMessageDialogModel.class)
@JsonDeserialize(as = ImmutableMessageDialogModel.class)
public abstract class MessageDialogModel {
   public abstract MessageCommand.Type type();
   public abstract boolean success();
   public abstract String message();

   public static MessageDialogModel.Builder builder() {
      return new MessageDialogModel.Builder();
   }

   public static class Builder extends ImmutableMessageDialogModel.Builder {
   }
}
