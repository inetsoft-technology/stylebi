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
package inetsoft.web.admin.user;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableSessionModel.class)
@JsonDeserialize(as = ImmutableSessionModel.class)
public interface SessionModel extends Serializable {
   String address();
   String id();
   IdentityID user();
   long dateCreated();
   long dateAccessed();
   int activeViewsheets();
   List<IdentityID> roles();
   List<String> groups();
   String organization();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableSessionModel.Builder {
   }
}
