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
package inetsoft.web.composer.ws.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonDeserialize(builder = OpenWorksheetEvent.Builder.class)
public abstract class OpenWorksheetEvent {
   public abstract String id();

   public abstract boolean openAutoSavedFile();

   public abstract boolean gettingStartedWs();

   public abstract boolean createQuery();

   /**
    * The runtime identifier of the viewsheet this worksheet is being opened from
    * (e.g. clicking the base worksheet link in the composer's bottom status bar),
    * so the new worksheet's sandbox can be linked back to it. Null when the
    * worksheet is opened with no originating viewsheet (e.g. from the portal or
    * repository tree).
    */
   @Nullable
   public abstract String vsId();

   public static Builder builder() {
      return new Builder();
   }

   public static class Builder extends ImmutableOpenWorksheetEvent.Builder {
   }
}
