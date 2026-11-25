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
import inetsoft.report.painter.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hint about values a presenter does not draw is worked out from what it accepts.
 */
// some presenters reach Spring beans when they are built
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PresenterValueHintTest {
   @Test
   void presentersThatTakeNumbersOnlySayOtherValuesShowAsText() {
      for(Presenter p : new Presenter[] { new BarPresenter(), new Bar2Presenter(),
                                          new BulletGraphPresenter(), new TrafficLightPresenter(),
                                          new IconCounterPresenter() })
      {
         assertEquals(PresenterValueHint.NUMBERS, PresenterValueHint.of(p),
                      p.getClass().getSimpleName());
      }
   }

   @Test
   void theCheckMarkTakesNumbersAndTrueOrFalse() {
      assertEquals(PresenterValueHint.NUMBERS_AND_BOOLEANS,
                   PresenterValueHint.of(new BooleanPresenter()));
   }

   @Test
   void presentersThatTakeAnyTextHaveNothingToSay() {
      for(Presenter p : new Presenter[] { new ImagePresenter(), new HTMLPresenter(),
                                          new ButtonPresenter(), new ShadowPresenter(),
                                          new QRCodePresenter(), new BarcodeCode39Presenter() })
      {
         assertNull(PresenterValueHint.of(p), p.getClass().getSimpleName());
      }
   }

   @Test
   void noPresenterHasNoHint() {
      assertNull(PresenterValueHint.of(null));
   }
}
