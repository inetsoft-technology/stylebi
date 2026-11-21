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

/**
 * The name of the original 40-color chart palette. It is a key: a customer's format.css can
 * override it as ChartPalette[name='Default'], so it must not be renamed.
 */
export const LEGACY_PALETTE_NAME = "Default";

/**
 * The name a palette is shown under in a palette list. The original palette is shown as "Legacy",
 * because it is no longer the default of a modern chart. The stored name is unchanged.
 */
export function paletteLabel(name: string): string {
   return name === LEGACY_PALETTE_NAME ? "_#(js:Legacy)" : name;
}
