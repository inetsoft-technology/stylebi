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
package inetsoft.web.viewsheet.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Event used to edit a bookmark
 */
@Value.Immutable
@Serial.Structural
@JsonSerialize(as = ImmutableVSEditBookmarkEvent.class)
@JsonDeserialize(as = ImmutableVSEditBookmarkEvent.class)
public interface VSEditBookmarkEvent extends ViewsheetEvent {
   @Nullable
   String instruction();
   @Nullable
   VSBookmarkInfoModel vsBookmarkInfoModel();
   @Nullable
   String oldName();
   @Nullable
   String bookmarkConfirmed();
   @Nullable
   String clientId();
   @Nullable
   Integer windowWidth();
   @Nullable
   Integer windowHeight();
   @Nullable
   Boolean mobile();
   @Nullable
   String userAgent();

   static VSEditBookmarkEvent from(VSEditBookmarkEvent event, String bookmarkConfirmed) {
      return ImmutableVSEditBookmarkEvent.copyOf(event)
                                         .withBookmarkConfirmed(bookmarkConfirmed);
   }
}
