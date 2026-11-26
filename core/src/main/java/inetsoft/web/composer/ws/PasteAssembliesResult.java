/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.ws;

import java.io.Serializable;
import java.util.*;

/**
 * What {@link PasteAssembliesService#pasteAssemblies} did, for a caller that has no browser to
 * read the focus / message commands from.
 *
 * <p>{@code newNames} and {@code oldNames} are parallel lists, in the order the service renamed
 * the dependencies: the i-th pasted assembly is named {@code newNames[i]} and was copied from
 * {@code oldNames[i]} of the source worksheet. They also list the hidden copies of a pasted
 * mirror's worksheet, which the caller did not request by name. For a cut inside one worksheet
 * the two names are equal (the assembly only moved).
 *
 * <p>{@code skipped} maps each requested name the paste left out to the reason.
 */
public final class PasteAssembliesResult implements Serializable {
   public PasteAssembliesResult(List<String> newNames, List<String> oldNames,
                                Map<String, String> skipped)
   {
      this.newNames = new ArrayList<>(newNames);
      this.oldNames = new ArrayList<>(oldNames);
      this.skipped = new LinkedHashMap<>(skipped);
   }

   public List<String> getNewNames() {
      return newNames;
   }

   public List<String> getOldNames() {
      return oldNames;
   }

   public Map<String, String> getSkipped() {
      return skipped;
   }

   private final ArrayList<String> newNames;
   private final ArrayList<String> oldNames;
   private final LinkedHashMap<String, String> skipped;
}
