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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Event used to layout the position of the assemblies in a worksheet view.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableWSLayoutGraphEvent.class)
@JsonDeserialize(as = ImmutableWSLayoutGraphEvent.class)
public abstract class WSLayoutGraphEvent {
   /**
    * The names of the assemblies to be laid out.
    */
   public abstract String[] names();

   /**
    * The widths of the assemblies.
    */
   public abstract int[] widths();

   /**
    * The heights of the assemblies.
    */
   public abstract int[] heights();

   /**
    * Creates a new builder instance.
    */
   public static Builder builder() {
      return new Builder();
   }

   /**
    * Class that is responsible for building new instances of <tt>WSLayoutGraphEvent</tt>.
    */
   public static class Builder extends ImmutableWSLayoutGraphEvent.Builder {
   }
}
