/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.security.user;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

/**
 * Scheduled tasks affected by deleting the selected identities: tasks that will be deleted
 * because the identity owns them, and tasks whose "execute as" will be reset to the task owner.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableDeleteIdentitiesTaskImpactResponse.class)
@JsonDeserialize(as = ImmutableDeleteIdentitiesTaskImpactResponse.class)
public interface DeleteIdentitiesTaskImpactResponse {
   List<String> ownedTasks();

   List<String> executeAsTasks();

   static Builder builder() {
      return new Builder();
   }

   final class Builder extends ImmutableDeleteIdentitiesTaskImpactResponse.Builder {
   }
}
