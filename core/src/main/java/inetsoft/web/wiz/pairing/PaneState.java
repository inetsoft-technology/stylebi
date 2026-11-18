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
package inetsoft.web.wiz.pairing;

/**
 * What the browser's open pane currently shows that its {@link EditorContext} does not say, because
 * the user can change it after the code was minted. Kept apart from {@code EditorContext} on
 * purpose: that record is compared for equality when a pane is detached and when a session is
 * retargeted, so a value the user can flip must not be part of it.
 *
 * @param sql       whether the pane's SQL/Script switch is on SQL (the calculated field formula
 *                  dialog)
 * @param aggregate whether the pane's "Calculate Field From" choice is Aggregate rather than
 *                  Detail (the calculated field formula dialog)
 */
public record PaneState(boolean sql, boolean aggregate) {
   /** A state that only carries the SQL/Script switch (Detail calculation). */
   public PaneState(boolean sql) {
      this(sql, false);
   }

   /** The state of a pane that never reported one: its default value type. */
   public static final PaneState DEFAULT = new PaneState(false, false);
}
