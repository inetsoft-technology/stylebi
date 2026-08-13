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
package inetsoft.web.admin.ai;

import java.util.List;

/**
 * Catalog metadata plus the current value, for plugin/agent discovery.
 *
 * @param exists   whether the property demonstrably exists in StyleBI: {@code confirmed} when it is
 *                 catalogued or has a stored value, {@code unknown} otherwise. Two distinct things
 *                 produce a null {@code currentValue} on an uncatalogued property — a real property
 *                 nobody has set yet, and a name that does not exist at all — and the server cannot
 *                 tell them apart, because the only evidence a bare {@code SreeEnv} property exists
 *                 is a read site in Java source. Reporting that ambiguity is the point: a caller
 *                 that reads a null value as "no such property" will conclude the setting lives
 *                 somewhere else, and a caller that reads it as "exists, unset" will happily write a
 *                 typo. Both have happened.
 * @param guidance what to do about an {@code unknown} property; null when {@code exists} is
 *                 {@code confirmed}.
 */
public record PropertyView(String name, List<String> aliases, String type,
                           List<String> allowedValues, Integer min, Integer max,
                           String description, String risk, String snapshotScope,
                           String currentValue, boolean recognized,
                           String exists, String guidance)
{
   /** The property is catalogued, or holds a value; either way it is real. */
   public static final String EXISTS_CONFIRMED = "confirmed";
   /** Uncatalogued and unset — indistinguishable from a name that does not exist. */
   public static final String EXISTS_UNKNOWN = "unknown";
}
