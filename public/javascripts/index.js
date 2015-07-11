$(document).ready(function () {
    window.setInterval(function() {
        $("#message-bar").width($(window).width() - 200 - 350);
        $("#message-bar").height($(window).height());
        $("#side-bar").height($(window).height());
        $("#group-pane").height($(window).height() - 80);
        $("#topic-pane").height($(window).height() - 100);
        $("#message-pane").height($(window).height() - 100);
        $("#message-bar").find("#input").width($("#message-bar").width() - 85);
    });

    var sideBar = $("<div id='side-bar'>");

    sideBar.append($("<div id='header'>").text("JetBrains"));

    var groupPane = $("<ul id='group-pane'>");
    var selectedGroup = null;
    var selectedTopicGroup = null;
    var selectedTopic = null;
    var newTopic = false;
    var newMessage = false;

    var allGroupItem = $("<li id='all-chats'>").append($("<span>").text("All Chats"));
    if (selectedGroup == null) {
        allGroupItem.addClass("selected");
    }
    allGroupItem.click(function () {
        $("#group-pane").find("li").removeClass("selected");
        $(this).addClass("selected");
        selectedGroup = null;
        onGroupSelection();
        onTopicSelection();
    });
    allGroupItem.appendTo(groupPane);

    function addGroup(group, insert) {
        var groupItem = $("<li>").attr("data-group", group).append($("<span class='group-header'>").text("#")).append($("<span>").text(group));
        if (selectedGroup == group) {
            groupItem.addClass("selected");
            selectedGroup = group;
        }
        groupItem.click(function () {
            $("#group-pane").find("li").removeClass("selected");
            $(this).addClass("selected");
            selectedGroup = $(this).attr("data-group");
            onGroupSelection();
            onTopicSelection();
        });
        if (insert) {
            groupItem.insertBefore(groupPane.find("#new-group"));
        } else {
            groupItem.appendTo(groupPane);
        }
        return groupItem;
    }

    $.each(groups, function (group, count) {
        addGroup(group);
    });

    var isVisible = false;

    var hideAllPopovers = function() {
        $('#new-group').each(function() {
            $(this).popover('hide');
        });
    };

    var newGroupButton = $("<a id='new-group'>").text("New group").popover({
        html: true,
        trigger: 'manual',
        content: function() {
            var pane = $("<div class='new-group-popover'>");
            window.createGroup = function () {
                var groupId = $(".new-group-popover .group-name").val().trim();
                if (groupId && !/\s/.test(groupId)) {
                    $.ajax({
                        type: "POST",
                        url: "/json/group/add",
                        data: JSON.stringify(groupId),
                        contentType: "application/json",
                        success: function () {
                            hideAllPopovers();
                            isVisible = false;
                            var groupItem = addGroup(groupId, true);
                            $("#group-pane").find("li").removeClass("selected");
                            groupItem.addClass("selected");
                            selectedGroup = groupId;
                            onGroupSelection();
                            onTopicSelection();
                        },
                        fail: function (e) {
                            console.error(e);
                        }
                    })
                }
            };
            var groupInput = $("<input type='text' class='group-name' placeholder='Name…'>").attr("onkeypress", "if (event.keyCode == 13) createGroup()");
            var groupLabel = $("<div class='group-label'>").text("Must be 21 characters or less. No spaces or periods.");
            var button = $("<a class='btn btn-default btn-sm'>").text("Create group").attr("onclick", "createGroup()");
            pane.append(groupInput);
            pane.append(groupLabel);
            pane.append(button);
            return $("<div>").append(pane).html();
        }
    }).on('shown.bs.popover', function () {
        $(".new-group-popover .group-name").focus();
    }).on('click', function(e) {
        // if any other popovers are visible, hide them
        if(isVisible) {
            hideAllPopovers();
        }

        $(this).popover('show');

        // handle clicking on the popover itself
        $('.popover').off('click').on('click', function(e) {
            e.stopPropagation(); // prevent event for bubbling up => will not get caught with document.onclick
        });

        isVisible = true;
        e.stopPropagation();
    });

    $(document).on('click', function(e) {
        hideAllPopovers();
        isVisible = false;
    });

    groupPane.append(newGroupButton);
    sideBar.append(groupPane);
    $(document.body).append(sideBar);

    var topicBar = $("<div id='topic-bar'>");

    var newTopicPane = $("<div id='new-topic-pane'>");
    var searchPane = $("<div id='search-pane' class='form-inline'>")
        .append($("<div class='btn-group'>").append($("<input id='search' type='text' class='search-query' placeholder='Search people, groups and topics.&hellip;' autocomplete='off'>")));

    /*<form id="form-search" class="form-inline">
        <div class="btn-group">
        <input id="search" type="search" required class="form-control search-query" placeholder="Search" autocomplete="off">
        <span id="search-clear" class="glyphicon glyphicon-remove"></span>
        </div>
        </form>
            */
    var newTopicButton = $("<a id='new-topic'>").append($("<span id='plus'>")).append($("<span>").text("New topic")).click(function () {
        if ($(this).hasClass("enabled")) {
            selectedTopic = null;
            onTopicSelection();
            $("#topic-pane").find("li").removeClass("selected");
            $(this).addClass("selected");
            input.attr("placeholder", "Topic…");
            input.focus();
        }
    });

    newTopicPane.append(newTopicButton);
    topicBar.append(searchPane);
    topicBar.append(newTopicPane);

    var topicPane = $("<div id='topic-pane'>");

    var messageBar = $("<div id='message-bar'>");

    var messagePane = $("<div id='message-pane'>");
    var input = $("<textarea id='input' autocomplete='off'>");
    input.keypress(function (e) {
        if (e.which == 13 && (newTopic || newMessage)) {
            if (input.val().trim()) {
                var data = {
                    "user": {
                        "id": userId,
                        "name": userName,
                        "avatar": userAvatar
                    },
                    "date": new Date().getMilliseconds(),
                    "groupId": newTopic ? selectedGroup : selectedTopicGroup,
                    "text": input.val()
                };
                if (newMessage) {
                    data["topicId"] = selectedTopic;
                    addMessage(data);
                }
                $.ajax({
                    type: "POST",
                    url: newTopic ? "/json/topic/add" : "/json/comment/add",
                    data: JSON.stringify(data),
                    contentType: "application/json",
                    success: function (id) {
                        if (newTopic) {
                            addTopic({
                                topic: {
                                    id: id,
                                    userId: userId,
                                    groupId: data.groupId,
                                    text: data.text,
                                    date: new Date().getMilliseconds(),
                                    user: {
                                        id: userId,
                                        name: userName
                                    }
                                }
                            });
                            selectedGroup = null;
                            selectedTopic = id;
                            selectedTopicGroup = data.groupId;
                            onTopicSelection();
                        }
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                });
                input.val("");
            }
            e.preventDefault();
        }
    });

    onGroupSelection();
    onTopicSelection();
    topicBar.append(topicPane);
    $(document.body).append(topicBar);

    messageBar.append(messagePane);
    messageBar.append(input);
    $(document.body).append(messageBar);

    var addedTopics = {};

    function addTopic(t, prepend) {
        addedTopics[t.topic.id] = true;
        var topicItem = $("<li>").attr("data-group", t.topic.groupId).attr("data-topic", t.topic.id);
        topicItem.append($("<div class='text'>").text(t.topic.text));
        var info = $("<div class='info'>").append($("<span class='author'>").text(t.topic.user.name)).append(" in #")
            .append($("<span class='group'>").text(t.topic.groupId)).append("&nbsp;&nbsp;").append($("<span class='pretty date'>").
                text($.format.prettyDate(t.topic.date)).attr("data-date", t.topic.date));
        topicItem.append(info);
        if (selectedTopic == null) {
            topicItem.addClass("selected");
            selectedTopic = t.topic.id;
            selectedTopicGroup = t.topic.groupId;
            onTopicSelection();
        }
        topicItem.click(function () {
            $("#topic-pane").find("li").removeClass("selected");
            $(this).addClass("selected");
            selectedTopic = parseInt($(this).attr("data-topic"));
            selectedTopicGroup = $(this).attr("data-group");
            onTopicSelection();
        });
        if (prepend) {
            topicItem.prependTo(topicPane);
        } else {
            topicItem.appendTo(topicPane);
        }
    }

    var addedMessages = {};

    function addMessage(m) {
        addedMessages[m.text] = true;
        var sameUser = false;
        var sameUserTopic = false;
        if (!messagePane.is(':empty')) {
            var lastItem = messagePane.children().last();
            if (parseInt(lastItem.attr("data-user")) == m.user.id) {
                sameUser = true;
                sameUserTopic = lastItem.hasClass("topic")
            }
        }
        var messageItem = $("<li class='clearfix'>").attr("data-user", m.user.id);
        if (!m.topicId || sameUserTopic) {
            messageItem.addClass("topic");
        }
        if (!sameUser) {
            messageItem.append($("<img class='img avatar pull-left'>").attr("src", m.user.avatar));
        } else {
            messageItem.addClass("same-user");
        }
        var info = $("<div class='info'>")
            .append($("<span class='author'>").text(m.user.name));
        info.append(" in #").append($($("<span class='group'>").text(m.groupId)));
        info.append("&nbsp;&nbsp;").append($("<span class='pretty date'>").
            text($.format.prettyDate(m.date)).attr("data-date", m.date));

        var details = $("<div class='details'>");
        if (!sameUser) {
            details.append(info);
        }
        details.append($("<div class='text'>").text(m.text));
        var message = $("<div class='message'>")
            .append(details);
        messageItem.append(message);
        messageItem.appendTo(messagePane);
        messagePane.scrollTop(messagePane[0].scrollHeight);
    }

    function onGroupSelection() {
        selectedTopic = null;
        if (selectedGroup) {
            newTopicButton.addClass("enabled");
        } else {
            newTopicButton.removeClass("enabled");
            newTopicButton.removeClass("selected");
        }
        $.ajax({
            type: "GET",
            url: "/json/user/" + userId + "/topics" + (selectedGroup ? "/" + selectedGroup : ""),
            success: function (topics) {
                topicPane.html("");
                topics.forEach(function (t) {
                    addTopic(t)
                });
                if (selectedTopic == null) {
                    if (newTopic) {
                        newTopicButton.addClass("selected");
                    }
                    input.attr("placeholder", "Topic…");
                }
            },
            fail: function (e) {
                console.error(e);
            }
        })
    }

    function onTopicSelection() {
        if (selectedTopic) {
            newTopicButton.removeClass("selected");
            input.attr("placeholder", "Message…");
            $.ajax({
                type: "GET",
                url: "/json/user/" + userId + "/messages/" + selectedTopic,
                success: function (messages) {
                    messagePane.html("");
                    messages.forEach(function (m) {
                        addMessage(m);
                    });
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        } else {
            messagePane.html("");
        }
        if (selectedGroup) {
            if (selectedTopic) {
                newTopic = false;
                newMessage = true;
            } else {
                newTopic = true;
                newMessage = false;
            }
        } else {
            if (selectedTopic) {
                newTopic = false;
                newMessage = true;
            } else {
                newTopic = false;
                newMessage = false;
            }
        }
        if (selectedGroup || selectedTopic) {
            input.addClass("enabled");
            input.show();
            input.focus();
        } else {
            input.hide();
            input.removeClass("enabled");
        }
    }

    if (window.WebSocket) {
        var webSocket = new WebSocket(webSocketUrl);
        webSocket.onmessage = function (message) {
            if (message.data != JSON.stringify("Tack")) {
                var d = JSON.parse(message.data);
                if (d.topicId) {
                    if (selectedTopic == d.topicId && !addedMessages[d.text]) {
                        addMessage(d);
                    }
                } else {
                    if (selectedGroup == d.groupId && !addedTopics[d.id]) {
                        addTopic({topic: d}, true);
                    } else if (!selectedGroup || (selectedTopic && selectedTopicGroup == d.groupId)) {
                        addTopic({topic: d}, true);
                    }
                }
            }
        };
        webSocket.onopen = function () {
            setInterval(function () {
                webSocket.send(JSON.stringify("Tick"));
            }, 10000);
        };
        webSocket.onclose = function (event) {
            console.error("Websocket is closed: " + event)
        };
        webSocket.onerror = function (error) {
            console.error("Websocket error: " + error);
        };
    }
});

window.setInterval(function() {
    $(".pretty").map(function() {
        $(this).text($.format.prettyDate(parseInt($(this).attr("data-date"))))
    })
}, 1000 * 60);
