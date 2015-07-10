$(document).ready(function () {
    if (window.WebSocket) {
        var webSocket = new WebSocket(webSocketUrl);
        webSocket.onmessage = function (message) {
            console.dir(message);
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

    var groupPane = $("<ul id='group-pane'>");
    var selectedGroup = null;
    var selectedTopic = null;

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
    $(document.body).append(groupPane);


    var newTopicButton = $("<a id='new-topic'>").text("New topic").click(function () {
        selectedTopic = null;
        onTopicSelection();
        input.attr("placeholder", "Topic…");
    });

    $(document.body).append(newTopicButton);

    var topicPane = $("<div id='topic-pane'>");

    var messagePane = $("<div id='message-pane'>");
    var input = $("<input id='input' type='text'>");

    onGroupSelection();
    onTopicSelection();
    $(document.body).append(topicPane);

    $(document.body).append(messagePane);
    $(document.body).append(input);

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
                    var topicItem = $("<li>").attr("data-group", t.topic.groupId).attr("data-topic", t.topic.id);
                    topicItem.append($("<span>").text(t.topic.text));
                    if (selectedTopic == null) {
                        topicItem.addClass("selected");
                        selectedTopic = t.topic.id;
                        onTopicSelection();
                    }
                    topicItem.click(function () {
                        $("#topic-pane").find("li").removeClass("selected");
                        $(this).addClass("selected");
                        selectedTopic = $(this).attr("data-topic");
                        onTopicSelection();
                    });
                    topicItem.appendTo(topicPane);
                });
                if (selectedTopic == null) {
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
            input.attr("placeholder", "Message…");
            $.ajax({
                type: "GET",
                url: "/json/user/" + userId + "/messages/" + selectedTopic,
                success: function (messages) {
                    messagePane.html("");
                    console.dir(messages);
                    messages.forEach(function (m) {
                        var messageItem = $("<li>");
                        messageItem.append($("<span>").text(m.text));
                        messageItem.appendTo(messagePane);

                    });
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        } else {
            messagePane.html("");
        }
        if (selectedGroup || selectedTopic) {
            input.addClass("enabled");
        } else {
            input.removeClass("enabled");
        }
    }
});
