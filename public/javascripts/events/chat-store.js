import Reflux from 'reflux';
import ChatActions from './chat-actions';
import { _topicsToMarkAsRead, _messagesToMarkAsRead, _directMessagesToMarkAsRead } from './../utils';
var $ = require('jquery');
/**
 * TODO: don't mutate state
 */
var ChatStore = Reflux.createStore({
    listenables: [ChatActions],

    history: new Map(),

    CHAT: "chat",
    SETTINGS: "settings",
    USER: "user",

    firstDefined: function () {
        for (var id = 0; id < arguments.length; ++id)
            if (arguments[id])
                return arguments[id];

        return undefined;
    },

    integrationHistory: function (integrationId) {
        if (!integrationId)
            integrationId = this.state.selected.integrationId;

        var history = this.history;
        if (!history.has(integrationId))
            history.set(integrationId, {
                enabled: true,
                groupsHistory: new Map(),
                lastGroupId: undefined,
                name: undefined
            });

        return history.get(integrationId);
    },

    groupHistory: function (integrationId, groupId) {
        if (!integrationId)
            integrationId = this.state.selected.integrationId;

        if (!groupId)
            groupId = this.state.selected.groupId;

        var history = this.integrationHistory(integrationId).groupsHistory;
        if (!history.has(groupId))
            history.set(groupId, { lastTopicId: undefined });

        return history.get(groupId);
    },

    resetSelected: function () {
        var sel = this.state.selected;
        sel.stateId = undefined;
        sel.groupId = undefined;
        sel.topicId = undefined;
    },

    setTopState: function (stateId) {
        this.resetSelected();
        this.state.selected.stateId = stateId;
    },

    init: function () {
        this.state = this.getInitialState();

        var stateId = this.state.selected.stateId;
        if (stateId == this.SETTINGS) {
            this.onShowIntegrations(true);
        } else if (stateId == this.USER) {
            this.onSelectUser(_global.users.find(u => u.id == this.state.selected.userId));
        } else if (stateId == this.CHAT) {
            this.onSelectGroup(this.state.selectedGroup);
        } else {
            console.error("unsupported stateId: " + stateId);
        }

        //if (this.state.selectedIntegration && !this.state.selectedIntegrationGroup && !this.state.selectedIntegrationTopic) {
        //    this.onSelectIntegration(this.state.selectedIntegration);
        //} else if (this.state.selectedIntegration) {
        //    this.onSelectIntegrationGroup(this.state.selectedIntegration, this.state.selectedIntegrationGroup);

        this.trigger(this.state);
        // var self = this;
        // TODO: Re-fetch groups, topics, etc
        /*window.addEventListener('popstate', function (e) {
         self.trigger(e.state);
         }, false);*/
    },

    getInitialState: function () {
        return (this.state)
            ? this.state
            : {
                CHAT: this.CHAT,
                SETTINGS: this.SETTINGS,
                USER: this.USER,
                selected: {
                    stateId: _global.stateId,
                    groupId: this.firstDefined(_global.selectedGroupId, _global.selectedIntegrationGroupId),
                    topicId: undefined,
                    query: undefined,
                    userId: _global.selectedUserId
                },
                users: _global.users.filter(function (u) {
                    return u.id !== _global.user.id
                }),
                integrations: _global.integrations,
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
                g.integrationId == _global.selectedIntegrationId && g.integrationGroupId == _global.selectedIntegrationGroupId) : undefined
            };
    },

    visibleQuery: function () {
        var keyValues = new Map([
            ["state", this.state.selected.stateId],
            ["group", this.state.selected.groupId],
            ["query", this.state.selected.query],
            ["topic", this.state.selected.topicId]
        ]);

        if (this.state.selectedUserTopic)
            keyValues.set("userTopicId", this.state.selectedUserTopic.id);

        if (this.state.selectedIntegrationGroup)
            keyValues.set("integrationGroupId", this.state.selectedIntegrationGroup.integrationGroupId);

        if (this.state.selectedIntegrationTopic)
            keyValues.set("integrationTopicId", this.state.selectedIntegrationTopic.integrationTopicId
                ? this.state.selectedIntegrationTopic.integrationTopicId
                : this.state.selectedIntegrationTopic.id);

        if (this.state.selectedIntegrationTopic)
            keyValues.set("integrationTopicGroupId", this.state.selectedIntegrationTopic.integrationGroupId
                ? this.state.selectedIntegrationTopic.integrationGroupId
                : this.state.selectedIntegrationTopic.group.id);

        var query = "?", separator = "";

        for (var [key, value] of keyValues) {
            if (!value) continue;
            query += separator + key + "=" + value;
            separator = "&";
        }

        return query;
    },

    formQueryRequest: function(prefix) {
        var selected = this.state.selected;
        if (!prefix) { prefix = "?" }
        return selected.query && selected.query !== "" ? prefix + "query=" + selected.query : "";
    },

    messagesURL: function () {
        return "/json/user/" + _global.user.id + "/messages/" + this.state.selected.topicId + this.formQueryRequest("?");
    },

    topicsURL: function () {
        return "/json/user/" + _global.user.id + "/topics" +
            (this.state.selectedGroup ? "/" + this.state.selectedGroup.id : "") + this.formQueryRequest("?");
    },

    groupsURL: function () {
        return "/json/user/" + _global.user.id + "/groups" + this.formQueryRequest("?");
    },

    userTopicURL: function () {
        return "/json/user/" + _global.user.id + "/direct/" + this.selectedUserTopic.id + this.formQueryRequest("?");
    },

    integrationTopicURL: function () {
        var integrationTopicId = this.state.selectedIntegrationTopic.integrationTopicId
            ? this.state.selectedIntegrationTopic.integrationTopicId
            : this.state.selectedIntegrationTopic.id;

        var integrationTopicGroupId = this.state.selectedIntegrationTopic.integrationGroupId
            ? this.state.selectedIntegrationTopic.integrationGroupId
            : this.state.selectedIntegrationTopic.group.id;

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
                this.state.groups = groups;
            }.bind(this),
            fail: function (e) {
                this.state.groups = [];
                console.error(e);
            }.bind(this)
        });
    },

    updateTopics: function () {
        var self = this;

        function selectTopics(topics) {
            self.state.topics = topics;
            self.state.integrationTopics = undefined;
            if (self.state.selectedUserTopic && !self.state.selectedGroup) {
                self.onSelectUserTopic(self.state.selectedUserTopic);
            } else {
                if (topics.length > 0) {
                    var selectedTopic = self.state.selected.topicId
                        ? topics.find(t => t.topic && t.topic.id == self.state.selected.topicId)
                        : undefined;
                    if (selectedTopic) {
                        self.onSelectTopic(selectedTopic.topic);
                    } else if (topics[0].topic) {
                        self.onSelectTopic(topics[0].topic);
                    } else if (topics[0].userTopic) {
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
            var topics = _global.topics;
            _global.topics = undefined;
            selectTopics(topics);
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
        if (this.state.selectedTopic) {
            var self = this;
            $.ajax({
                context: this,
                type: "GET",
                url: this.messagesURL(),
                success: function (messages) {
                    self.state.messages = messages;
                    self.state.integrationMessages = undefined;
                    // TODO: pushState
                    window.history.replaceState(self.state, window.title, self.visibleQuery());
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        } else {
            this.state.messages = [];
            // TODO: pushState
            window.history.replaceState(this.state, window.title, this.visibleQuery());
        }
    },

    onSelectGroup: function (group) {
        this.setTopState(this.CHAT);
        if (group) {
            var sel = this.state.selected;
            sel.groupId = group.id;
            sel.topicId = this.groupHistory().lastTopicId;
            this.integrationHistory().lastGroupId = sel.groupId;
        }
        this.state.selectedGroup = group;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.updateTopics();
    },

    onSelectTopic: function (topic) {
        if (topic) {
            var sel = this.state.selected;
            sel.topicId = topic.id;
            this.groupHistory(sel.integrationId, sel.groupId).lastTopicId = sel.topicId;
        }
        this.state.selectedTopic = topic;
        this.state.selectedUserTopic = undefined;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.updateMessages();
        this.trigger(this.state);
    },

    onSelectUserTopic: function (userTopic) {
        var self = this;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;
        this.state.selectedUserTopic = userTopic;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: this.userTopicURL(),
            success: function (messages) {
                self.state.messages = messages;
                self.state.integrationMessages = undefined;
                self.trigger(self.state);
                window.history.replaceState(self.state, window.title, self.visibleQuery());
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectIntegrationTopic: function (integration, group, topic) {
        var self = this;
        this.state.selectedIntegration = integration;
        this.state.selectedIntegrationGroup = group;
        this.state.selectedIntegrationTopic = topic;
        this.state.selectedTopic = undefined;
        this.state.selectedUserTopic = undefined;
        if (topic) {
            // TODO: Refactor it
            $.ajax({
                context: this,
                type: "GET",
                url: this.integrationTopicURL(),
                success: function (messages) {
                    self.state.messages = undefined;
                    self.state.integrationMessages = messages;
                    self.trigger(self.state);
                    // TODO: pushState
                    window.history.replaceState(self.state, window.title, self.visibleQuery());
                },
                fail: function (e) {
                    console.error(e);
                }
            });
        } else {
            this.state.messages = undefined;
            this.state.integrationMessages = []; // TODO
            this.trigger(this.state);
            // TODO: pushState
            window.history.replaceState(this.state, window.title, this.visibleQuery());
        }
    },

    onSelectUser: function (user) {
        var self = this;
        this.setTopState(this.USER);
        this.state.selected.userId = user.id;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/direct/" + user.id,
            success: function (messages) {
                self.state.messages = messages;
                self.state.integrationMessages = undefined;
                self.trigger(self.state);
                window.history.replaceState(self.state, window.title, "?userId=" + self.state.selected.userId);
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectIntegration: function (integration) {
        this.setTopState(integration.id);
        this.state.selectedIntegration = integration;
        this.state.selectedGroup = undefined;
        this.state.selectedUserTopic = undefined;
        this.state.selectedIntegrationGroup = undefined;
        var self = this;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/integration/" + integration.id + "/topics" + this.formQueryRequest("?"),
            success: function (topics) {
                self.state.topics = undefined;
                self.state.integrationTopics = topics;
                if (topics.length > 0) {
                    self.onSelectIntegrationTopic(integration, undefined, topics[0].topic);
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
        this.setTopState(integration.id);
        this.state.selectedIntegration = integration;
        this.state.selectedIntegrationGroup = group;
        this.state.selectedGroup = undefined;
        this.state.selectedUserTopic = undefined;
        var self = this;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/integration/" + integration.id + "/topics"
            + (group ? "?groupId=" + group.integrationGroupId : "") + this.formQueryRequest("&"),
            success: function (topics) {
                self.state.topics = undefined;
                self.state.integrationTopics = topics;
                if (topics.length > 0) {
                    var selectedIntegrationTopicId = self.state.selectedIntegrationTopic ? (self.state.selectedIntegrationTopic.integrationTopicId ? self.state.selectedIntegrationTopic.integrationTopicId : self.state.selectedIntegrationTopic.id) : undefined;
                    self.onSelectIntegrationTopic(integration, group, self.state.selectedIntegrationTopic &&
                    topics.find(t => t.topic.id == selectedIntegrationTopicId) ? self.state.selectedIntegrationTopic : topics[0].topic);
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

    onEnableIntegration: function (integrationId, integration) {
        var i = this.state.integrations.findIndex(ii => ii.id == integrationId);
        if (i > -1) {
            this.state.integrations[i].enabled = true;
            this.trigger(this.state);
        } else if (integration) {
            integration.enabled = true;
            this.state.integrations.push(integration);
            this.trigger(this.state);
        }
    },

    onDisableIntegration: function (integrationId) {
        var i = this.state.integrations.findIndex(ii => ii.id == integrationId);
        if (i > -1) {
            this.state.integrations[i].enabled = false;
            this.trigger(this.state);
        }
    },

    onShowIntegrations: function (initial) {
        this.setTopState(this.SETTINGS);
        this.state.selectedUserTopic = undefined;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;

        if (!initial) {
            this.trigger(this.state);
            window.history.replaceState(this.state, window.title, this.visibleQuery());
        }
    },

    onNewTopic: function (topic, select) {
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
                this.trigger(this.state);
            }
        } else if (select) {
            this.onSelectTopic(topic);
        }
    },

    onNewMessage: function (message) {
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
            var hitSelectedTopic = this.state.selectedTopic && this.state.selectedTopic.id == message.topicId;
            var hitSelectedUser = this.state.selected.stateId == this.USER && message.toUser
                && (this.state.selected.userId == message.toUser.id && _global.user.id == message.user.id
                    || this.state.selected.userId == message.user.id && _global.user.id == message.toUser.id);
            if (hitSelectedTopic || hitSelectedUser ||
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
            this.trigger(this.state);
        }
    },

    onMarkTopicAsRead: function(topic) {
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
            this.trigger(this.state);
        }
    },

    onMarkDirectMessageAsRead: function(message) {
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
            this.trigger(this.state);
        }
    },

    onMarkMessageAsRead: function(message) {
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
            this.trigger(this.state);
        }
    },

    onAlertQuery: function(newQuery) {
        if (this.state.selected.query !== newQuery) {
            this.state.selected.query = newQuery;
            this.updateGroups();
            this.updateTopics();
            this.trigger(this.state);
        }
    }
});

var $ = require('jquery');

export default ChatStore;
