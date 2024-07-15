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
package inetsoft.web.admin.user;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.security.IdentityID;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableTopUser.class)
@JsonDeserialize(as = ImmutableTopUser.class)
public abstract class TopUser {
   public abstract IdentityID name();
   public abstract int activeReports();
   public abstract int executingReports();
   public abstract int activeViewsheets();
   public abstract int executingViewsheets();
   public abstract long age();

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder extends ImmutableTopUser.Builder {
   }
}
