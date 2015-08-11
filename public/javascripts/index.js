var NewGroupDialog = React.createClass({
    createGroup: function () {
        var groupName = React.findDOMNode(this.refs.groupName).value.trim();
        if (groupName && !/\s/.test(groupName)) {
            $.ajax({
                type: "POST",
                url: "/json/group/add",
                data: JSON.stringify(groupName),
                contentType: "application/json",
                success: function (group) {
                    $(document).trigger("newGroup", group);
                    $(document).trigger("selectedGroup", group);
                },
                fail: function (e) {
                    console.error(e);
                }
            });
            $('#new-group').each(function () {
                $(this).popover('hide');
            });
            React.findDOMNode(this.refs.groupName).value = "";
        }
    },

    onKeyPress: function (event) {
        if (event.which == 13) {
            this.createGroup();
        }
    },

    render: function () {
        return (
            <div className="new-group-popover">
                <input ref="groupName" type="text" className="group-name" placeholder="Name..."
                       onKeyPress={this.onKeyPress}/>
                <div className="group-label">Must be 21 characters or less. No spaces or periods.</div>
                <a className='btn btn-default btn-sm' onClick={this.createGroup}>Create group</a>
            </div>
        );
    }
});

var NewGroupButton = React.createClass({
    onClick: function () {
    },

    componentDidMount: function () {
        var self = this;
        $(this.getDOMNode()).popover({
            react: true,
            content: <NewGroupDialog/>
        }).on('shown.bs.popover', function () {
            $(".new-group-popover .group-name").focus();
        });
    },

    render: function () {
        return (
            <a id="new-group" onClick={self.onClick}>New group</a>
        );
    }
});

