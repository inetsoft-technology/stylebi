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
function initLoginView(requestedUrl, sessionExpired, defaultErrorMessage, currentUser, onloadError) {
   var $userNameField = $("#loginUserName");
   var $userNameError = $("#userNameError");

   var $passwordField = $("#loginPassword");
   var $passwordError = $("#passwordError");

   var $loginAsGroup = $("#loginAsGroup");
   var $loginAsNameField = $("#loginAsName");

   var $loginButton = $("#loginButton");
   var $confirmLogin = $("#confirmLogin");

   var $loadingIndicator = $(".loading-indicator");
   var $notifications = $("#notifications");
   var $sessionExpiredMessage = $("#sessionExpiredMessage");

   var userName = null;
   var password = null;
   var loginAsName = null;

   var userNameInvalid = false;
   var userNamePristine = true;
   var passwordInvalid = false;
   var passwordPristine = true;
   var loginAsPristine = true;
   var firstLogin = "true";

   var updateViewState = function() {
      if(userNameInvalid && !userNamePristine) {
         $userNameField.addClass("is-invalid");
         $userNameError.show();
      }
      else {
         $userNameField.removeClass("is-invalid");
         $userNameError.hide();
      }

      if(passwordInvalid && !passwordPristine) {
         $passwordField.addClass("is-invalid");
         $passwordError.show();
      }
      else {
         $passwordField.removeClass("is-invalid");
         $passwordError.hide();
      }

      // auto-filled values not detected and there is no consistent way across browsers
      // don't disable
      // $loginButton.prop("disabled", userNameInvalid || passwordInvalid);
   };

   var validateForm = function() {
      userName = $userNameField.val();
      userNameInvalid = !userName;
      password = $passwordField.val();
      passwordInvalid = !password;
      loginAsName = $loginAsNameField.val();

      if(!loginAsName) {
         loginAsName = userName;
      }

      updateViewState();
   };

   var authenticate = function(logoutCurrent) {
      // needed for auto-filled values
      validateForm();

      if(userNameInvalid || passwordInvalid) {
         return;
      }

      if(!!currentUser && !!userName && currentUser != userName && !logoutCurrent) {
         $("#activeSession").modal("show");
         return;
      }

      $sessionExpiredMessage.hide();
      $notifications.hide();
      $loadingIndicator.addClass("loading");

      try {
         let headers = {};

         $("#loginLocale").each(function() {
            headers["Inetsoft-Locale"] = $(this).val();
         });

         authenticateUser(userName, password, loginAsName, requestedUrl, firstLogin,
             (data) => {
                if(data && data.logInAs) {
                   firstLogin = "false";
                   $loginAsGroup.css("display", "block");
                   $loginAsNameField.empty();
                   loginAsName = userName;
                   $("<option/>").val("").html("").appendTo($loginAsNameField);

                   for(var i = 0; i < data.users.length; i++) {
                      $("<option/>")
                          .val(data.users[i].name)
                          .html(data.users[i].label)
                          .appendTo($loginAsNameField);
                   }
                }
                else if(requestedUrl != null) {
                   window.location.href = requestedUrl;
                }
                else {
                   window.location.href = "index.html";
                }
             },
             (jqXHR, textStatus, errorThrown) => {
                var message;
                var responseText;

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

                $notifications.html(message);
                $notifications.show();
             },
             () => {
                $loadingIndicator.removeClass("loading");
             },
             headers);
      }
      catch(ignore) {
         $loadingIndicator.removeClass("loading");
      }
   };

   $(document).on("keypress", function(e) {
      if(e.which == 13) {
         $("#activeSession").modal("hide");
         authenticate(true);
      }
   });

   $loginButton.click(function() {
      authenticate(false);
   });

   $confirmLogin.click(function() {
      $("#activeSession").modal("hide");
      authenticate(true);
   });

   $userNameField.keyup(function(event) {
      validateForm();

      if(firstLogin == "false") {
         $loginAsGroup.css("display", "none");
      }

      if(event.which == 13) {
         authenticate();
      }
      else {
         userNamePristine = false;
      }
   });

   $passwordField.keyup(function(event) {
      validateForm();

      if(firstLogin == "false") {
         $loginAsGroup.css("display", "none");
      }

      if(event.which == 13) {
         authenticate();
      }
      else {
         passwordPristine = false;
      }
   });

   $loginAsNameField.change(function() {
      validateForm();
      loginAsPristine = false;
   });

   $userNameField.change(function() {
      validateForm()
   });

   $passwordField.change(function() {
      validateForm()
   });

   if(sessionExpired) {
      $sessionExpiredMessage.show();
   }

   validateForm();

   if(onloadError) {
      $notifications.html(onloadError);
      $notifications.show();
   }
}

function authenticateUser(userName, password, loginAsName, requestedUrl, firstLogin, callBack, errorCallBack,
                          completeCallBack, headers)
{
   if(!headers) {
      headers = {};
   }

   headers["X-Requested-With"] = "XMLHttpRequest";
   headers["Authorization"] = "Basic " + btoa(encodeURIComponent(userName) + ":" + encodeURIComponent(password));
   headers["LoginAsUser"] = loginAsName;
   headers["FirstLogin"] = firstLogin;

   $.ajax({
      url: requestedUrl,
      type: "get",
      headers: headers,
      cache: false,
      success: function(data) {
         if(callBack) {
            callBack(data);
         }
      },
      error: function(jqXHR, textStatus, errorThrown) {
         if(errorCallBack) {
            errorCallBack(jqXHR, textStatus, errorThrown);
         }
      },
      complete: function() {
         if(completeCallBack) {
            completeCallBack();
         }
      }
   })
}
