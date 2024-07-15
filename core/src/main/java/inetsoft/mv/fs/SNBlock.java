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
package inetsoft.mv.fs;

/**
 * SNBlock, server side node block.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class SNBlock extends NBlock {
   /**
    * Constructor.
    */
   public SNBlock() {
      super();
   }

   /**
    * Constructor.
    */
   public SNBlock(String parent, String id) {
      super(parent, id);
   }

   /**
    * Constructor.
    */
   public SNBlock(NBlock block) {
      this(block.getParent(), block.getID());

      setLength(block.getLength());
      setPhysicalLen(block.getPhysicalLen());
      setVersion(block.getVersion());
   }
}
