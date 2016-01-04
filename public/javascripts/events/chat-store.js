import Reflux from 'reflux';
import ChatActions from './chat-actions';

var ChatStore = Reflux.createStore({
    listenables: [ChatActions],

    init: function () {
        this.state = this.getInitialState();
        if (this.state.displaySettings) {
            this.onShowIntegrations(true);
        } else if (this.state.selectedUser) {
            this.onSelectUser(this.state.selectedUser);
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
            users: _global.users.filter(function (u) {
                return u.id !== _global.user.id
            }),
            integrations: _global.integrations,
            displaySettings: _global.displaySettings,
            groups: _global.groups,
            topics: [],
            messages: [],
            selectedGroup: _global.selectedGroupId ? _global.groups.filter(function (g) {
                return g.id == _global.selectedGroupId
            })[0] : undefined,
            selectedTopic: _global.selectedTopic,
            selectedUser: _global.selectedUserId ? _global.users.filter(function (u) {
                return u.id == _global.selectedUserId
            })[0] : undefined
        }
    },

    onSelectGroup: function (group) {
        var self = this;
        this.state.selectedGroup = group;
        this.state.selectedUser = undefined;
        this.state.displaySettings = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/topics" +
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
        this.state.displaySettings = undefined;
        if (topic) {
            $.ajax({
                context: this,
                type: "GET",
                url: "/json/user/" + _global.user.id + "/messages/" + topic.id,
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
            window.history.replaceState(this.state, window.title, this.state.selectedGroup ? ("?groupId=" + this.state.selectedGroup.id) : "/");
        }
    },

    onSelectUser: function (user) {
        var self = this;
        this.state.selectedUser = user;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;
        this.state.displaySettings = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/direct/" + user.id,
            success: function (messages) {
                self.state.messages = messages;
                self.trigger(self.state);
                window.history.replaceState(this.state, window.title, "?userId=" + self.state.selectedUser.id);
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
        this.state.displaySettings = true;
        this.state.selectedUser = undefined;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;

        if (!initial) {
            this.trigger(this.state);
            window.history.replaceState(this.state, window.title, "?settings=true");
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
            _global.user.id == message.user.id || this.state.selectedUser.id == message.user.id &&
            _global.user.id == message.toUser.id)) && this.state.messages.filter(function (m) {
                return m.text == message.text
            }).length == 0) {
            // TODO: Preserve message order
            this.state.messages.push(message);
            this.trigger(this.state);
        }
    }
});

export default ChatStore;