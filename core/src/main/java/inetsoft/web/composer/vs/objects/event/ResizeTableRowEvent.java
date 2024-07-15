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
package inetsoft.web.composer.vs.objects.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import inetsoft.web.viewsheet.event.table.BaseTableEvent;
import org.immutables.value.Value;

/**
 * Data transfer object that represents the {@link ResizeTableRowEvent} for resizing
 * table rows in the composer
 *
 * @since 12.3
 */
@Value.Immutable
@JsonDeserialize(as = ImmutableResizeTableRowEvent.class)
public interface ResizeTableRowEvent extends BaseTableEvent {
   int row();
   int rowHeight();
   int rowSpan();
   boolean header();
   int start();
   int rowCount();
}
