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
package inetsoft.report.internal.png;

public interface PNGConstants {
   public static final byte[] PNG_SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10,
      26, 10 };
   public static final byte PNG_COLOR_TYPE_PALLETE = 1;
   public static final byte PNG_COLOR_TYPE_COLOR = 2;
   public static final byte PNG_COLOR_TYPE_ALPHA = 4;
   public static final byte PNG_COMPRESSION_TYPE_DEFAULT = 0;
   public static final byte PNG_COMPRESSION_TYPE_LZ77 = 0;
   public static final byte PNG_FILTER_METHOD_DEFAULT = 0;
   public static final byte PNG_INTERLACE_NONE = 0;
   public static final byte PNG_INTERLACE_ADAM7 = 1;
   public static final byte PNG_FILTER_NULL = 0;
   public static final byte PNG_FILTER_SUB = 1;
   public static final byte PNG_FILTER_UP = 2;
   public static final byte PNG_FILTER_AVERAGE = 3;
   public static final byte PNG_FILTER_PAETH = 4;
   public static final byte[] PNG_IHDR = {  73, 72, 68, 82 };
   public static final byte[] PNG_IDAT = {  73, 68, 65, 84 };
   public static final byte[] PNG_IEND = {  73, 69, 78, 68 };
   public static final byte[] PNG_PLTE = {  80, 76, 84, 69 };
   public static final byte[] PNG_BKGD = {  98, 75, 71, 68 };
   public static final byte[] PNG_CHRM = {  99, 72, 82, 77 };
   public static final byte[] PNG_GAMA = { 103, 65, 77, 65 };
   public static final byte[] PNG_HIST = { 104, 73, 83, 84 };
   public static final byte[] PNG_OFFS = { 111, 70, 70, 115 };
   public static final byte[] PNG_PCAL = { 112, 67, 65, 76 };
   public static final byte[] PNG_PHYS = { 112, 72, 89, 115 };
   public static final byte[] PNG_SBIT = { 115, 66, 73, 84 };
   public static final byte[] PNG_TEXT = { 116, 69, 88, 116 };
   public static final byte[] PNG_TIME = { 116, 73, 77, 69 };
   public static final byte[] PNG_TRNS = { 116, 82, 87, 83 };
   public static final byte[] PNG_ZTXT = { 122, 84, 88, 116 };
   public static final int PNG_ZBUF_SIZE = 8192;
}

