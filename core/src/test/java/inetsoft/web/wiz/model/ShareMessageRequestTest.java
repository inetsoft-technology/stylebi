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
package inetsoft.web.wiz.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code link}/{@code message} back {@code ShareMessage.link()}/{@code message()}
 * (non-{@code @Nullable} Immutables attributes) — omitting either from the JSON body must fail
 * validation with a clean 400 (via {@code @Valid} in {@code WizShareController} +
 * {@code GlobalExceptionHandler}'s default {@code MethodArgumentNotValidException} handling)
 * instead of reaching {@code toShareMessage()} and throwing an unhandled
 * {@code NullPointerException} from the Immutables builder.
 */
@Tag("core")
class ShareMessageRequestTest {
   @Test
   void linkIsRequired() {
      ShareMessageRequest request = new ShareMessageRequest();
      request.setMessage("Check this out");

      Set<ConstraintViolation<ShareMessageRequest>> violations = VALIDATOR.validate(request);

      assertTrue(violations.stream().anyMatch(v -> "link".equals(v.getPropertyPath().toString())));
   }

   @Test
   void messageIsRequired() {
      ShareMessageRequest request = new ShareMessageRequest();
      request.setLink("/sree/viewer/view/global/visualizations-593.../abc");

      Set<ConstraintViolation<ShareMessageRequest>> violations = VALIDATOR.validate(request);

      assertTrue(violations.stream().anyMatch(v -> "message".equals(v.getPropertyPath().toString())));
   }

   @Test
   void validWhenLinkAndMessagePresent() {
      ShareMessageRequest request = new ShareMessageRequest();
      request.setLink("/sree/viewer/view/global/visualizations-593.../abc");
      request.setMessage("Check this out");

      assertTrue(VALIDATOR.validate(request).isEmpty());
   }

   // ParameterMessageInterpolator sidesteps the default interpolator's jakarta.el requirement,
   // which isn't on this module's classpath — irrelevant here since violation messages aren't asserted.
   private static final Validator VALIDATOR = Validation.byDefaultProvider().configure()
      .messageInterpolator(new ParameterMessageInterpolator())
      .buildValidatorFactory().getValidator();
}
