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
package inetsoft.web.viewsheet.model.dialog.schedule;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Data transfer object that represents the {@link ViewsheetActionModel} for the
 * schedule dialog
 */
@Value.Immutable
@JsonSerialize(as = ImmutableViewsheetActionModel.class)
@JsonDeserialize(as = ImmutableViewsheetActionModel.class)
@JsonTypeName(value = "viewsheet")
public abstract class ViewsheetActionModel extends ActionModel {
   @Nullable
   public abstract String viewsheet();

   @Nullable
   public abstract String bookmarkName();

   @Nullable
   public abstract IdentityID bookmarkUser();

   @Nullable
   public abstract Integer bookmarkType();

   @Nullable
   public abstract Boolean hasPrintLayout();

   @Value.Default
   public EmailInfoModel emailInfoModel() {
      return ImmutableEmailInfoModel.builder().build();
   }

   public static ViewsheetActionModel.Builder builder() {
      return new ViewsheetActionModel.Builder();
   }

   public static class Builder extends ImmutableViewsheetActionModel.Builder {
   }
}
