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
package inetsoft.report.composition;

import java.io.Serializable;

/**
 * Wrapper class that holds compressed RuntimeSheetState data for storage in Ignite cache.
 * This reduces off-heap memory usage by storing GZIP compressed JSON instead of
 * the full serialized state objects.
 */
public class CompressedSheetState implements Serializable {
   public CompressedSheetState(byte[] compressedData, SheetType type, String user) {
      this.compressedData = compressedData;
      this.type = type;
      this.user = user;
   }

   public byte[] getCompressedData() {
      return compressedData;
   }

   public SheetType getType() {
      return type;
   }

   public String getUser() {
      return user;
   }

   public enum SheetType {
      VIEWSHEET,
      WORKSHEET;
   }

   private final byte[] compressedData;
   private final SheetType type;
   private final String user; // Stored separately for getAllIds filtering

   private static final long serialVersionUID = 1L;
}
