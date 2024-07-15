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
package inetsoft.web.composer.vs.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;
/**
 * Class that encapsulates the parameters for moving and resizing a layout object.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonDeserialize(as = ImmutableMoveResizeLayoutObjectsEvent.class)
public abstract class MoveResizeLayoutObjectsEvent implements BaseVSLayoutEvent {
   public abstract String[] objectNames();

   public abstract int[] left();

   public abstract int[] top();

   public abstract int[] width();

   public abstract int[] height();
}
