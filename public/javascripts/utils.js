import React from 'react';
import ReactDOM from 'react-dom';
import PrettyDate from 'pretty-date'

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
        $(this).text(PrettyDate.format(new Date(parseInt($(this).attr("data-date")))))
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

export var _topicsToMarkAsRead = [];
export var _messagesToMarkAsRead = [];
export var _directMessagesToMarkAsRead = [];

window.setInterval(function () {
    var topics = [], messages = [], directMessages = [];
    if (_topicsToMarkAsRead.length > 0) {
        topics = _topicsToMarkAsRead.splice(0, _topicsToMarkAsRead.length);
        topics.map(id => console.log("Marking topic as read: " + id));
    }
    if (_messagesToMarkAsRead.length > 0) {
        messages = _messagesToMarkAsRead.splice(0, _messagesToMarkAsRead.length);
        messages.map(id => console.log("Marking message as read: " + id));
    }
    if (_directMessagesToMarkAsRead.length > 0) {
        directMessages = _directMessagesToMarkAsRead.splice(0, _directMessagesToMarkAsRead.length);
        directMessages.map(id => console.log("Marking direct message as read: " + id));
    }
    if (topics.length > 0 || messages.length > 0 || directMessages.length > 0) {
        $.ajax({
            type: "POST",
            url: "/json/markAsRead",
            data: JSON.stringify({
                "userId": _global.user.id,
                "topicIds": topics,
                "messageIds": messages,
                "directMessageIds": directMessages
            }),
            contentType: "application/json",
            fail: function (e) {
                Log.error(e);
            }
        })
    }
}, 1000);

var enablePageVisiblity = function() {
    var hidden = "hidden";

    // Standards:
    if (hidden in document)
        document.addEventListener("visibilitychange", onchange);
    else if ((hidden = "mozHidden") in document)
        document.addEventListener("mozvisibilitychange", onchange);
    else if ((hidden = "webkitHidden") in document)
        document.addEventListener("webkitvisibilitychange", onchange);
    else if ((hidden = "msHidden") in document)
        document.addEventListener("msvisibilitychange", onchange);
    // IE 9 and lower:
    else if ("onfocusin" in document)
        document.onfocusin = document.onfocusout = onchange;
    // All others:
    else
        window.onpageshow = window.onpagehide
            = window.onfocus = window.onblur = onchange;

    function onchange (evt) {
        var v = "visible", h = "hidden",
            evtMap = {
                focus:v, focusin:v, pageshow:v, blur:h, focusout:h, pagehide:h
            };

        evt = evt || window.event;
        if (evt.type in evtMap)
            document.body.className = evtMap[evt.type];
        else
            document.body.className = this[hidden] ? "hidden" : "visible";
    }

    // set the initial state (but only if browser supports the Page Visibility API)
    if( document[hidden] !== undefined )
        onchange({type: document[hidden] ? "blur" : "focus"});
};

$(document).ready(e =>
    enablePageVisiblity()
);