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
package inetsoft.web.viewsheet.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

@Value.Immutable
@JsonSerialize(as = ImmutableVSRefreshEvent.class)
@JsonDeserialize(as = ImmutableVSRefreshEvent.class)
public abstract class VSRefreshEvent implements ViewsheetEvent {
   @Value.Default
   public boolean tableMetaData() {
      return false;
   }

   @Value.Default
   public boolean userRefresh() {
      return false;
   }

   @Value.Default
   public boolean initing() {
      return false;
   }

   @Value.Default
   public boolean checkShareFilter() {
      return false;
   }

   @Value.Default
   public boolean resizing() {
      return false;
   }

   @Value.Default
   public int width() {
      return 0;
   }

   @Value.Default
   public int height() {
      return 0;
   }

   @Value.Default
   public boolean autoRefresh() {
      return false;
   }

   public abstract @Nullable IdentityID bookmarkUser();

   public abstract @Nullable String bookmarkName();

   public abstract @Nullable Map<String, String[]> parameters();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableVSRefreshEvent.Builder {
   }
}