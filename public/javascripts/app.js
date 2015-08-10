var NewGroup = React.createClass({
    onClick: function() {

    },

    render: function() {
        return (
            <a id="new-group" onClick={self.onClick}>New group</a>
        );
    }
});

var GroupPane = React.createClass({
    getInitialState: function () {
        return {
            groups: global.groups,
            selectedGroup: undefined
        };
    },

    componentWillMount: function() {
        var self = this;
        $(document).on("newGroup", function (event, newGroup) {
            if (self.state.groups.filter(function (g) {
                    return g.id == newGroup.id
                }).length == 0) {
                self.setState(React.addons.update(self.state, {
                    groups: {
                        $push: [newGroup]
                    }
                }));
            }
        })
    },

    componentDidMount: function () {
        $(document).trigger("selectedGroup", this.state.selectedGroup);
    },

    onClick: function (group) {
        var selectedGroup = group ? group : undefined;
        this.setState({selectedGroup: selectedGroup});
        $(document).trigger("selectedGroup", selectedGroup);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.groups.map(function (group) {
            var groupClass = (self.state.selectedGroup && self.state.selectedGroup.id == group.id) ? "selected" : "";
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var allGroupsClass = !self.state.selectedGroup ? "selected" : "";

        return (
            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onClick.bind(self, undefined)}>
                    <span>All groups</span></li>
                {groupItems}
                <NewGroup/>
            </ul>
        );
    }
});

var SideBar = React.createClass({
    render: function () {
        return (
            <div id="side-bar">
                <div id="header">JetBrains</div>
                <GroupPane/>
            </div>
        );
    }
});

var SearchPane = React.createClass({
    render: function () {
        return (
            <div id="search-pane" className="form-inline">
                <div className="btn-group">
                    <input id="search" type="text" className="search-query"
                           placeholder="Search people, groups, topics, and messages"
                           autoComplete="off"/></div>
            </div>
        );
    }
});

var NewTopicPane = React.createClass({
    getInitialState: function () {
        return {
            enabled: false,
            selected: true
        };
    },

    onClick: function () {
        if (this.state.enabled) {
            $(document).trigger("selectedTopic", undefined);
        }
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedGroup", function (event, selectedGroup) {
            self.setState({enabled: selectedGroup !== undefined});
        });
        $(document).on("selectedTopic", function (event, selectedTopic) {
            self.setState({selected: self.state.enabled && selectedTopic === undefined});
        });
    },

    render: function () {
        var newTopicClass = ((this.state.enabled ? "enabled" : "") + " " + (this.state.selected ? "selected" : "")).trim();
        return (
            <div id="new-topic-pane">
                <a id="new-topic" className={newTopicClass}
                   onClick={this.onClick}>
                    <span id="plus"/>
                    <span>New topic</span>
                </a>
            </div>
        );
    }
});

var TopicItem = React.createClass({
    onClick: function (topic) {
        $(document).trigger("selectedTopic", topic);
    },

    render: function () {
        var self = this;
        var topicClass = self.props.selected ? "selected" : "";
        var prettyDate = $.format.prettyDate(new Date(self.props.topic.date));
        var groupRef;
        if (self.props.showGroup) {
            groupRef = <span>in #<span
                className="group">{self.props.topic.group.name}</span></span>
        }
        return (
            <li data-topic={self.props.topic.id} className={topicClass}
                onClick={self.onClick.bind(self, self.props.topic)}>
                <div className="text">{self.props.topic.text}</div>
                <div className="info">
                    <span className="author">{self.props.topic.user.name}</span>
                    &nbsp;
                    {groupRef}
                    &nbsp;
                        <span className="pretty date"
                              data-date={self.props.topic.date}>{prettyDate}</span>
                </div>
            </li>
        );
    }
});

