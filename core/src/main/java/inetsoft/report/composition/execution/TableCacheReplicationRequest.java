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
package inetsoft.report.composition.execution;

import java.io.Serializable;

/**
 * Cluster message broadcast by a newly-started node to request that all other nodes flush their
 * local {@link AssetDataCache} entries to the distributed table cache store. Receiving nodes
 * respond by writing any cached {@link inetsoft.report.TableLens} objects they hold to the
 * distributed store asynchronously; the requesting node picks them up on cache miss.
 * <p>
 * This is only active when {@code distributed.table.cache.mode=prestop}. In that mode, normal
 * {@link DistributedTableCacheStore#put} calls are no-ops, so the distributed store is only
 * populated at node shutdown (flush) or when another node requests replication.
 */
public class TableCacheReplicationRequest implements Serializable {
   private static final long serialVersionUID = 1L;
}
