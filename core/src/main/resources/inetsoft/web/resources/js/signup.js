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
const EMAIL_REGEXP =
    /^(?=.{1,254}$)(?=.{1,64}@)[-!#$%&'*+/0-9=?A-Z^_`a-z{|}~]+(\.[-!#$%&'*+/0-9=?A-Z^_`a-z{|}~]+)*@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;

function initSignUpView() {
   let $userEmailField = $("#email");
   let $signButton = $("#signupButton");
   let $emailError = $("#emailError");
   let $emailRequiredError = $("#emailRequiredError");
   let $emailFormatError = $("#emailFormatError");
   let $notifications = $("#notifications");
   let $loadingIndicator = $(".loading-indicator");

   let userEmail;
   let userEmailValid;

   let emailPristine = true;

   function validateForm(ignorePristineState) {
      userEmail = $userEmailField.val();
      userEmailValid = EMAIL_REGEXP.test(userEmail);
      updateViewState(ignorePristineState);
   }

   function updateViewState(ignorePristineState) {
      if((ignorePristineState || !emailPristine) && (!userEmail || !userEmailValid)) {
         $userEmailField.addClass("is-invalid");
         $emailError.show();

         if(!userEmail) {
            $emailRequiredError.show();
            $emailFormatError.hide();
         }
         else {
            $emailFormatError.show();
            $emailRequiredError.hide();
         }
      }
      else {
         $userEmailField.removeClass("is-invalid");
         $emailError.hide();
         $emailRequiredError.hide();
         $emailFormatError.hide();
      }
   }

   function signWithEmail() {
      validateForm(true);

      if(!userEmailValid) {
         return;
      }

      $loadingIndicator.addClass("loading");

      $.ajax({
         url: "signup/withEmail?email=" + $userEmailField.val(),
         type: "get",
         cache: false,
         success: function(data) {
            if(data.success) {
               window.location.href = "signupDetail.html";
            }
            else {
               $notifications.html(data.errorMessage);
               $notifications.show();
            }
         },
         error: function(jqXHR, textStatus, errorThrown) {
            handleRequestError(jqXHR, textStatus, errorThrown, $notifications);
         },
         complete: function() {
            $loadingIndicator.removeClass("loading");
         }
      })
   }

   $userEmailField.keyup(function(event) {
      if(event.which == 13) {
         signWithEmail();
      }
      else {
         emailPristine = false;
      }

      validateForm();
   });

   $userEmailField.keyup(function(event) {
      if(event.which == 13) {
         signWithEmail();
      }
      else {
         emailPristine = false;
      }

      validateForm();
   });

   $userEmailField.keyup(function(event) {
      if(event.which == 13) {
         signWithEmail();
      }
      else {
         emailPristine = false;
      }

      validateForm();
   });

   $(document).on("keypress", function(e) {
      if(e.which == 13) {
         signWithEmail();
      }
   });

   $signButton.click(function() {
      signWithEmail(false);
   });

   validateForm();
}

function initSignUpDetailView(requestedUrl) {
   const USER_NAME_MAX_LENGTH = 39;
   const PASSWORD_MIN_LENGTH = 8;
   const PASSWORD_MAX_LENGTH = 72;
   let $signupButton  = $("#signupButton");
   let $userNameField = $("#signupUserName");
   let $passwordField = $("#signupPassword");
   let $verifyPasswordField = $("#signupVerifyPassword");
   let $emailCodeField = $("#emailCode");
   let $resendCodeBtn = $("#resendCodeBtn");
   let $loadingIndicator = $(".loading-indicator");

   let $userNameError = $("#userNameError");
   let $userNameRequiredError = $("#userNameRequiredError");
   let $userNameFormatError = $("#userNameFormatError");
   let $userNameTooLongError = $("#userNameTooLongError");
   let $passwordError = $("#passwordError");
   let $passwordRequiredError = $("#passwordRequiredError");
   let $passwordFormatError = $("#passwordFormatError");
   let $passwordTooLongError = $("#passwordTooLongError");
   let $verifyPasswordError = $("#verifyPasswordError");
   let $verifyPasswordRequiredError = $("#verifyPasswordRequiredError");
   let $verifyPasswordFormatError = $("#verifyPasswordFormatError");
   let $emailCodeError = $("#emailCodeError");
   let $emailCodeRequiredError = $("#emailCodeRequiredError");
   let $emailCodeFormatError = $("#emailCodeFormatError");
   let $notifications = $("#notifications");

   let userName;
   let password;
   let verifyPassword;
   let launchCode;

   let userNameValid;
   let passwordValid;
   let verifyPasswordValid;
   let launchCodeValid;

   let userNamePristine = true;
   let passwordPristine = true;
   let verifyPasswordPristine = true;
   let launchCodePristine = true;
   let resendBtnEnable = true;

   function validateForm(ignorePristineState) {
      userName = $userNameField.val();
      userNameValid = validateUserName(userName);
      password = $passwordField.val();
      passwordValid = validatePassword(password);
      verifyPassword = $verifyPasswordField.val();

      if(password != verifyPassword) {
         verifyPasswordValid = false;
      }
      else {
         verifyPasswordValid = true;
      }

      launchCode = $emailCodeField.val();

      if(!launchCode || launchCode.length != 6) {
         launchCodeValid = false;
      }
      else {
         launchCodeValid = true;
      }

      updateViewState(ignorePristineState);
   }

   function updateViewState(ignorePristineState) {
      updateFieldView(!ignorePristineState && userNamePristine, userName, userNameValid, $userNameField,
          $userNameError, $userNameRequiredError, $userNameFormatError, $userNameTooLongError, 39);
      updateFieldView(!ignorePristineState && passwordPristine, password, passwordValid, $passwordField,
          $passwordError, $passwordRequiredError, $passwordFormatError, $passwordTooLongError, 72);
      updateFieldView(!ignorePristineState && verifyPasswordPristine, verifyPassword, verifyPasswordValid,
          $verifyPasswordField, $verifyPasswordError, $verifyPasswordRequiredError, $verifyPasswordFormatError);
      updateFieldView(!ignorePristineState && launchCodePristine, launchCode, launchCodeValid, $emailCodeField,
          $emailCodeError, $emailCodeRequiredError, $emailCodeFormatError);

      if(!userNameValid || !passwordValid || !verifyPasswordValid || !launchCodeValid) {
         $signupButton.addClass("disabled");
      }
      else {
         $signupButton.removeClass("disabled");
      }
   }

   function updateFieldView(valPristine, value, valid, fieldEle, errorEle, requiredEle, formatErrorEle,
                            tooLongErrorEle, valueLimitLong)
   {
      if(!valPristine && (!value || !valid)) {
         fieldEle.addClass("is-invalid");
         errorEle.show();

         if(!value) {
            requiredEle.show();
            formatErrorEle.hide();

            if(tooLongErrorEle) {
               tooLongErrorEle.hide();
            }
         }
         else if(tooLongErrorEle && valueLimitLong > 0 && valueLimitLong < value.length) {
            tooLongErrorEle.show();
            formatErrorEle.hide();
            requiredEle.hide();
         }
         else {
            formatErrorEle.show();
            requiredEle.hide();

            if(tooLongErrorEle) {
               tooLongErrorEle.hide();
            }
         }
      }
      else {
         fieldEle.removeClass("is-invalid");
         errorEle.hide();
         requiredEle.hide();
         formatErrorEle.hide();

         if(tooLongErrorEle) {
            tooLongErrorEle.hide();
         }
      }
   }

   function validateUserName(name) {
      return name && name.length < USER_NAME_MAX_LENGTH && !/[~`!#%^*=\[\]\\;,/{}|":<>?()]/g.test(name);
   }

   function validatePassword(password) {
      if((!password || password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH) ||
          !/[A-Za-z]/g.test(password) || !/[0-9]/g.test(password))
      {
         return false;
      }

      return true;
   }

   function signup() {
      validateForm(true);

      if(!userNameValid || !passwordValid || !verifyPasswordValid || !launchCodeValid) {
         return;
      }

      $loadingIndicator.addClass("loading");

      let url = "signup/userDetail?name=" + userName + "&password=" + password +
          "&code=" + $emailCodeField.val();

      $.ajax({
         url: url,
         type: "get",
         cache: false,
         success: function(data) {
            if(data.success) {
               authenticateUser(userName, password, userName, requestedUrl, true,
                   () => {
                      if(requestedUrl != null) {
                         window.location.href = requestedUrl;
                      }
                      else {
                         window.location.href = "index.html";
                      }
                   }
               );
            }
            else {
               $notifications.html(data.errorMessage);
               $notifications.show();
            }
         },
         error: function(jqXHR, textStatus, errorThrown) {
            handleRequestError(jqXHR, textStatus, errorThrown, $notifications);
         },
         complete: function() {
            $loadingIndicator.removeClass("loading");
         }
      })
   }

   function resendEmailCode() {
      $.ajax({
         url: "signup/resendEmailCode",
         type: "get",
         cache: false,
         success: function(data) {
            if(data.success) {
               updateResendBtn();
            }
            else {
               $notifications.html(data.errorMessage);
               $notifications.show();
            }
         },
         error: function(jqXHR, textStatus, errorThrown) {
            handleRequestError(jqXHR, textStatus, errorThrown, $notifications);
         },
         complete: function() {
            //Todo finish
         }
      })
   }

   function updateResendBtn() {
      let time = 60;
      resendBtnEnable = false;
      $resendCodeBtn.addClass("disabled");

      let interVal = window.setInterval(() => {
         if(time < 0) {
            window.clearInterval(interVal);
            $resendCodeBtn.removeClass("disabled");
            resendBtnEnable = true;
            $resendCodeBtn.html("Resend the code");

            return;
         }

         $resendCodeBtn.html(time + "s");
         time--;
      }, 1000)
   }

   $userNameField.keyup(function(event) {
      if(event.which == 13) {
         signup();
      }
      else {
         userNamePristine = false;
      }

      validateForm();
   });

   $passwordField.keyup(function(event) {
      if(event.which == 13) {
         signup();
      }
      else {
         passwordPristine = false;
      }

      validateForm();
   });

   $verifyPasswordField.keyup(function(event) {
      if(event.which == 13) {
         signup();
      }
      else {
         verifyPasswordPristine = false;
      }

      validateForm();
   });

   $emailCodeField.keyup(function(event) {
      if(event.which == 13) {
         signup();
      }
      else {
         launchCodePristine = false;
      }

      validateForm();
   });

   $resendCodeBtn.click(function() {
      resendEmailCode();
   });

   $(document).on("keypress", function(e) {
      if(e.which == 13) {
         signup();
      }
   });

   $signupButton.click(function() {
      signup();
   });

   validateForm();
}

function handleRequestError(jqXHR, textStatus, errorThrown, notificationsEle) {
   let message;
   let responseText;

   try {
      responseText = JSON.parse(jqXHR.responseText).message;
   }
   catch(ignore) {
   }

   if(!responseText) {
      responseText = jqXHR.responseText;
   }

   if(errorThrown && responseText) {
      message = errorThrown + " - " + responseText;
   }
   else if(errorThrown) {
      message = errorThrown;
   }
   else if(responseText) {
      message = responseText;
   }
   else {
      message = defaultErrorMessage;
   }

   notificationsEle.html(message);
   notificationsEle.show();
}
