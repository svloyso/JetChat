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

    stateHistory: function() {
        var stateId = this.state.selected.stateId;
        if (stateId === this.state.CHAT && this.state.selected.groupId)
            stateId = "G" + this.state.selected.groupId;

        if (!this.history.has(stateId))
            this.history.set(stateId, { lastTopicId: undefined });

        return this.history.get(stateId);
    },

    init: function () {
        this.state = this.getInitialState();
        if (this.isStateIdIn([this.state.SETTINGS])) {
            this.onShowIntegrations();
        } else if (this.isStateIdIn([this.state.CHAT])) {
            this.onSelectGroup(this.state.selected.groupId);
        } else if (this.state.selectedUser) {
            this.onSelectUser(this.state.selectedUser);
        } else if (this.state.selectedIntegration && !this.state.selectedIntegrationGroup && !this.state.selectedIntegrationTopic) {
            this.onSelectIntegration(this.state.selectedIntegration);
        } else if (this.state.selectedIntegration) {
            this.onSelectIntegrationGroup(this.state.selectedIntegration, this.state.selectedIntegrationGroup);
        } else {
            console.error("Unsopported stateId=" + this.state.selected.stateId)
        }

        // var self = this;
        // TODO: Re-fetch groups, topics, etc
        /*window.addEventListener('popstate', function (e) {
         self.trigger(e.state);
         }, false);*/
    },

    nullifyExcept: function () {
        var keys = new Set([
            "selectedIntegration",
            "selectedIntegrationGroup",
            "selectedIntegrationTopic",
            "selectedUser",
            "selectedUserTopic"]);

        for (var id = 0; id < arguments.length; ++id)
            keys.delete(arguments[id]);

        for (let key of keys)
            this.state[key] = undefined;
    },

    setStateId: function (stateId) {
        this.state.selected.stateId = stateId;
        this.state.selected.groupId = undefined;
        this.state.selected.topicId = undefined;
        this.state.selected.userId = undefined;
    },

    isStateIdIn: function (stateIds) {
        return stateIds.indexOf(this.state.selected.stateId) > -1;
    },

    setTopicId: function (topicId) {
        this.stateHistory().lastTopicId = topicId;
        this.state.selected.topicId = topicId;
    },

    setUserId: function (userId) {
        this.state.selected.userId = userId;
    },

    setTopics: function (topics) {
        this.state.topics = topics;
        if (topics)
            this.setTopicId(this.stateHistory().lastTopicId);
    },

    setGroupId: function (groupId) {
        this.state.selected.groupId = groupId;
        var topicId = this.stateHistory().lastTopicId;
        if (topicId)
            this.setTopicId(topicId);
    },

    updateState: function () {
        var keyValues = new Map([
            ["state", this.state.selected.stateId],
            ["group", this.state.selected.groupId],
            ["topic", this.state.selected.topicId],
            ["user", this.state.selected.userId]
        ]);

        if (this.state.selectedUserTopic)
            keyValues.set("userTopicId", this.state.selectedUserTopic.id);

        if (this.state.selectedIntegration)
            keyValues.set("integrationId", this.state.selectedIntegration.id);

        if (this.state.selectedIntegrationGroup)
            keyValues.set("integrationGroupId", this.state.selectedIntegrationGroup.integrationGroupId);

        if (this.state.selectedIntegrationTopic)
            keyValues.set("integrationTopicId", this.state.selectedIntegrationTopic.integrationTopicId
                ? this.state.selectedIntegrationTopic.integrationTopicId
                : this.state.selectedIntegrationTopic.id);

        if (this.state.selectedIntegrationTopic)
            keyValues.set("integrationTopicGroupId", this.state.selectedIntegrationTopic.integrationGroupId
                ? this.state.selectedIntegrationTopic.integrationGroupId
                : this.state.selectedIntegrationTopic.id);

        if (this.state.query && this.state.query !== "")
            keyValues.set("query", this.state.query);

        var urlQuery = "?", separator = "";

        for (var [key, value] of keyValues) {
            if (!value) continue;
            urlQuery += separator + key + "=" + value;
            separator = "&";
        }

        window.history.replaceState(this.state, window.title, urlQuery);
        // TODO: pushState
        this.trigger(this.state);
    },

    getInitialState: function () {
        return (this.state)
            ? this.state
            : {
                CHAT: "chat",
                SETTINGS: "settings",
                USER: "user",
                selected: {
                    stateId: _global.stateId,
                    groupId: _global.groupId,
                    topicId: _global.topicId,
                    userId: undefined
                },
                users: _global.users.filter(function (u) {
                    return u.id !== _global.user.id
                }),
                integrations: _global.integrations,
                groups: _global.groups,
                integrationGroups: _global.integrationGroups,
                topics: _global.topics,
                messages: [],
                selectedIntegrationTopic: _global.selectedIntegrationTopic,
                selectedIntegration: _global.selectedIntegrationId ? _global.integrations.find(i => i.id == _global.selectedIntegrationId) : undefined,
                selectedIntegrationGroup: _global.selectedIntegrationId && _global.selectedIntegrationGroupId ? _global.integrationGroups.find(g =>
                    g.integrationId == _global.selectedIntegrationId && g.integrationGroupId == _global.selectedIntegrationGroupId) : undefined,
                query: undefined
            };
    },

    formQueryRequest: function(prefix) {
        if (!prefix) { prefix = "?" }
        return this.state.query && this.state.query !== "" ? prefix + "query=" + this.state.query : "";
    },

    messagesURL: function () {
        return "/json/user/" + _global.user.id + "/messages/" + this.state.selected.topicId + this.formQueryRequest("?");
    },

    topicsURL: function () {
        return "/json/user/" + _global.user.id + "/topics" +
            (this.state.selected.groupId ? "/" + this.state.selected.groupId : "") + this.formQueryRequest("?");
    },

    groupsURL: function () {
        return "/json/user/" + _global.user.id + "/groups" + this.formQueryRequest("?");
    },

    userURL: function () {
        return "/json/user/" + _global.user.id + "/direct/" + this.state.selected.userId + this.formQueryRequest("?");
    },

    integrationTopicURL: function () {
        var integrationTopicId = this.state.selectedIntegrationTopic.integrationTopicId
            ? this.state.selectedIntegrationTopic.integrationTopicId
            : this.state.selectedIntegrationTopic.id;

        var integrationTopicGroupId = this.state.selectedIntegrationTopic.integrationGroupId
            ? this.state.selectedIntegrationTopic.integrationGroupId
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
                this.state.groups = groups;
            }.bind(this),
            fail: function (e) {
                this.state.groups = [];
                console.error(e);
            }.bind(this)
        });
    },

    updateTopics: function () {
        this.state.selected.topicId = undefined;
        this.state.topics = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: this.topicsURL(),
            success: function (topics) {
                this.setTopics(topics);
                this.updateState();
            }.bind(this),
            fail: function (e) {
                console.error(e);
            }
        });
    },

    updateMessages: function () {
        this.state.messages = undefined;
        this.state.integrationMessages = undefined;

        var url = '';
        if (this.state.selected.stateId === this.state.CHAT) {
            url = this.messagesURL();
        } else if (this.state.selected.stateId === this.state.USER) {
            url = this.userURL();
        } else {
            console.error("updateMessages: unsupported stateId=" + this.state.selected.stateId);
        }

        $.ajax({
            context: this,
            type: "GET",
            url: url,
            success: function (messages) {
                this.state.messages = messages;
                this.updateState();
            }.bind(this),
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectGroup: function (groupId) {
        this.setStateId(this.state.CHAT);
        this.setGroupId(groupId);
        this.updateTopics();
        this.updateState();
    },

    onSelectTopic: function (topicId) {
        var groupId = this.state.selected.groupId;
        this.setStateId(this.state.selected.stateId);
        this.setGroupId(groupId);
        this.setTopicId(topicId);
        this.updateMessages();
        this.updateState();
    },

    onSelectUser: function (userId) {
        this.setStateId(this.state.USER);
        this.setUserId(userId);
        this.updateMessages();
        this.updateState();
    },

    onSelectUserTopic: function (userId) {
        this.setStateId(this.state.CHAT);
        this.setUserId(userId);
        this.updateMessages();
        this.updateState();
    },

    onSelectIntegrationTopic: function (integration, group, topic) {
        var self = this;
        this.nullifyExcept();
        this.state.selectedIntegration = integration;
        this.state.selectedIntegrationGroup = group;
        this.state.selectedIntegrationTopic = topic;

        if (topic) {
            // TODO: Refactor it
            $.ajax({
                context: this,
                type: "GET",
                url: this.integrationTopicURL(),
                success: function (messages) {
                    self.state.messages = undefined;
                    self.state.integrationMessages = messages;
                    // TODO: pushState
                    self.updateState();
                },
                fail: function (e) {
                    console.error(e);
                }
            });
        } else {
            this.state.messages = undefined;
            this.state.integrationMessages = []; // TODO
            // TODO: pushState
            this.updateState();
        }
    },

    onSelectIntegration: function (integration) {
        var self = this;
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
        var self = this;
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
                this.onSelectGroup(group.id);
            } else {
                this.trigger(this.state);
            }
        } else if (select) {
            this.onSelectGroup(group.id);
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

    onShowIntegrations: function () {
        this.setStateId(this.state.SETTINGS);
        this.updateState();
    },

    onNewTopic: function (topic, select) {
        if ((this.state.selected.groupId == topic.group.id) && this.state.topics.filter(function (m) {
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
        if (!this.state.selected.groupId && this.state.topics && message.toUser && (_global.user.id == message.toUser.id || _global.user.id == message.user.id)) {
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
        if (this.state.query !== newQuery) {
            this.state.query = newQuery;
            this.updateGroups();
            this.updateTopics();
            this.updateState();
        }
    }
});

var $ = require('jquery');

export default ChatStore;
