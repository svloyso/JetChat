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
        var stateId = this.state.stateId;
        if (this.state.groupId) {
            if (stateId === this.state.CHAT)
                stateId = "G" + this.state.groupId;
            else if (this.isStateIdIntegration())
                stateId = stateId + this.state.groupId;
        }

        if (!this.history.has(stateId))
            this.history.set(stateId, { lastTopicId: undefined });

        return this.history.get(stateId);
    },

    init: function () {
        this.state = this.getInitialState();
        if (this.isStateIdIn([this.state.SETTINGS])) {
            this.onShowIntegrations();
        } else if (this.isStateIdIn([this.state.CHAT])) {
            this.onSelectGroup(this.state.groupId);
        } else if (this.isStateIdIn([this.state.USER])) {
            this.onSelectUser(this.state.userId);
        } else if (this.isStateIdIntegration()) {
            if (!this.state.selectedIntegrationGroup && !this.state.selectedIntegrationTopic) {
                this.onSelectIntegration(/*this.state.selectedIntegration*/);
            } else {
                this.onSelectIntegrationGroup(this.state.selectedIntegration, this.state.selectedIntegrationGroup);
            }
        } else {
            console.error("Unsopported stateId=" + this.state.stateId)
        }

        // var self = this;
        // TODO: Re-fetch groups, topics, etc
        /*window.addEventListener('popstate', function (e) {
         self.trigger(e.state);
         }, false);*/
    },

    setStateId: function (stateId) {
        this.state.stateId = stateId;
        this.state.groupId = undefined;
        this.state.topicId = undefined;
        this.state.userId = undefined;
    },

    isStateIdIn: function (stateIds) {
        return stateIds.indexOf(this.state.stateId) > -1;
    },

    isStateIdIntegration: function () {
        return !!this.state.integrations.find(i => i.id === this.state.stateId);
    },

    setTopicId: function (topicId) {
        this.stateHistory().lastTopicId = topicId;
        this.state.topicId = topicId;
    },

    setUserId: function (userId) {
        this.state.userId = userId;
    },

    setTopics: function (topics) {
        this.state.topics = topics;
        if (topics && topics.length > 0)
            this.setTopicId(this.stateHistory().lastTopicId);
    },

    setGroupId: function (groupId) {
        this.state.groupId = groupId;
    },

    extractTopicId: function (topicWrapper) {
        if (topicWrapper.topic)
            return topicWrapper.topic.id;

        return undefined;
    },

    updateState: function () {
        var keyValues = new Map([
            ["state", this.state.stateId],
            ["group", this.state.groupId],
            ["topic", this.state.topicId],
            ["user", this.state.userId]
        ]);

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
                SEP: "@",

                stateId: _global.stateId,
                groupId: _global.groupId,
                topicId: _global.topicId,
                userId: undefined,

                users: _global.users,
                //users: _global.users.filter(function (u) {
                //    return u.id !== _global.user.id
                //}),
                integrations: _global.integrations,
                groups: _global.groups,
                integrationGroups: _global.integrationGroups,
                topics: _global.topics,
                messages: [],
                query: undefined
            };
    },

    formQueryRequest: function(prefix) {
        if (!prefix) { prefix = "?" }
        return this.state.query && this.state.query !== "" ? prefix + "query=" + this.state.query : "";
    },

    messagesURL: function () {
        return "/json/user/" + _global.user.id + "/messages/" + this.state.topicId + this.formQueryRequest("?");
    },

    topicsURL: function () {
        return "/json/user/" + _global.user.id + "/topics" +
            (this.state.groupId ? "/" + this.state.groupId : "") + this.formQueryRequest("?");
    },

    groupsURL: function () {
        return "/json/user/" + _global.user.id + "/groups" + this.formQueryRequest("?");
    },

    userMessagesURL: function () {
        var toUserId = this.state.userId
            ? this.state.userId
            : this.state.topicId.substring(this.state.USER.length + this.state.SEP.length);

        return "/json/user/" + _global.user.id + "/direct/" + toUserId + this.formQueryRequest("?");
    },

    integrationURL: function () {
        return "/json/user/" + _global.user.id + "/integration/" + this.state.stateId + "/topics" + this.formQueryRequest("?");
    },

    integrationTopicsURL: function () {
        return "/json/user/" + _global.user.id + "/integration/" + this.state.stateId + "/topics"
            + (this.state.groupId ? "?groupId=" + this.state.groupId : "")
            + this.formQueryRequest("&");
    },

    integrationUpdatesURL: function () {
        var groupId, topicId;
        [topicId, groupId] = this.state.topicId.split(this.state.SEP);

        return "/json/user/" + _global.user.id + "/integration/" + this.state.stateId
            + "/messages?integrationGroupId=" + groupId
            + "&integrationTopicId=" + topicId + this.formQueryRequest("&");
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
        this.state.topicId = undefined;
        this.state.topics = undefined;

        var url;
        if (this.isStateIdIn([this.state.CHAT])) {
            url = this.topicsURL();
        } else if (this.isStateIdIntegration()) {
            url = this.state.groupId
                ? this.integrationTopicsURL()
                : this.integrationURL();
        } else {
            console.log("updateTopics: wrong state");
        }

        $.ajax({
            context: this,
            type: "GET",
            url: url,
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
        var url = '';
        if (this.isStateIdIn([this.state.CHAT])) {
            if (typeof this.state.topicId === "string" &&
                this.state.topicId.startsWith(this.state.USER)) {
                url = this.userMessagesURL();
            } else {
                url = this.messagesURL();
            }
        } else if (this.isStateIdIn([this.state.USER]) && this.state.userId) {
            url = this.userMessagesURL();
        } else if (this.isStateIdIntegration()) {
            url = this.integrationUpdatesURL();
        } else {
            console.error("updateMessages: bad state, stateId=" + this.state.stateId);
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
        var groupId = this.state.groupId;
        this.setStateId(this.state.stateId);
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
        this.setTopicId(this.state.USER + this.state.SEP + userId);
        this.updateMessages();
        this.updateState();
    },

    onSelectIntegrationTopic: function (topicId) {
        var groupId = this.state.groupId;
        this.setStateId(this.state.stateId);
        this.setGroupId(groupId);
        this.setTopicId(topicId);
        //todo: selected topic history
        this.updateMessages();
        this.updateState();
    },

    onSelectIntegration: function (integration) {
        this.setStateId(integration.id);
        this.updateTopics();
        this.updateState();
    },

    onSelectIntegrationGroup: function (integration, group) {
        this.setStateId(integration.id);
        this.setGroupId(group ? group.integrationGroupId : undefined);
        this.updateTopics();
        this.updateState();
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
        if ((this.state.groupId == topic.group.id) && this.state.topics.filter(function (m) {
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
        console.log("onNewMessage: " + JSON.stringify(message));
        var trigger = false;

        function messageFitTopic(message, selected) {
            if (message.toUser && message.user && selected.userId)
                return (_global.user.id === message.user.id && selected.userId === message.toUser.id) ||
                    (_global.user.id === message.toUser.id && selected.userId === message.user.id);

            if (message.topicId)
                return selected.topicId === message.topicId;
        }

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
            if (messageFitTopic(message, this.state.selected)) {
                // TODO: Preserve message order
                this.state.messages.push(message);
                trigger = true;
            }
        }
        if (!this.state.groupId && this.state.topics && message.toUser && (_global.user.id == message.toUser.id || _global.user.id == message.user.id)) {
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
