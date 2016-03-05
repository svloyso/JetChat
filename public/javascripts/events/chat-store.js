import Reflux from 'reflux';
import ChatActions from './chat-actions';
import { _topicsToMarkAsRead, _messagesToMarkAsRead, _directMessagesToMarkAsRead } from './../utils';
var $ = require('jquery');

/**
 * TODO: don't mutate state
 */
var ChatStore = Reflux.createStore({
    listenables: [ChatActions],

    init: function () {
        this.state = this.getInitialState();
        if (this.state.displaySettings) {
            this.onShowIntegrations(true);
        } else if (this.state.selectedUser) {
            this.onSelectUser(this.state.selectedUser);
        } else if (this.state.selectedIntegration && !this.state.selectedIntegrationGroup && !this.state.selectedIntegrationTopic) {
            this.onSelectIntegration(this.state.selectedIntegration);
        } else if (this.state.selectedIntegration) {
            this.onSelectIntegrationGroup(this.state.selectedIntegration, this.state.selectedIntegrationGroup);
        } else {
            this.onSelectGroup(this.state.selectedGroup);
        }
        this.trigger(this.state);
        // var self = this;
        // TODO: Re-fetch groups, topics, etc
        /*window.addEventListener('popstate', function (e) {
         self.trigger(e.state);
         }, false);*/
    },

    getInitialState: function () {
        if (this.state) {
            return this.state;
        } else {
            return {
                users: _global.users.filter(function (u) {
                    return u.id !== _global.user.id
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
                selectedUser: _global.selectedUserId ? _global.users.find(u => u.id == _global.selectedUserId) : undefined
            }
        }
    },

    onSelectGroup: function (group) {
        var self = this;
        this.state.selectedGroup = group;
        this.state.selectedUser = undefined;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.state.displaySettings = undefined;

        function selectTopics(topics) {
            self.state.topics = topics;
            self.state.integrationTopics = undefined;
            if (self.state.selectedUserTopic && !self.state.selectedGroup) {
                self.onSelectUserTopic(self.state.selectedUserTopic);
            } else {
                if (topics.length > 0) {
                    var selectedTopic = self.state.selectedTopic ? topics.find(t => t.topic && t.topic.id == self.state.selectedTopic.id) : undefined;
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
                url: "/json/user/" + _global.user.id + "/topics" +
                (group ? "/" + group.id : ""),
                success: function (topics) {
                    selectTopics(topics);
                },
                fail: function (e) {
                    console.error(e);
                }
            });
        }
    },

    onSelectTopic: function (topic) {
        var self = this;
        this.state.selectedTopic = topic;
        this.state.selectedUser = undefined;
        this.state.selectedUserTopic = undefined;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.state.displaySettings = undefined;
        if (topic) {
            $.ajax({
                context: this,
                type: "GET",
                url: "/json/user/" + _global.user.id + "/messages/" + topic.id,
                success: function (messages) {
                    self.state.messages = messages;
                    self.state.integrationMessages = undefined;
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

    onSelectUserTopic: function (userTopic) {
        var self = this;
        this.state.selectedUser = undefined;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;
        this.state.selectedUserTopic = userTopic;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.state.displaySettings = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/direct/" + userTopic.id,
            success: function (messages) {
                self.state.messages = messages;
                self.state.integrationMessages = undefined;
                self.trigger(self.state);
                window.history.replaceState(self.state, window.title, "?userTopicId=" + self.state.selectedUserTopic.id);
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
        this.state.selectedUser = undefined;
        this.state.selectedUserTopic = undefined;
        this.state.displaySettings = undefined;
        if (topic) {
            // TODO: Refactor it
            var integrationTopicId = topic.integrationTopicId ? topic.integrationTopicId : topic.id;
            var integrationTopicGroupId = topic.integrationGroupId ? topic.integrationGroupId : topic.group.id;
            $.ajax({
                context: this,
                type: "GET",
                url: "/json/user/" + _global.user.id + "/integration/" + integration.id +
                    "/messages?integrationGroupId=" + integrationTopicGroupId + "&integrationTopicId=" + integrationTopicId,
                success: function (messages) {
                    self.state.messages = undefined;
                    self.state.integrationMessages = messages;
                    self.trigger(self.state);
                    // TODO: pushState
                    window.history.replaceState(self.state, window.title,
                        "?integrationId=" + integration.id + (group ? "&integrationGroupId=" + group.integrationGroupId : "") +
                        "&integrationTopicGroupId=" + integrationTopicGroupId + "&integrationTopicId=" + integrationTopicId);
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
            window.history.replaceState(this.state, window.title,
                "?integrationId=" + this.state.selectedIntegration.id + (group ? "&integrationGroupId=" + group.integrationGroupId : ""));
        }
    },

    onSelectUser: function (user) {
        var self = this;
        this.state.selectedUser = user;
        this.state.selectedGroup = undefined;
        this.state.selectedTopic = undefined;
        this.state.selectedIntegration = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.state.displaySettings = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/direct/" + user.id,
            success: function (messages) {
                self.state.messages = messages;
                self.state.integrationMessages = undefined;
                self.trigger(self.state);
                window.history.replaceState(self.state, window.title, "?userId=" + self.state.selectedUser.id);
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    onSelectIntegration: function (integration) {
        var self = this;
        this.state.selectedIntegration = integration;
        this.state.selectedGroup = undefined;
        this.state.selectedUser = undefined;
        this.state.selectedUserTopic = undefined;
        this.state.selectedIntegrationGroup = undefined;
        this.state.displaySettings = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/integration/" + integration.id + "/topics",
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
        this.state.selectedIntegration = integration;
        this.state.selectedIntegrationGroup = group;
        this.state.selectedGroup = undefined;
        this.state.selectedUser = undefined;
        this.state.selectedUserTopic = undefined;
        this.state.displaySettings = undefined;
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/user/" + _global.user.id + "/integration/" + integration.id + "/topics" + (group ? "?groupId=" + group.integrationGroupId : ""),
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
        this.state.displaySettings = true;
        this.state.selectedUser = undefined;
        this.state.selectedUserTopic = undefined;
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
    }
});

export default ChatStore;
