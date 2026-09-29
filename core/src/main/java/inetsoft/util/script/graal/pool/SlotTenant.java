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
package inetsoft.util.script.graal.pool;

/**
 * A formula table whose owned vars hold script objects that live on a pooled context, its
 * home (Testing #77123, B1 residual part 2). The pool holds tenants weakly.
 */
public interface SlotTenant {
   /**
    * Hand off: save, as a context-free tree, the values of this tenant that live in
    * {@code codec.context()}, whose slot the calling thread holds while it is idle, so the pool
    * can close, expire or reuse that slot. The tenant takes its own table lock without waiting;
    * the pool never waits for it.
    *
    * @return {@code true} if none of its values live there any more (saved, or lost with a
    *         warning when read); {@code false} if it could not take its lock now, and the slot
    *         stays its home.
    */
   boolean handOff(OwnedValueCodec codec);
}
