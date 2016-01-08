import React from 'react';
import ReactDOM from 'react-dom';
var prettydate = require("pretty-date");

// Patch Bootstrap popover to take a React component instead of a
// plain HTML string
$.extend($.fn.popover.Constructor.DEFAULTS, {react: false});
var oldSetContent = $.fn.popover.Constructor.prototype.setContent;
$.fn.popover.Constructor.prototype.setContent = function () {
    if (!this.options.react) {
        return oldSetContent.call(this);
    }

    var $tip = this.tip();
    var title = this.getTitle();
    var content = this.getContent();

    $tip.removeClass('fade top bottom left right in');

    // If we've already rendered, there's no need to render again
    if (!$tip.find('.popover-content').html()) {
        // Render title, if any
        var $title = $tip.find('.popover-title');
        if (title) {
            ReactDOM.render(title, $title[0]);
        } else {
            $title.hide();
        }

        ReactDOM.render(content, $tip.find('.popover-content')[0]);
    }
};

// Other utils

var __urlRegex = /(\b(https?|ftp|file):\/\/[-A-Z0-9+&@#\/%?=~_|!:,.;]*[-A-Z0-9+&@#\/%=~_|])/ig;

window.setInterval(function () {
    $(".imagify").each(function (i, a) {
        var _a = a;
        var img = $("<img>");
        img.load(function () {
            $(_a).replaceWith($("<a target='_blank'>").append($(img)).attr("href", _a.href));
        }).error(function () {
            $(_a).removeClass("imagify");
        }).attr("src", _a.href).addClass("").
            attr("class", "preview");
    });
}, 100);

window.setInterval(function () {
    $(".pretty").map(function () {
        $(this).text(prettydate.format(new Date(parseInt($(this).attr("data-date")))))
    })
}, 1000 * 60);

window.setInterval(function() {
    $("#message-bar").width($(window).width() - 200 - 350);
    $("#message-bar").height($(window).height());
    $("#side-bar").height($(window).height());
    $("#group-pane").height($(window).height() - 80);
    $("#topic-pane").height($(window).height() - 100);
    $("#message-pane").height($(window).height() - 100);
    $("#message-roll").css({maxHeight: ($(window).height() - 95) + "px"});
    $("#message-bar").find("#input").width($("#message-bar").width() - 85);
});

Object.defineProperty(Array.prototype, 'group', {
    enumerable: false,
    value: function (key) {
        var map = {};
        this.forEach(function (e) {
            var k = key(e);
            map[k] = map[k] || [];
            map[k].push(e);
        });
        return Object.keys(map).map(function (k) {
            return {key: k, data: map[k]};
        });
    }
});
