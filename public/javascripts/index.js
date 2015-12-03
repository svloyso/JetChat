var ChatActions = Reflux.createActions([
    'selectGroup',
    'selectTopic',
    'selectUser',
    'newGroup',
    'newUser',
    'newTopic',
    'newMessage',
    'showIntegrations'
]);

var ChatStore = Reflux.createStore({
    listenables: [ChatActions],

    init: function () {
        this.state = this.getInitialState();
        if (this.state.displayIntegrations) {
            this.onShowIntegrations(true);
        } else {
            this.onSelectGroup(this.state.selectedGroup);
        }
        // var self = this;
        // TODO: Re-fetch groups, topics, etc
        /*window.addEventListener('popstate', function (e) {
            self.trigger(e.state);
        }, false);*/
    },

    getInitialState: function () {
        return {
            users: global.users.filter(function (u) {
                return u.id !== global.user.id
            }),
            integrations: global.integrations,
            displayIntegrations: global.displayIntegrations,
            groups: global.groups,
            topics: [],
            messages: [],
            selectedGroup: global.selectedGroupId ? global.groups.filter(function (g) {
                return g.id == global.selectedGroupId
            })[0] : undefined,
            selectedTopic: global.selectedTopic,
            selectedUser: global.selectedUserId ? global.users.filter(function (u) {
                return u.id == global.selectedUserId
            })[0] : undefined
        }
    },

    onSelectGroup: function (group) {
        var self = this;
        this.state.selectedGroup = group;
        this.state.selectedUser = undefined;
        this.state.displayIntegrations = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + global.user.id + "/topics" +
            (group ? "/" + group.id : ""),
            success: function (topics) {
                self.state.topics = topics;
                if (topics.length > 0) {
                    self.onSelectTopic(topics[0].topic);
                } else {
                    self.onSelectTopic();
                }
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectTopic: function (topic) {
        var self = this;
        this.state.selectedTopic = topic;
        this.state.selectedUser = undefined;
        this.state.displayIntegrations = undefined;
        if (topic) {
            $.ajax({
                context: this,
                type: "GET",
                url: "/json/user/" + global.user.id + "/messages/" + topic.id,
                success: function (messages) {
                    self.state.messages = messages;
                    self.trigger(self.state);
                    // TODO: pushState
                    window.history.replaceState(self.state, window.title,
                        self.state.selectedGroup ? ("?groupId=" + self.state.selectedGroup.id +
                            "&topicId=" + self.state.selectedTopic.id
                        ) : "?topicId=" + self.state.selectedTopic.id);
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        } else {
            this.state.messages = [];
            this.trigger(this.state);
            // TODO: pushState
            window.history.replaceState(this.state, window.title, this.state.selectedGroup ? ("?groupId=" + this.state.selectedGroup.id) : "");
        }
    },

    onSelectUser: function (user) {
        var self = this;
        this.state.selectedUser = user;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;
        this.state.displayIntegrations = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + global.user.id + "/direct/" + user.id,
            success: function (messages) {
                self.state.messages = messages;
                self.trigger(self.state);
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onNewGroup: function (group, select) {
        if (this.state.groups.filter(function (g) {
                return g.id == group.id
            }).length == 0) {
            this.state.groups.push(group);
            if (select) {
                this.onSelectGroup(group);
            } else {
                this.trigger(this.state);
            }
        } else if (select) {
            this.onSelectGroup(group);
        }
    },

    onNewUser: function (user) {
        this.state.users.push(user);
        this.trigger(this.state);
    },

    onEnableIntegration: function (integration) {
        this.state.integrations[integration] = true;
        this.trigger(this.state);
    },

    onDisableIntegration: function (integration) {
        delete this.state.integrations[integration];
        this.trigger(this.state);
    },

    onShowIntegrations: function (initial) {
        var self = this;
        this.state.displayIntegrations = true;
        this.state.selectedUser = undefined;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;

        if (!initial) {
            setInterval(function(){ self.trigger(self.state); }, 0); // Otherwise it doesn't work for some reason
            window.history.replaceState(this.state, window.title, "?integrations=true");
        }
    },

    onNewTopic: function (topic, select) {
        if ((!this.state.selectedGroup || this.state.selectedGroup.id ==
            topic.group.id) && this.state.topics.filter(function (m) {
                return m.topic.id == topic.id
            }).length == 0) {
            this.state.topics.splice(0, 0, {topic: topic});
            if (select) {
                this.onSelectTopic(topic);
            } else {
                this.trigger(this.state);
            }
        } else if (select) {
            this.onSelectTopic(topic);
        }
    },

    onNewMessage: function (message) {
        if ((this.state.selectedTopic && this.state.selectedTopic.id ==
            message.topicId || this.state.selectedUser && (this.state.selectedUser.id == message.toUser.id &&
            global.user.id == message.user.id || this.state.selectedUser.id == message.user.id &&
            global.user.id == message.toUser.id)) && this.state.messages.filter(function (m) {
                return m.text == message.text
            }).length == 0) {
            // TODO: Preserve message order
            this.state.messages.push(message);
            this.trigger(this.state);
        }
    }
});

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
                    ChatActions.newGroup(group, true);
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
    componentDidMount: function () {
        $(this.getDOMNode()).popover({
            react: true,
            content: <NewGroupDialog/>
        }).on("shown.bs.popover", function () {
            $(".new-group-popover .group-name").focus();
        });
    },

    render: function () {
        return (
            <a id="new-group">New group</a>
        );
    }
});

var IntegrationsButton = React.createClass({
    onClick: function() {
        ChatActions.showIntegrations();
    },

    render: function() {
        var self = this;
        var className = self.props.displayIntegrations ? "selected" : "";
        return (
            <li id="integrations-button" onClick={self.onClick.bind(self, undefined)} className={className}>Integrations</li>
        );
    }
});

var GroupPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onGroupClick: function (group) {
        ChatActions.selectGroup(group);
    },

    onUserClick: function (user) {
        ChatActions.selectUser(user);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.store.groups.map(function (group) {
            var groupClass = (self.state.store.selectedGroup && self.state.store.selectedGroup.id == group.id) ? "selected" : "";
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onGroupClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var userItems = self.state.store.users.map(function (user) {
            var userClass = (self.state.store.selectedUser && self.state.store.selectedUser.id == user.id) ? "selected" : "";
            return (
                <li data-user={user.id} className={userClass}
                    onClick={self.onUserClick.bind(self, user)} key={user.id}>
                    <span>{user.name}</span>
                </li>
            );
        });

        var allGroupsClass = !self.state.store.selectedGroup && !self.state.store.selectedUser &&
            !self.state.store.displayIntegrations? "selected" : "";

        return (

            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onGroupClick.bind(self, undefined)}>
                    <span>All groups</span></li>
                {groupItems}
                <NewGroupButton/>
                <IntegrationsButton displayIntegrations={self.state.store.displayIntegrations}/>
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
    mixins: [Reflux.connect(ChatStore, 'store')],

    onClick: function () {
        if (this.state.store.selectedGroup) {
            ChatActions.selectTopic();
        }
    },

    render: function () {
        var newTopicClass = ((this.state.store.selectedGroup ? "enabled" : "") + " " + (!this.state.store.selectedTopic ? "selected" : "")).trim();
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
        ChatActions.selectTopic(topic);
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
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function () {
        var self = this;
        var topicItems = self.state.store.topics.map(function (t) {
            return (
                <TopicItem topic={t.topic}
                           selected={self.state.store.selectedTopic &&
                           self.state.store.selectedTopic.id == t.topic.id}
                           showGroup={!self.state.store.selectedGroup}
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
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function () {
        console.log("TopicBar state = " + this.state.store.topics);
        return (
            <div id="topic-bar" style={{display: this.state.store.selectedUser ? "none" : ""}}>
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
            avatar = <img className="img avatar pull-left" src={self.props.message.user.avatar}/>;
            var prettyDate = $.format.prettyDate(new Date(self.props.message.date));
            info = <div className="info">
                <span className="author">{self.props.message.user.name}</span>
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
                        if (typeof el === "string")
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
    mixins: [Reflux.connect(ChatStore, 'store')],

    componentDidUpdate: function () {
        var messageRoll = $(React.findDOMNode(this.refs.messageRoll));
        messageRoll.scrollTop(messageRoll[0].scrollHeight);
        React.findDOMNode(this.refs.input).focus();
    },

    onInputKeyPress: function (event) {
        var self = this;
        var input = React.findDOMNode(self.refs.input);
        if (event.which == 13 && input.value.trim()) {
            if (self.state.store.selectedUser) {
                var toUser = self.state.store.users.filter(function (u) {
                    return u.id == self.state.store.selectedUser.id
                })[0];
                var newDirectMessage = {
                    "user": global.user,
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
                        ChatActions.newMessage(newDirectMessage);
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                });
            } else {
                var newMessage = {
                    "user": global.user,
                    "date": new Date().getTime(),
                    "groupId": self.state.store.selectedTopic ? self.state.store.selectedTopic.group.id : self.state.store.selectedGroup.id,
                    "text": input.value
                };
                if (self.state.store.selectedTopic) {
                    newMessage.topicId = self.state.store.selectedTopic.id;
                }
                $.ajax({
                    type: "POST",
                    url: self.state.store.selectedTopic ? "/json/comment/add" : "/json/topic/add",
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
                            ChatActions.newMessage(m);
                        } else {
                            ChatActions.newTopic(m, true);
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
        var topic = self.state.store.selectedUser === undefined;
        var sameUser = false;
        var messageItems = self.state.store.messages.map(function (message, index) {
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
            var key = message.topicId ? message.topicId + "_" + message.id : message.id;
            return (
                <MessageItem message={message} topic={topic} sameUser={sameUser}
                             key={key}/>
            )
        });
        var inputPlaceHolder = self.state.store.selectedTopic ?
            "Message..." : "Topic...";
        var userHeader;
        if (self.state.store.selectedUser) {
            userHeader = <div id="message-roll-header">
                <li className="clearfix topic">
                    <img className="img avatar pull-left" src={self.state.store.selectedUser.avatar}/>
                    <div className="details">
                        <div className="info">
                            <span className="user">{self.state.store.selectedUser.name}</span>
                        </div>
                    </div>
                </li>
            </div>
        }
        return (
            // TODO: Replace logic with className
            <div id="message-bar" style={{left: this.state.store.selectedUser ? "200px" : "550px"}}>
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

var IntegrationsPane = React.createClass({
    render: function() {
        return (
            <div id="integration-pane"></div>
        );
    }
});

var App = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    openSocket: function () {
        var self = this;
        var socket = new WebSocket(global.webSocketUrl);
        socket.onmessage = function (message) {
            if (message.data != JSON.stringify("Tack")) {
                var data = JSON.parse(message.data);
                if (data.topicId) {
                    ChatActions.newMessage(data);
                } else if (data.newGroup) {
                    ChatActions.newGroup(data.newGroup);
                } else if (data.newUser) {
                    // TODO newUser
                    ChatActions.newUser(data.newUser);
                } else if (data.enableIntegration) {
                    ChatActions.enableIntegration(data.enableIntegration);
                } else if (data.disableIntegration) {
                    ChatActions.enableIntegration(data.disableIntegration);
                } else if (!data.toUser) {
                    ChatActions.newTopic(data);
                } else {
                    ChatActions.newMessage(data);
                }
            }
        };
        socket.onopen = function () {
            setInterval(function () {
                socket.send(JSON.stringify("Tick"));
            }, 10000);
        };
        socket.onclose = function (event) {
            console.error();
            setTimeout(function () {
                console.log("Reopenning websocket...");
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
        console.log("App state = " + this.state.store.topics);
        if (this.state.store.displayIntegrations) {
            return (
                <div>
                    <SideBar/>
                    <IntegrationsPane/>
                </div>
            )
        } else {
            return (
                <div>
                    <SideBar/>
                    <TopicBar/>
                    <MessageBar/>
                </div>
            );
        }
    }
});

React.render(<App/>, document.getElementById("app"));