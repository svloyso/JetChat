import Reflux from 'reflux';
import ChatActions from './chat-actions';
import { _topicsToMarkAsRead, _messagesToMarkAsRead, _directMessagesToMarkAsRead } from './../utils';
var $ = require('jquery');

var ChatStore = Reflux.createStore({
    listenables: [ChatActions],

    history: new Map(),

    groupPaneHash: function () {
        var result = "";
        this.beginStateTx();
        if (this.state.selectedIntegrationGroup) {
            result = "IG_" + this.state.selectedIntegrationGroup.integrationGroupId;
        } else if (this.state.selectedIntegration) {
            result = "I_" + this.state.selectedIntegration.id;
        } else if (this.state.selectedGroup) {
            result = "G_" + this.state.selectedGroup.id;
        } else if (!this.state.displaySettings) {
            result = "CHAT";
        }

        return result;
    },

    groupPaneHistory: function () {
        var hash = this.groupPaneHash();
        if (!this.history.has(hash))
            this.history.set(hash, {
                selectedTopic: undefined,
                selectedIntegrationTopic: undefined,
                selectedUserTopic: undefined
            });

        return this.history.get(hash);
    },

    init: function () {
        this.state = this.getInitialState();
        if (this.state.displaySettings) {
            this.onShowIntegrations(true);
        } else if (this.state.selectedTopic) {
            this.onSelectTopic(this.state.selectedTopic);
        } else if (this.state.selectedUser) {
            this.onSelectUser(this.state.selectedUser);
        } else if (this.state.selectedIntegration && this.state.selectedIntegrationGroup && this.state.selectedIntegrationTopic) {
            this.onSelectIntegrationTopic(this.state.selectedIntegration, this.state.selectedIntegrationGroup, this.state.selectedIntegrationTopic);
        } else if (this.state.selectedIntegration && this.state.selectedIntegrationGroup) {
            this.onSelectIntegrationGroup(this.state.selectedIntegration, this.state.selectedIntegrationGroup);
        } else if (this.state.selectedIntegration) {
            this.onSelectIntegration(this.state.selectedIntegration);
        } else {
            this.onSelectGroup(this.state.selectedGroup);
        }
        delete _global.topics;
        this.commitStateTx();
        // var self = this;
        // TODO: Re-fetch groups, topics, etc
        /*window.addEventListener('popstate', function (e) {
         self.trigger(e.state);
         }, false);*/
    },

    _copy: function (obj) {
        var copy = obj.constructor();
        for (var attr in obj) {
            if (obj.hasOwnProperty(attr)) {
                if (Object.prototype.toString.call(obj[attr]) === '[object Array]') {
                    copy[attr] = obj[attr].slice();
                } else {
                    copy[attr] = obj[attr];
                }
            }
        }
        return copy;
    },

    beginStateTx: function () {
        if (!this.tx) {
            this.state = this._copy(this.state);
            this.tx = true;
        }
    },

    nullifyExcept: function () {
        this.beginStateTx();
        var keys = new Set([
            "displaySettings",
            "selectedGroup",
            "selectedIntegration",
            "selectedIntegrationGroup",
            "selectedIntegrationTopic",
            "selectedTopic",
            "selectedUser",
            "selectedUserTopic"]);

        for (var id = 0; id < arguments.length; ++id)
            keys.delete(arguments[id]);

        for (let key of keys)
            this.state[key] = undefined;
    },

    getInitialState: function () {
        return (this.state)
            ? this.state
            : {
                users: _global.users.filter(function (u) {
                    return u.id !== _global.user.id;
                }),
                integrations: _global.integrations,
                displaySettings: _global.displaySettings,
                groups: _global.groups,
                integrationGroups: _global.integrationGroups,
                topics: _global.topics,
                messages: [],
                selectedGroup: _global.selectedGroupId ? _global.groups.filter(function (g) {
                    return g.id == _global.selectedGroupId
                })[0] : undefined,
                selectedTopic: _global.selectedTopic,
                selectedUserTopic: _global.selectedUserTopicId ? _global.users.find(u => u.id == _global.selectedUserTopicId) : undefined,
                selectedIntegrationTopic: _global.selectedIntegrationTopic,
                selectedIntegration: _global.selectedIntegrationId ? _global.integrations.find(i => i.id == _global.selectedIntegrationId) : undefined,
                selectedIntegrationGroup: _global.selectedIntegrationId && _global.selectedIntegrationGroupId ? _global.integrationGroups.find(g =>
                    g.integrationId == _global.selectedIntegrationId && g.integrationGroupId == _global.selectedIntegrationGroupId) : undefined,
                selectedUser: _global.selectedUserId ? _global.users.find(u => u.id == _global.selectedUserId) : undefined,
                query: undefined
            };
    },

    commitStateTx: function (_state) {
        var state = _state ? _state : this.state;
        if (state) {
            var keyValues = new Map();

            if (state.selectedGroup)
                keyValues.set("groupId", state.selectedGroup.id);

            if (state.displaySettings)
                keyValues.set("displaySettings", "true");

            if (state.selectedTopic)
                keyValues.set("topicId", state.selectedTopic.id);

            if (state.selectedUserTopic)
                keyValues.set("userTopicId", state.selectedUserTopic.id);

            if (state.selectedUser)
                keyValues.set("userId", state.selectedUser.id);

            if (state.selectedIntegration)
                keyValues.set("integrationId", state.selectedIntegration.id);

            if (state.selectedIntegrationGroup)
                keyValues.set("integrationGroupId", state.selectedIntegrationGroup.integrationGroupId);

            if (state.selectedIntegrationTopic)
                keyValues.set("integrationTopicId", state.selectedIntegrationTopic.integrationTopicId
                    ? state.selectedIntegrationTopic.integrationTopicId
                    : state.selectedIntegrationTopic.id);

            if (state.selectedIntegrationTopic)
                keyValues.set("integrationTopicGroupId", state.selectedIntegrationTopic.integrationGroupId
                    ? state.selectedIntegrationTopic.integrationGroupId
                    : state.selectedIntegrationTopic.id);

            if (state.query && state.query !== "")
                keyValues.set("query", state.query);

            var query = "?", separator = "";

            for (var [key, value] of keyValues) {
                if (!value) continue;
                query += separator + key + "=" + value;
                separator = "&";
            }

            window.history.replaceState(state, window.title, query);
            // TODO: pushState

            this.state = state;
            delete this.tx;
            this.trigger(state);
        }
    },

    formQueryRequest: function(prefix) {
        this.beginStateTx();
        if (!prefix) { prefix = "?" }
        return this.state.query && this.state.query !== "" ? prefix + "query=" + this.state.query : "";
    },

    messagesURL: function () {
        this.beginStateTx();
        return "/json/user/" + _global.user.id + "/messages/" + this.state.selectedTopic.id + this.formQueryRequest("?");
    },

    topicsURL: function () {
        this.beginStateTx();
        return "/json/user/" + _global.user.id + "/topics" +
            (this.state.selectedGroup ? "/" + this.state.selectedGroup.id : "") + this.formQueryRequest("?");
    },

    groupsURL: function () {
        return "/json/user/" + _global.user.id + "/groups" + this.formQueryRequest("?");
    },

    userTopicURL: function () {
        this.beginStateTx();
        return "/json/user/" + _global.user.id + "/direct/" + this.state.selectedUserTopic.id + this.formQueryRequest("?");
    },

    integrationTopicURL: function () {
        this.beginStateTx();
        var integrationTopicId = this.state.selectedIntegrationTopic.integrationTopicId
            ? this.state.selectedIntegrationTopic.integrationTopicId
            : this.state.selectedIntegrationTopic.id;

        var integrationTopicGroupId = this.state.selectedIntegrationTopic.group.id
            ? this.state.selectedIntegrationTopic.group.id
            : this.state.selectedIntegrationTopic.id;

        return "/json/user/" + _global.user.id + "/integration/" + this.state.selectedIntegration.id +
        "/messages?integrationGroupId=" + integrationTopicGroupId
        + "&integrationTopicId=" + integrationTopicId + this.formQueryRequest("&");
    },

    updateGroups: function () {
        $.ajax({
            context: this,
            type: "GET",
            url: this.groupsURL(),
            success: function (groups) {
                var state = this._copy(this.state);
                state.groups = groups;
                this.commitStateTx(state);
            }.bind(this),
            fail: function (e) {
                console.error(e);
            }.bind(this)
        });
    },

    updateTopics: function () {
        var self = this;

        function selectTopics(topics) {
            self.beginStateTx();
            self.state.topics = topics;
            self.state.integrationTopics = undefined;
            if (self.state.selectedUserTopic && !self.state.selectedGroup) {
                self.onSelectUserTopic(self.state.selectedUserTopic);
            } else {
                if (topics.length > 0) {
                    var history = self.groupPaneHistory();
                    if (history.selectedTopic && topics.find(t => t.topic && t.topic.id === history.selectedTopic.id)) {
                        self.onSelectTopic(history.selectedTopic);
                    } else if (history.selectedUserTopic && topics.find(t => t.userTopic && t.userTopic.id === history.selectedUserTopic.id)) {
                        self.onSelectUserTopic(history.selectedUserTopic);
                    } else if (topics[0].topic) {
                        history.selectedTopic = topics[0].topic;
                        history.selectedUserTopic = undefined;
                        self.onSelectTopic(topics[0].topic);
                    } else if (topics[0].userTopic) {
                        history.selectedTopic = undefined;
                        history.selectedUserTopic = topics[0].userTopic;
                        self.onSelectUserTopic(topics[0].userTopic);
                    } else {
                        self.onSelectTopic();
                    }
                } else {
                    self.onSelectTopic();
                }
            }
        }

        if (_global.topics) {
            selectTopics(_global.topics);
        } else {
            $.ajax({
                context: this,
                type: "GET",
                url: this.topicsURL(),
                success: function (topics) {
                    selectTopics(topics);
                },
                fail: function (e) {
                    console.error(e);
                }
            });
        }
    },

    updateMessages: function () {
        var self = this;
        this.state.messages = undefined;
        this.state.integrationMessages = undefined;
        this.beginStateTx();

        if (this.state.selectedTopic) {
            $.ajax({
                context: this,
                type: "GET",
                url: this.messagesURL(),
                success: function (messages) {
                    var state = self._copy(self.state);
                    state.messages = messages;
                    self.commitStateTx(state);
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        } else if (this.state.selectedIntegrationTopic) {
            $.ajax({
                context: this,
                type: "GET",
                url: this.integrationTopicURL(),
                success: function (messages) {
                    var state = self._copy(self.state);
                    state.integrationMessages = messages;
                    self.commitStateTx(state);
                },
                fail: function (e) {
                    console.error(e);
                }
            });
        } else if (this.state.selectedIntegration) {
            this.state.integrationMessages = [];
            this.state.messages = undefined;
            self.commitStateTx();
        } else {
            this.state.integrationMessages = undefined;
            this.state.messages = [];
            self.commitStateTx();
        }
    },

    filterUsers: function () {
        this.beginStateTx();
        this.state.users = _global.users.filter(function (u) {
            return u.id !== _global.user.id && (!this.state.query || u.name.indexOf(this.state.query) > -1);
        }.bind(this));
    },

    onSelectGroup: function (group) {
        this.beginStateTx();
        this.nullifyExcept('selectedTopic', 'selectedUserTopic');
        this.state.selectedGroup = group;
        this.updateTopics();
    },

    onSelectTopic: function (topic) {
        this.beginStateTx();
        this.nullifyExcept("selectedGroup");
        if (topic) {
            var history = this.groupPaneHistory();
            history.selectedTopic = topic;
            history.selectedUserTopic = undefined;
        }

        this.state.selectedTopic = topic;
        this.updateMessages();
        this.commitStateTx();
    },

    onSelectUserTopic: function (userTopic) {
        var self = this;
        this.beginStateTx();
        this.nullifyExcept();

        if (userTopic) {
            var history = this.groupPaneHistory();
            history.selectedTopic = undefined;
            history.selectedUserTopic = userTopic;
        }

        this.state.messages = undefined;
        this.state.integrationMessages = undefined;

        this.state.selectedUserTopic = userTopic;
        this.commitStateTx();
        $.ajax({
            context: this,
            type: "GET",
            url: this.userTopicURL(),
            success: function (messages) {
                var state = self._copy(self.state);
                state.messages = messages;
                self.commitStateTx(state);
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectIntegrationTopic: function (integration, group, topic) {
        this.beginStateTx();
        if (topic) {
            this.groupPaneHistory().selectedIntegrationTopic = topic;
        }

        this.nullifyExcept();
        this.state.selectedIntegration = integration;
        this.state.selectedIntegrationGroup = group;
        this.state.selectedIntegrationTopic = topic;

        //todo: selected topic history

        this.updateMessages();
        this.commitStateTx();
    },

    onSelectUser: function (user) {
        this.beginStateTx();
        var self = this;
        if (this.state.query) {
            this.state.query = undefined;
            this.filterUsers();
            this.updateGroups();
        }
        this.nullifyExcept();
        this.state.messages = undefined;
        this.state.integrationMessages = undefined;
        this.state.selectedUser = user;
        this.commitStateTx();
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/direct/" + user.id,
            success: function (messages) {
                var state = self._copy(self.state);
                state.messages = messages;
                self.commitStateTx(state);
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectIntegration: function (integration) {
        var self = this;
        this.beginStateTx();
        this.nullifyExcept("selectedIntegrationTopic");
        this.state.selectedIntegration = integration;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/integration/" + integration.id + "/topics" + this.formQueryRequest("?"),
            success: function (topics) {
                self.state.topics = undefined;
                self.state.integrationTopics = topics;
                if (topics.length > 0) {
                    var history = this.groupPaneHistory();
                    if (!history.selectedIntegrationTopic) {
                        history.selectedIntegrationTopic = topics[0].topic;
                    }
                    self.onSelectIntegrationTopic(integration, undefined, history.selectedIntegrationTopic);
                } else {
                    self.onSelectIntegrationTopic(integration);
                }
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectIntegrationGroup: function (integration, group) {
        var self = this;
        this.beginStateTx();
        this.nullifyExcept("selectedIntegrationTopic");
        this.state.selectedIntegration = integration;
        this.state.selectedIntegrationGroup = group;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/integration/" + integration.id + "/topics"
            + (group ? "?groupId=" + group.integrationGroupId : "") + this.formQueryRequest("&"),
            success: function (topics) {
                self.state.topics = undefined;
                self.state.integrationTopics = topics;
                if (topics.length > 0) {
                    var history = this.groupPaneHistory();
                    if (!history.selectedIntegrationTopic || !topics.find(t => t.topic.id == history.selectedIntegrationTopic.id && t.topic.group.id == history.selectedIntegrationTopic.group.id)) {
                        history.selectedIntegrationTopic = topics[0].topic;
                    }

                    self.onSelectIntegrationTopic(integration, group, history.selectedIntegrationTopic);
                } else {
                    self.onSelectIntegrationTopic(integration, group);
                }
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onNewGroup: function (group, select) {
        this.beginStateTx();
        if (this.state.groups.filter(function (g) {
                return g.id == group.id
            }).length == 0) {
            this.state.groups.push(group);
            if (select) {
                this.onSelectGroup(group);
            } else {
                this.commitStateTx();
            }
        } else if (select) {
            this.onSelectGroup(group);
        }
    },

    onNewUser: function (user) {
        this.beginStateTx();
        this.state.users.push(user);
        this.commitStateTx();
    },

    onUserOffline: function (id) {
        this.beginStateTx();
        var i = this.state.users.findIndex(u => u.id == id);
        if (i > -1) {
            this.state.users[i].online = false;
            this.commitStateTx();
        }
    },

    onUserOnline: function (id) {
        this.beginStateTx();
        var i = this.state.users.findIndex(u => u.id == id);
        if (i > -1) {
            this.state.users[i].online = true;
            this.commitStateTx();
        }
    },

    onEnableIntegration: function (integrationId, integration) {
        this.beginStateTx();
        var i = this.state.integrations.findIndex(ii => ii.id == integrationId);
        if (i > -1) {
            this.state.integrations[i].enabled = true;
            this.commitStateTx();
        } else if (integration) {
            integration.enabled = true;
            this.state.integrations.push(integration);
            this.commitStateTx();
        }
    },

    onDisableIntegration: function (integrationId) {
        this.beginStateTx();
        var i = this.state.integrations.findIndex(ii => ii.id == integrationId);
        if (i > -1) {
            this.state.integrations[i].enabled = false;
            this.commitStateTx();
        }
    },

    onShowIntegrations: function (initial) {
        this.beginStateTx();
        this.nullifyExcept();
        this.state.displaySettings = true;
        if (!initial) {
            this.commitStateTx();
            window.history.replaceState(this.state, window.title, "?settings=true");
        }
    },

    onNewTopic: function (topic, select) {
        this.beginStateTx();
        if ((!this.state.selectedGroup || this.state.selectedGroup.id ==
            topic.group.id) && this.state.topics.filter(function (m) {
                return m.topic.id == topic.id
            }).length == 0) {
            var unread = topic.user.id != _global.user.id;
            this.state.topics.splice(0, 0, { topic: topic, unread: unread, updateDate: topic.date });
            var group = this.state.groups.find(g => g.id == topic.group.id);
            if (group) {
                group.count = group.count + 1;
                if (unread) {
                    group.unreadCount = group.unreadCount + 1;
                }
            }
            if (select) {
                this.onSelectTopic(topic);
            } else {
                this.commitStateTx();
            }
        } else if (select) {
            this.onSelectTopic(topic);
        }
    },

    onNewIntegrationMessage: function (message) {
        this.beginStateTx();
        var trigger = false;
        // TODO: Check if we may apply message twice
        if (this.state.integrationMessages) {
            //todo: unread?
            if (this.state.selectedIntegrationTopic && this.state.selectedIntegrationTopic.id == message.integrationTopicId) {
                // TODO: Preserve message order
                this.state.integrationMessages.push(message);
                trigger = true;
            }
        }
        if (trigger == true) {
            this.commitStateTx();
        }
    },

    onNewMessage: function (message) {
        this.beginStateTx();
        var trigger = false;
        // TODO: Check if we may apply message twice
        if (this.state.messages && !this.state.messages.find(m => m.text == message.text)) {
            var unread = message.user.id != _global.user.id;
            message.unread = unread;
            if (message.group) {
                var group = this.state.groups.find(g => g.id == message.group.id);
                if (group) {
                    group.count = group.count + 1;
                    if (unread) {
                        group.unreadCount = group.unreadCount + 1;
                    }
                    trigger = true;
                }
            }
            if (message.topicId) {
                if (this.state.topics) {
                    var topic = this.state.topics.find(t => t.topic && t.topic.id == message.topicId);
                    if (topic) {
                        topic.count = topic.count + 1;
                        topic.updateDate = message.date;
                        if (unread) {
                            topic.unreadCount = topic.unreadCount + 1;
                        }
                        trigger = true
                    }
                }
            }
            if (message.toUser) {
                if (this.state.users) {
                    var user = this.state.users.find(u => u.id == message.user.id);
                    if (user) {
                        user.count = user.count + 1;
                        if (unread) {
                            user.unreadCount = user.unreadCount + 1;
                        }
                        trigger = true
                    }
                }
            }
            if (this.state.selectedTopic && this.state.selectedTopic.id == message.topicId ||
                this.state.selectedUser && message.toUser && (this.state.selectedUser.id == message.toUser.id && _global.user.id == message.user.id ||
                this.state.selectedUser.id == message.user.id && _global.user.id == message.toUser.id) ||
                this.state.selectedUserTopic && message.toUser && (this.state.selectedUserTopic.id == message.toUser.id && _global.user.id == message.user.id ||
                this.state.selectedUserTopic.id == message.user.id && _global.user.id == message.toUser.id)) {
                // TODO: Preserve message order
                this.state.messages.push(message);
                trigger = true;
            }
        }
        if (!this.state.selectedGroup && this.state.topics && message.toUser && (_global.user.id == message.toUser.id || _global.user.id == message.user.id)) {
            var index = this.state.topics.findIndex(t => t.userTopic && (t.userTopic.id == message.toUser.id || t.userTopic.id == message.user.id));
            var oldUserTopic;
            if (index >= 0) {
                oldUserTopic = this.state.topics[index];
                this.state.topics.splice(index, 1)
            }
            var newUserTopic = {
                userTopic: {
                    id: _global.user.id == message.toUser.id ? message.user.id : message.toUser.id,
                    name: _global.user.id == message.toUser.id ? message.user.name : message.toUser.name,
                    text: message.text
                },
                updateDate: message.date,
                unreadCount: (_global.user.id == message.toUser.id ? 1 : 0) + (oldUserTopic ? oldUserTopic.unreadCount : 0)
            };
            this.state.topics.splice(0, 0, newUserTopic);

        }
        if (trigger == true) {
            this.commitStateTx();
        }
    },

    onMarkTopicAsRead: function(topic) {
        this.beginStateTx();
        var trigger = false;
        if (this.state.topics) {
            var tt = this.state.topics.find(t => t.topic && t.topic.id == topic.id);
            if (tt && tt.unread) {
                tt.unread = false;
                _topicsToMarkAsRead.push(tt.topic.id);
                trigger = true;
            }
        }
        if (this.state.groups) {
            var group = this.state.groups.find(g => g.id == topic.group.id);
            if (group && group.unreadCount) {
                group.unreadCount = group.unreadCount - 1;
                trigger = true;
            }
        }
        if (trigger) {
            this.commitStateTx();
        }
    },

    onMarkDirectMessageAsRead: function(message) {
        this.beginStateTx();
        var trigger = false;
        if (this.state.topics) {
            var tt = this.state.topics.find(t => t.userTopic && t.userTopic.id == message.user.id);
            if (tt && tt.unreadCount) {
                tt.unreadCount = tt.unreadCount - 1;
                trigger = true;
            }
        }
        if (this.state.messages) {
            var mm = this.state.messages.find(m => m.id == message.id);
            if (mm && mm.unread) {
                mm.unread = false;
                _directMessagesToMarkAsRead.push(mm.id);
                trigger = true;
            }
        }
        if (this.state.users) {
            var user = this.state.users.find(g => g.id == message.user.id);
            if (user && user.unreadCount) {
                user.unreadCount = user.unreadCount - 1;
                trigger = true;
            }
        }
        if (trigger) {
            this.commitStateTx();
        }
    },

    onMarkMessageAsRead: function(message) {
        this.beginStateTx();
        var trigger = false;
        if (this.state.messages) {
            var mm = this.state.messages.find(m => m.id == message.id && m.topicId);
            if (mm && mm.unread) {
                mm.unread = false;
                _messagesToMarkAsRead.push(mm.id);
                trigger = true;
            }
        }
        if (this.state.groups) {
            var group = this.state.groups.find(g => g.id == message.group.id);
            if (group && group.unreadCount) {
                group.unreadCount = group.unreadCount - 1;
                trigger = true;
            }
        }
        if (this.state.topics) {
            var topic = this.state.topics.find(t => t.topic && t.topic.id == message.topicId);
            if (topic && topic.unreadCount) {
                topic.unreadCount = topic.unreadCount - 1;
                trigger = true;
            }
        }
        if (trigger) {
            this.commitStateTx();
        }
    },

    onAlertQuery: function(newQuery) {
        this.beginStateTx();
        if (this.state.query !== newQuery) {
            this.state.query = newQuery;
            this.filterUsers();
            this.updateGroups();
            this.updateTopics();
            this.commitStateTx();
        }
    }
});

export default ChatStore;
