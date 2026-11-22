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
package inetsoft.report.painter;

import inetsoft.report.Presenter;

import java.util.function.DoubleSupplier;

/**
 * A presenter that can scale its drawing to the maximum of the data it is shown with, e.g.
 * the cells of a table column. The table lens that wraps the presenter in a painter supplies
 * the maximum lazily, so the data is only scanned when a cell is actually painted.
 */
public interface AutoMaximumPresenter extends Presenter {
   /**
    * Check if the drawing maximum is taken from the data instead of the configured maximum.
    */
   boolean isAutoMaximum();

   /**
    * Check if the data maximum is the largest absolute value (positive and negative values
    * share one scale) instead of the largest positive value.
    */
   boolean absoluteMaximum();

   /**
    * Create a copy of this presenter that resolves its drawing maximum from the resolver at
    * paint time. The resolver returns NaN or a value {@code <= 0} when no maximum is
    * available, in which case the configured maximum is used. This presenter is not changed.
    */
   Presenter withMaximumResolver(DoubleSupplier resolver);
}