var GroupPane = React.createClass({
    getInitialState: function () {
        return {
            groups: global.groups,
            users: global.users.filter(function (u) {
                return u.id != global.user.id
            }),
            selectedGroup: undefined,
            selectedUser: undefined
        };
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("newGroup", function (event, group) {
            if (self.state.groups.filter(function (g) {
                    return g.id == group.id
                }).length == 0) {
                global.groups.push(group);
                self.setState(React.addons.update(self.state, {
                    groups: {
                        $push: [group]
                    }
                }));
            }
        });
        $(document).on("newUser", function (event, user) {
            global.users.push(user);
            if (self.state.users.filter(function (u) {
                    return u.id == user.id
                }).length == 0) {
                self.setState(React.addons.update(self.state, {
                    users: {
                        $push: [user]
                    }
                }));
            }
        });
        $(document).on("selectedGroup", function (event, selectedGroup) {
            self.setState({selectedGroup: selectedGroup});
            self.setState({selectedUser: undefined});
        });
        $(document).on("selectedUser", function (event, selectedUser) {
            self.setState({selectedUser: selectedUser});
            self.setState({selectedGroup: undefined});
        });
    },

    componentDidMount: function () {
        $(document).trigger("selectedGroup", this.state.selectedGroup);
    },

    onGroupClick: function (group) {
        var selectedGroup = group ? group : undefined;
        $(document).trigger("selectedGroup", selectedGroup);
    },

    onUserClick: function (user) {
        $(document).trigger("selectedUser", user);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.groups.map(function (group) {
            var groupClass = (self.state.selectedGroup && self.state.selectedGroup.id == group.id) ? "selected" : "";
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onGroupClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var userItems = self.state.users.map(function (user) {
            var userClass = (self.state.selectedUser && self.state.selectedUser.id == user.id) ? "selected" : "";
            return (
                <li data-user={user.id} className={userClass}
                    onClick={self.onUserClick.bind(self, user)} key={user.id}>
                    <span>{user.name}</span>
                </li>
            );
        });

        var allGroupsClass = !self.state.selectedGroup && !self.state.selectedUser ? "selected" : "";

        return (
            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onGroupClick.bind(self, undefined)}>
                    <span>All groups</span></li>
                {groupItems}
                <NewGroupButton/>
                {userItems}
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
        $(document).on("selectedGroup", function (event, group) {
            self.setState({selectedGroup: group});
            $.ajax({
                type: "GET",
                url: "/json/user/" + global.user.id + "/topics" +
                (group ? "/" + group.id : ""),
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
        $(document).on("selectedTopic", function (event, topic) {
            self.setState({selectedTopic: topic});
        });
        $(document).on("newTopic", function (event, newTopic) {
            if ((!self.state.selectedGroup || self.state.selectedGroup.id ==
                newTopic.group.id) && self.state.topics.filter(function (m) {
                    return m.topic.id == newTopic.id
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
    getInitialState: function () {
        return {
            selectedUser: undefined
        };
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedUser", function (event, user) {
            self.setState({selectedUser: user});
        });
        $(document).on("selectedGroup", function (event, group) {
            self.setState({selectedUser: undefined});
        });
    },

    render: function () {
        return (
            <div id="topic-bar" style={{display: this.state.selectedUser ? "none" : ""}}>
                <SearchPane/>
                <NewTopicPane/>
                <TopicPane/>
            </div>
        );
    }
});

var MessageItem = React.createClass({
    mixins: [
        ReactEmoji, ReactAutolink
    ],

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
                    <div className="text">{this.autolink(self.props.message.text).map(function (el) {
                        if (typeof el  === "string")
                            return self.emojify(el);
                        else
                            return el;
                    })}</div>
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
            selectedTopic: undefined,
            selectedUser: undefined
        };
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedGroup", function (event, group) {
            self.setState({selectedGroup: group, selectedUser: undefined});
        });
        $(document).on("selectedTopic", function (event, topic) {
            React.findDOMNode(self.refs.input).focus();
            if (topic) {
                $.ajax({
                    type: "GET",
                    url: "/json/user/" + global.user.id + "/messages/" + topic.id,
                    success: function (messages) {
                        self.setState({
                            messages: messages,
                            selectedTopic: topic,
                            selectedUser: undefined
                        });
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                })
            } else {
                self.setState({messages: [], selectedTopic: undefined, selectedUser: undefined});
            }
        });
        $(document).on("selectedUser", function (event, user) {
            React.findDOMNode(self.refs.input).focus();
            $.ajax({
                type: "GET",
                url: "/json/user/" + global.user.id + "/direct/" + user.id,
                success: function (messages) {
                    self.setState({
                        messages: messages,
                        selectedTopic: undefined,
                        selectedGroup: undefined,
                        selectedUser: user
                    });
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        });
        $(document).on("newMessage", function (event, newMessage) {
            if ((self.state.selectedTopic && self.state.selectedTopic.id ==
                newMessage.topicId || self.state.selectedUser && (self.state.selectedUser.id == newMessage.toUser.id &&
                global.user.id == newMessage.user.id || self.state.selectedUser.id == newMessage.user.id &&
                global.user.id == newMessage.toUser.id)) && self.state.messages.filter(function (m) {
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

    componentDidUpdate: function() {
        var messageRoll = $(React.findDOMNode(this.refs.messageRoll));
        messageRoll.scrollTop(messageRoll[0].scrollHeight);
    },

    onInputKeyPress: function (event) {
        var self = this;
        var input = React.findDOMNode(self.refs.input);
        if (event.which == 13 && input.value.trim()) {
            if (self.state.selectedUser) {
                var user = global.users.filter(function (u) { return u.id == global.user.id })[0];
                var toUser = global.users.filter(function (u) { return u.id == self.state.selectedUser.id })[0];
                var newDirectMessage = {
                    "user": user,
                    "toUser": toUser,
                    "date": new Date().getTime(),
                    "text": input.value
                };
                $.ajax({
                    type: "POST",
                    url: "/json/direct/add",
                    data: JSON.stringify(newDirectMessage),
                    contentType: "application/json",
                    success: function (id) {
                        newDirectMessage.id = id;
                        $(document).trigger("newMessage", newDirectMessage);
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                });
            } else {
                var newMessage = {
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
                    newMessage.topicId = self.state.selectedTopic.id;
                }
                $.ajax({
                    type: "POST",
                    url: self.state.selectedTopic ? "/json/comment/add" : "/json/topic/add",
                    data: JSON.stringify(newMessage),
                    contentType: "application/json",
                    success: function (id) {
                        // TODO: Send full object from server
                        var m = {
                            id: id,
                            group: {id: newMessage.groupId},
                            text: newMessage.text,
                            date: new Date().getTime(),
                            user: global.user
                        };
                        if (newMessage.topicId) {
                            m.topicId = newMessage.topicId;
                            $(document).trigger("newMessage", m);
                        } else {
                            $(document).trigger("newTopic", m);
                        }
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                });
            }
            input.value = "";
            event.preventDefault();
        }
    },

    render: function () {
        var self = this;
        var userId;
        var topic = self.state.selectedUser === undefined;
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
        var userHeader;
        if (self.state.selectedUser) {
            userHeader = <div id="message-roll-header">
                <li className="clearfix topic">
                    <img className="img avatar pull-left" src={self.state.selectedUser.avatar} />
                    <div className="details">
                        <div className="info">
                            <span className="user">{self.state.selectedUser.name}</span>
                        </div>
                    </div>
                </li>
            </div>
        }
        return (
            // TODO: Replace logic with className
            <div id="message-bar" style={{left: this.state.selectedUser ? "200px" : "550px"}}>
                <div id="message-pane">
                    <div id="message-roll" ref="messageRoll">
                        {messageItems}
                    </div>
                    {userHeader}
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
    openSocket: function () {
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
                    $(document).trigger("newUser", data.newUser);
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
            setTimeout(function () {
                self.openSocket();
            }, 1000);
        };
        socket.onerror = function (error) {
            console.error("websocket error: " + error);
        };
    },

    componentWillMount: function () {
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