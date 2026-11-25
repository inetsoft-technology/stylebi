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
package inetsoft.web.composer.vs;

import inetsoft.report.Presenter;

/**
 * What a presenter does with the values it cannot draw, for the presenter dropdown and dialog.
 * A presenter that does not accept a value leaves the cell showing the value's own text, which
 * nothing in the UI said. The hint is worked out from what the presenter accepts, so it cannot
 * drift from the presenter.
 */
public final class PresenterValueHint {
   public static final String NUMBERS = "Draws numbers. Other values show as plain text.";
   public static final String NUMBERS_AND_BOOLEANS =
      "Draws numbers and true or false values. Other values show as plain text.";

   private PresenterValueHint() {
   }

   /**
    * @return the hint for the presenter, or null when it has nothing to say: it takes any text
    *         (an image URL, HTML, a barcode) or it does not draw numbers.
    */
   public static String of(Presenter presenter) {
      if(presenter == null || !presenter.isPresenterOf(Number.class)) {
         return null;
      }

      // a presenter that takes any text cannot leave a text value as plain text
      if(presenter.isPresenterOf((Object) "a text value")) {
         return null;
      }

      boolean booleans = presenter.isPresenterOf(Boolean.class) ||
         presenter.isPresenterOf((Object) "true");

      return booleans ? NUMBERS_AND_BOOLEANS : NUMBERS;
   }
}
