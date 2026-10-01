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
package inetsoft.report.script;

/**
 * Test fixture for {@code inetsoft.util.script.graal.ScriptSharedStaticStateTest}: public statics
 * of a kind a script must not be able to use, on a class the script class filter admits (the
 * test's own package is refused by the filter).
 */
public final class SharedStaticFixture {
   private SharedStaticFixture() {
   }

   public static final ThreadLocal<String> LOCAL = new ThreadLocal<>();
   public static final ThreadLocal<String> SUPPLIED = ThreadLocal.withInitial(() -> "initial");
}
