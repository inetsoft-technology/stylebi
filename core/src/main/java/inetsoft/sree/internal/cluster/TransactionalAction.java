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
package inetsoft.sree.internal.cluster;

/**
 * An action run by {@link Cluster#runInTransaction}.
 *
 * @param <T> the type of the result.
 * @param <E> the type of the checked exception the action may throw.
 */
@FunctionalInterface
public interface TransactionalAction<T, E extends Exception> {
   /**
    * Runs the action.
    *
    * @return the result.
    */
   T run() throws E;
}
