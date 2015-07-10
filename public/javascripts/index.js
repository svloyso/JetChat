$(document).ready(function () {
    window.setInterval(function() {
        $("#message-bar").width($(window).width() - 200 - 350);
        $("#message-bar").height($(window).height());
        $("#side-bar").height($(window).height());
        $("#topic-bar").height($(window).height());
        $("#message-pane").height($(window).height() - 50);
    });

    var sideBar = $("<div id='side-bar'>");

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
    });
    allGroupItem.appendTo(groupPane);

    $.each(groups, function (group, count) {
        var groupItem = $("<li>").attr("data-group", group).append($("<span>").text(group));
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
        groupItem.appendTo(groupPane);
    });
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
    var input = $("<input id='input' type='text' autocomplete='off'>");
    input.keypress(function (e) {
        if (e.which == 13 && (newTopic || newMessage)) {
            var data = {
                "userId": userId,
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
        topicItem.append($("<span>").text(t.topic.text));
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
        var messageItem = $("<li>");
        messageItem.append($("<span>").text(m.text));
        messageItem.appendTo(messagePane);
    }

    function onGroupSelection() {
        selectedTopic = null;
        if (selectedGroup) {
            newTopicButton.addClass("enabled");
        } else {
            newTopicButton.removeClass("enabled");
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
                    newTopicButton.addClass("selected");
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
            input.focus();
        } else {
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
