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
package inetsoft.sree.internal.cluster;

/**
 * {@code SingletonRunnableTask} is an interface for classes that perform a task on a single node in
 * the cluster ensuring split-brain protection.
 * <p>
 * Implementations of this class cannot be lambdas, method references, or non-static inner classes.
 * The must be able to be serialized and sent to the cluster node that is assigned to the service.
 */
public interface SingletonRunnableTask extends SingletonTask, Runnable {
}
