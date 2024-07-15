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
$(window).on('load', function() {
    $("#status").fadeOut();
    $("#preloader").delay(350).fadeOut("slow");
})
$(function() {
    "use strict";
    $('#home').css({
        'height': ($(window).height()) + 'px'
    });
    $(window).resize(function() {
        $('#home').css({
            'height': ($(window).height()) + 'px'
        });
    });
});

$(function() {
    $('ul li a').click(function() {
        var item = $(this).parent();
        $('ul li').removeClass('current');
        item.addClass("current")
    });
});

$(document).ready(function() {

    $("ul#menu").click(function() {
        if ($("ul#menu li").css('display') != 'inline') {
            if ($("ul#menu").hasClass('showmenu')) {
                $("ul#menu").removeClass('showmenu');
                $("ul#menu li").css('display', 'none');
            } else {
                $("ul#menu").addClass('showmenu');
                $("ul#menu li").css('display', 'block');
            }
        }
    });
    $(window).resize(function() {
        if ($(window).width() >= 960) {
            if ($("ul#menu li").css('display') == 'none')
                $("ul#menu li").css('display', 'inline');
        } else {
            $("ul#menu li").css('display', 'none');
        }
    });
});

jQuery(document).ready(function() {
    (function($) {
        var container = $('.portfolio-isotope');
        function getNumbColumns() {
            var winWidth = $(window).width()
              , columnNumb = 1;
            if (winWidth > 700) {
                columnNumb = 4;
            } else if (winWidth > 400) {
                columnNumb = 2;
            } else if (winWidth > 200) {
                columnNumb = 1;
            }
            return columnNumb;
        }
        function setColumnWidth() {
            var winWidth = $(window).width()
              , columnNumb = getNumbColumns()
              , postWidth = Math.floor(winWidth / columnNumb);
        }
        $('#portfolio-filter #filter a').click(function() {
            var selector = $(this).attr('data-filter');
            $(this).parent().parent().find('a').removeClass('current');
            $(this).addClass('current');
            container.isotope({
                filter: selector
            });
            setTimeout(function() {
                reArrangeProjects();
            }, 300);
            return false;
        });


    }
    )(jQuery);
});
(function($) {
    var $event = $.event, $special, resizeTimeout;
    $special = $event.special.debouncedresize = {
        setup: function() {
            $(this).on('resize', $special.handler);
        },
        teardown: function() {
            $(this).off('resize', $special.handler);
        },
        handler: function(event, execAsap) {
            var context = this
              , args = arguments
              , dispatch = function() {
                event.type = 'debouncedresize';
                $event.dispatch.apply(context, args);
            };
            if (resizeTimeout) {
                clearTimeout(resizeTimeout);
            }
            execAsap ? dispatch() : resizeTimeout = setTimeout(dispatch, $special.threshold);
        },
        threshold: 150
    };
}
)(jQuery);


jQuery(document).ready(function($) {
    $('#send').click(function() {
        $('.error').fadeOut('slow');
        var error = false;
        var name = $('input#name').val();
        if (name == "" || name == " ") {
            $('#err-name').fadeIn('slow');
            error = true;
        }
        var email_compare = /^([a-z0-9_.-]+)@([da-z.-]+).([a-z.]{2,6})$/;
        var email = $('input#email').val();
        if (email == "" || email == " ") {
            $('#err-email').fadeIn('slow');
            error = true;
        } else if (!email_compare.test(email)) {
            $('#err-emailvld').fadeIn('slow');
            error = true;
        }
        if (error == true) {
            $('#err-form').slideDown('slow');
            return false;
        }
        var data_string = $('#ajax-form').serialize();
        alert(data_string);
        $.ajax({
            type: "POST",
            url: $('#ajax-form').attr('action'),
            data: data_string,
            timeout: 6000,
            error: function(request, error) {
                if (error == "timeout") {
                    $('#err-timedout').slideDown('slow');
                } else {
                    $('#err-state').slideDown('slow');
                    $("#err-state").html('An error occurred: ' + error + '');
                }
            },
            success: function() {
                $('#ajax-form').slideUp('slow');
                $('#ajaxsuccess').slideDown('slow');
            }
        });
        return false;
    });
});
