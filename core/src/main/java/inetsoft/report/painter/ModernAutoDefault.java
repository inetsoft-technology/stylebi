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

/**
 * The modern style scales a Bar, Bar +/- or Bullet Graph to the column's data unless the author
 * decided otherwise. The presenters' own default stays off, so classic tables, reports and scripts
 * keep their configured maximum; only a presenter whose Auto setting was never set is turned on.
 * The table lens and the presenter dialog both apply it, so the dialog shows what is drawn.
 */
public final class ModernAutoDefault {
   private ModernAutoDefault() {
   }

   /**
    * Turn the presenter's auto scaling on if the author never set it. Any other presenter is left
    * alone.
    */
   public static void apply(Presenter presenter) {
      if(presenter instanceof BarPresenter bar) {
         bar.useModernAutoMaximum();
         bar.useModernIgnoreOutliers();
      }
      else if(presenter instanceof Bar2Presenter bar) {
         bar.useModernAutoMaximum();
         bar.useModernIgnoreOutliers();
      }
      else if(presenter instanceof BulletGraphPresenter bullet) {
         bullet.useModernAutoScale();
         bullet.useModernIgnoreOutliers();
      }
   }
}
