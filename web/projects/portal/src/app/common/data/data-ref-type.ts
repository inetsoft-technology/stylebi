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
export const enum DataRefType {
   /**
    * Normal data ref.
    */
   NONE = 0,
   /**
    * Dimension data ref.
    */
   DIMENSION = 1,
   /**
    * Measure data ref.
    */
   MEASURE = 2,
   /**
    * Cube data ref.
    */
   CUBE = 4,
   /**
    * Model cube data ref.
    */
   MODEL = 8,
   /**
    * Time dimension data ref.
    */
   TIME = 16,
   /**
    * Calculate based on aggregate value.
    */
   AGG_CALC = 32,
   /**
    * Cube dimension data ref.
    */
   CUBE_DIMENSION = CUBE | DIMENSION,
   /**
    * Cube time dimension data ref.
    */
   CUBE_TIME_DIMENSION = CUBE | TIME | DIMENSION,

   /**
    * Cube model dimension data ref.
    */
   CUBE_MODEL_DIMENSION	= CUBE | MODEL | DIMENSION,
   /**
    * Cube model time dimension data ref.
    */
   CUBE_MODEL_TIME_DIMENSION = CUBE | MODEL | TIME | DIMENSION,
   /**
    * Cube measure data ref.
    */
   CUBE_MEASURE = CUBE | MEASURE
}
