/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.VSObjectModel;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableVSLayoutObjectModel.class)
@JsonDeserialize(as = ImmutableVSLayoutObjectModel.class)
public abstract class VSLayoutObjectModel {
   @Nullable
   public abstract Boolean editable();

   public abstract String name();

   public abstract int left();

   public abstract int top();

   public abstract int width();

   public abstract int height();

   public abstract int tableLayout();

   @Nullable
   public abstract VSObjectModel objectModel();

   public abstract List<VSObjectModel> childModels();

   public abstract boolean supportTableLayout();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableVSLayoutObjectModel.Builder {
   }
}
