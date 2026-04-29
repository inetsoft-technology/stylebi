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
package inetsoft.web.portal.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutablePortalSchedulerHealthModel.class)
@JsonDeserialize(as = ImmutablePortalSchedulerHealthModel.class)
public abstract class PortalSchedulerHealthModel {
   public abstract boolean available();
   public abstract boolean healthy();
   public abstract boolean started();
   public abstract boolean shutdown();
   public abstract boolean standby();
   public abstract long lastCheck();
   public abstract long nextCheck();
   public abstract int executingCount();
   public abstract int threadCount();
   public abstract String statusLabel();
   @Nullable
   public abstract String detailMessage();

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder extends ImmutablePortalSchedulerHealthModel.Builder {
   }
}