var TopicPane = React.createClass({
    getInitialState: function () {
        return {
            topics: [],
            selectedTopic: undefined,
            selectedGroup: undefined
        };
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedGroup", function (event, selectedGroup) {
            self.setState({selectedGroup: selectedGroup});
            $.ajax({
                type: "GET",
                url: "/json/user/" + global.user.id + "/topics" +
                (selectedGroup ? "/" + selectedGroup.id : ""),
                success: function (topics) {
                    self.setState({topics: topics});
                    if (topics.length > 0) {
                        $(document).trigger("selectedTopic", topics[0].topic);
                    } else {
                        $(document).trigger("selectedTopic", undefined);
                    }
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        });
        $(document).on("selectedTopic", function (event, selectedTopic) {
            self.setState({selectedTopic: selectedTopic});
        });
        $(document).on("newTopic", function (event, newTopic) {
            if ((!self.state.selectedGroup || self.state.selectedGroup.id ==
                newTopic.group.id) && self.state.topics.filter(function (m) {
                    return m.id == newTopic.id
                }).length == 0) {
                self.setState(React.addons.update(self.state, {
                    topics: {
                        $splice: [[0, 0, {topic: newTopic}]]
                    }
                }));
                $(document).trigger("selectedTopic", newTopic);
            }
        });
    },

    render: function () {
        var self = this;
        var topicItems = self.state.topics.map(function (t) {
            return (
                <TopicItem topic={t.topic}
                           selected={self.state.selectedTopic &&
                           self.state.selectedTopic.id == t.topic.id}
                           showGroup={!self.state.selectedGroup}
                           key={t.topic.id}/>
            )
        });

        return (
            <ul id="topic-pane">
                {topicItems}
            </ul>
        );
    }
});

var TopicBar = React.createClass({
    render: function () {
        return (
            <div id="topic-bar">
                <SearchPane/>
                <NewTopicPane/>
                <TopicPane/>
            </div>
        );
    }
});

var MessageItem = React.createClass({
    render: function () {
        var self = this;
        var className = ("clearfix" + " " + (self.props.topic ? "topic" : "") + " " + (self.props.sameUser ? "same-user" : "")).trim();
        var avatar;
        var info;
        if (!self.props.sameUser) {
            var user = global.users.filter(function (u) {
                return u.id == self.props.message.user.id
            })[0];
            avatar = <img className="img avatar pull-left" src={user.avatar}/>;
            var prettyDate = $.format.prettyDate(new Date(self.props.message.date));
            info = <div className="info">
                <span className="author">{user.name}</span>
                &nbsp;
                <span className="pretty date"
                      data-date={self.props.message.date}>{prettyDate}</span>
            </div>;
        }
        return (
            <li className={className} data-user={self.props.message.user.id}>
                {avatar}
                <div className="details">
                    {info}
                    <div className="text">{self.props.message.text}</div>
                </div>
            </li>
        );
    }
});

var MessageBar = React.createClass({
    getInitialState: function () {
        return {
            messages: [],
            selectedGroup: undefined,
            selectedTopic: undefined
        };
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedGroup", function (event, selectedGroup) {
            self.setState({selectedGroup: selectedGroup});
        });
        $(document).on("selectedTopic", function (event, selectedTopic) {
            React.findDOMNode(self.refs.input).focus();
            if (selectedTopic) {
                $.ajax({
                    type: "GET",
                    url: "/json/user/" + global.user.id + "/messages/" + selectedTopic.id,
                    success: function (messages) {
                        self.setState({
                            messages: messages,
                            selectedTopic: selectedTopic
                        });
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                })
            } else {
                self.setState({messages: [], selectedTopic: undefined});
            }
        });
        $(document).on("newMessage", function (event, newMessage) {
            if (self.state.selectedTopic && self.state.selectedTopic.id ==
                newMessage.topicId && self.state.messages.filter(function (m) {
                    return m.text == newMessage.text
                }).length == 0) {
                // TODO: Preserve message order
                self.setState(React.addons.update(self.state, {
                    messages: {
                        $push: [newMessage]
                    }
                }));
            }
        });
    },

    onInputKeyPress: function (event) {
        var self = this;
        var input = React.findDOMNode(self.refs.input);
        if (event.which == 13 && input.value.trim()) {
            var data = {
                "user": {
                    "id": global.user.id,
                    "name": global.user.name,
                    "avatar": global.user.avatar
                },
                "date": new Date().getTime(),
                "groupId": self.state.selectedTopic ? self.state.selectedTopic.group.id : self.state.selectedGroup.id,
                "text": input.value
            };
            if (self.state.selectedTopic) {
                data.topicId = self.state.selectedTopic.id;
                this.setState(React.addons.update(self.state, {
                    messages: {
                        $push: [data]
                    }
                }));
            }
            input.value = "";
            $.ajax({
                type: "POST",
                url: self.state.selectedTopic ? "/json/comment/add" : "/json/topic/add",
                data: JSON.stringify(data),
                contentType: "application/json",
                success: function (id) {
                    var newTopic = {
                        id: id,
                        group: {id: data.groupId},
                        text: data.text,
                        date: new Date().getTime(),
                        user: global.user
                    };
                    if (!self.state.selectedTopic) {
                        $(document).trigger("newTopic", newTopic);
                    }
                },
                fail: function (e) {
                    console.error(e);
                }
            });
            event.preventDefault();
        }
    },

    render: function () {
        var self = this;
        var userId;
        var topic = true;
        var sameUser = false;
        var messageItems = self.state.messages.map(function (message, index) {
            if (index == 0) {
                userId = message.user.id;
            } else {
                if (message.user.id != userId) {
                    sameUser = false;
                    topic = false;
                    userId = message.user.id;
                } else {
                    sameUser = true;
                }
            }
            var key = topic ? message.topicId + "_" + message.id : message.id;
            return (
                <MessageItem message={message} topic={topic} sameUser={sameUser}
                             key={key}/>
            )
        });
        var inputPlaceHolder = self.state.selectedTopic ?
            "Message..." : "Topic...";

        return (
            <div id="message-bar">
                <div id="message-pane">
                    <div id="message-roll">
                        {messageItems}
                    </div>
                </div>
                <textarea id="input" ref="input" autoComplete="off"
                          placeholder={inputPlaceHolder} className="enabled"
                          onKeyPress={self.onInputKeyPress}
                />
            </div>
        );
    }
});

var App = React.createClass({
    openSocket: function() {
        var self = this;
        var socket = new WebSocket(global.webSocketUrl);
        socket.onmessage = function (message) {
            if (message.data != JSON.stringify("Tack")) {
                var data = JSON.parse(message.data);
                if (data.topicId) {
                    $(document).trigger("newMessage", data);
                } else if (data.newGroup) {
                    $(document).trigger("newGroup", data.newGroup);
                } else if (data.newUser) {
                    global.users.push(data.newUser);
                    $(document).trigger("newUser", data);
                } else if (!data.toUser) {
                    $(document).trigger("newTopic", data);
                } else {
                    $(document).trigger("newMessage", data);
                }
            }
        };
        socket.onopen = function () {
            setInterval(function () {
                socket.send(JSON.stringify("Tick"));
            }, 10000);
        };
        socket.onclose = function (event) {
            console.error("Reopenning websocket");
            this.openSocket();
        };
        socket.onerror = function (error) {
            console.error("websocket error: " + error);
        };
    },

    componentWillMount: function() {
        if (window.WebSocket) {
            this.openSocket();
        }
    },

    render: function () {
        return (
            <div>
                <SideBar/>
                <TopicBar/>
                <MessageBar/>
            </div>
        );
    }
});

React.render(<App/>, document.getElementById("app"));