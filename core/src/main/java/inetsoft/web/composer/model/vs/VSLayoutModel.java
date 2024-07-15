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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
@JsonSerialize(as = ImmutableVSLayoutModel.class)
@JsonDeserialize(as = ImmutableVSLayoutModel.class)
public abstract class VSLayoutModel {
   @Nullable
   public abstract String name();

   @Value.Default
   public List<VSLayoutObjectModel> objects() {
      return new ArrayList<>();
   }

   @Value.Default
   public boolean printLayout() {
      return false;
   }

   @Nullable
   public abstract String unit();

   @Nullable
   public abstract Double width();

   @Nullable
   public abstract Double height();

   @Nullable
   public abstract Double marginTop();

   @Nullable
   public abstract Double marginLeft();

   @Nullable
   public abstract Double marginRight();

   @Nullable
   public abstract Double marginBottom();

   @Nullable
   public abstract Float headerFromEdge();

   @Nullable
   public abstract Float footerFromEdge();

   @Nullable
   public abstract Integer guideType();

   @Value.Default
   public List<VSLayoutObjectModel> headerObjects() {
      return new ArrayList<>();
   }

   @Value.Default
   public List<VSLayoutObjectModel> footerObjects() {
      return new ArrayList<>();
   }

   @Value.Default
   public boolean horizontal() {
      return false;
   }

   public abstract String runtimeID();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableVSLayoutModel.Builder {}
}
