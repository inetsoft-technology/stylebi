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
package inetsoft.web.composer.vs.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;
/**
 * Class that encapsulates the parameters for removing an object to the layout.
 *
 * @since 12.3
 */
@Value.Immutable
@JsonDeserialize(as = ImmutableRemoveVSLayoutObjectEvent.class)
public abstract class RemoveVSLayoutObjectEvent implements BaseVSLayoutEvent {

   /**
    * Gets the name of the object.
    *
    * @return the name of the object.
    */
   public abstract String[] names();
}
