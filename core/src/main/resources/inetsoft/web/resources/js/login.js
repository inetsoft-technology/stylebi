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
function initLoginView(requestedUrl, sessionExpired, defaultErrorMessage, gatewayErrorMessage,
                       currentUser, onloadError, sessionLimitColumnUser, sessionLimitColumnAge)
{
   var $userNameField = $("#loginUserName");
   var $userNameError = $("#userNameError");

   var $passwordField = $("#loginPassword");
   var $passwordError = $("#passwordError");

   var $loginAsGroup = $("#loginAsGroup");
   var $loginAsNameField = $("#loginAsName");

   var $loginButton = $("#loginButton");
   var $confirmLogin = $("#confirmLogin");
   var $terminateSessionButton = $("#terminateSessionButton");
   var $sessionLimitTableBody = $("#sessionLimitTableBody");

   var $loadingIndicator = $(".loading-indicator");
   var $notifications = $("#notifications");
   var $sessionExpiredMessage = $("#sessionExpiredMessage");

   var userName = null;
   var password = null;
   var loginAsName = null;
   var selectedSessionId = null;

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
                if(data && data.sessionsExceeded) {
                   showSessionLimitDialog(data.activeSessions);
                }
                else if(data && data.logInAs) {
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

                if(jqXHR && (jqXHR.status === 502 || jqXHR.status === 503)) {
                   message = gatewayErrorMessage;
                }
                else if(errorThrown && responseText) {
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

   $terminateSessionButton.click(function() {
      if(!selectedSessionId) {
         return;
      }

      $("#sessionLimitDialog").modal("hide");
      $loadingIndicator.addClass("loading");

      try {
         let headers = {};

         $("#loginLocale").each(function() {
            headers["Inetsoft-Locale"] = $(this).val();
         });

         authenticateUserWithSessionReplacement(
            userName, password, requestedUrl, selectedSessionId,
            () => {
               if(requestedUrl != null) {
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

               if(jqXHR && (jqXHR.status === 502 || jqXHR.status === 503)) {
                  message = gatewayErrorMessage;
               }
               else if(errorThrown && responseText) {
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
   });

   function showSessionLimitDialog(sessions) {
      selectedSessionId = null;
      $terminateSessionButton.prop("disabled", true);
      $sessionLimitTableBody.empty();

      if(sessions && sessions.length > 0) {
         var now = Date.now();

         for(var i = 0; i < sessions.length; i++) {
            var session = sessions[i];
            var ageMs = now - (session.loginTime || 0);
            var ageStr = formatSessionAge(ageMs);

            var $row = $("<tr/>").attr("data-session-id", session.sessionId);
            var $radioCell = $("<td/>");
            var $radio = $("<input/>").attr({
               type: "radio",
               name: "sessionToTerminate",
               value: session.sessionId
            });
            $radioCell.append($radio);
            $row.append($radioCell);
            $row.append($("<td/>").text(session.username || ""));
            $row.append($("<td/>").text(ageStr));

            $row.click(function() {
               var sessionId = $(this).attr("data-session-id");
               $(this).find("input[type=radio]").prop("checked", true);
               selectedSessionId = sessionId;
               $terminateSessionButton.prop("disabled", false);
               $sessionLimitTableBody.find("tr").removeClass("table-active");
               $(this).addClass("table-active");
            });

            $sessionLimitTableBody.append($row);
         }
      }

      $("#sessionLimitDialog").modal("show");
   }

   function formatSessionAge(ageMs) {
      var totalSeconds = Math.floor(ageMs / 1000);
      var hours = Math.floor(totalSeconds / 3600);
      var minutes = Math.floor((totalSeconds % 3600) / 60);
      var seconds = totalSeconds % 60;

      if(hours > 0) {
         return hours + "h " + minutes + "m";
      }
      else if(minutes > 0) {
         return minutes + "m " + seconds + "s";
      }
      else {
         return seconds + "s";
      }
   }

   $userNameField.keyup(function(event) {
      validateForm();

      if(firstLogin == "false") {
         $loginAsGroup.css("display", "none");
      }

      if(event.which != 13) {
         userNamePristine = false;
      }
   });

   $passwordField.keyup(function(event) {
      validateForm();

      if(firstLogin == "false") {
         $loginAsGroup.css("display", "none");
      }

      if(event.which != 13) {
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
   headers["LoginAsUser"] = encodeURIComponent(loginAsName);
   headers["FirstLogin"] = encodeURIComponent(firstLogin);

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

function authenticateUserWithSessionReplacement(userName, password, requestedUrl, sessionIdToReplace,
                                                 callBack, errorCallBack, completeCallBack, headers)
{
   if(!headers) {
      headers = {};
   }

   headers["X-Requested-With"] = "XMLHttpRequest";
   headers["Authorization"] = "Basic " + btoa(encodeURIComponent(userName) + ":" + encodeURIComponent(password));
   headers["SessionToReplace"] = sessionIdToReplace;

   $.ajax({
      url: requestedUrl,
      type: "get",
      headers: headers,
      cache: false,
      success: function(data) {
         if(data && (data.sessionsExceeded || data.logInAs)) {
            // Should not happen in normal flow, treat as an error
            if(errorCallBack) {
               errorCallBack({ status: 0 }, "error", "Unexpected response");
            }
         }
         else if(callBack) {
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
