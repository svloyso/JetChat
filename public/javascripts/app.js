import React from 'react';
import Reflux from 'reflux';
import ChatStore from './events/chat-store';
import ChatActions from './events/chat-actions';
import SideBar from './components/side-bar';
import IntegrationsPane from './components/integrations-pane';
import TopicBar from './components/topic-bar';
import MessageBar from './components/message-bar';
import utils from './utils';

var App = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    openSocket: function () {
        var self = this;
        var socket = new WebSocket(_global.webSocketUrl);
        socket.onmessage = function (message) {
            console.log("openSocket: " + JSON.stringify(message));
            if (message.data && message.data !== JSON.stringify("Tack")) {
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
                } else if (data.id && !data.toUser) {
                    ChatActions.newTopic(data);
                } else if (data.toUser) {
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
            console.error(event);
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

    stateIsSettings: function () {
        return this.state.store.selected.stateId === this.state.store.SETTINGS
    },

    stateIsIntegration: function () {
        return !!this.state.store.integrations.find(i => i.id === this.state.store.selected.stateId);
    },

    isTopicBarVisible: function () {
        return !this.state.store.selected.userId;
    },

    render: function () {
        var store = this.state.store;

        if (window.process && window.process.platform === 'darwin') {
            var app = window.require('remote').app;
            var unreadGroupCount = store.groups.length > 0 ? store.groups.map(g => g.unreadCount).reduce((a, b) => a + b) : 0;
            var unreadUserCount = store.users.length > 0 ? store.users.map(u => u.unreadCount).reduce((a, b) => a + b) : 0;
            var unreadCount = unreadGroupCount + unreadUserCount;
            app.dock.setBadge(unreadCount ? unreadCount.toString() : "");
        }

        console.log("re-rendering app");

        var selectedMyChats = !store.selected.groupId && store.selected.stateId === store.CHAT;
        var selectedIntegration = store.selected.groupId
            ? undefined
            : store.integrations.find(i => i.id === store.selected.stateId)
                ? store.selected.stateId
                : undefined;

        var integrationGroups = store.integrationGroups
            .group(g => g.integrationId)
            .filter(pair => store.integrations.find(i => i.id === pair.key).enabled);

        var bars = [<SideBar
            groups={store.groups}
            integrations={store.integrations.filter(i => i.enabled)}
            integrationGroups={integrationGroups}
            selectedGroupId={store.selected.groupId}
            selectedIntegration={selectedIntegration}
            selectedMyChats={selectedMyChats}
            users={store.users}
        />];

        if (this.stateIsSettings()) {
            bars.push(<IntegrationsPane/>);
        } else {
            if (this.isTopicBarVisible()) {
                var newTopicEnabled = !!store.selected.groupId;
                var newTopicSelected = store.topics && store.topics.length === 0;

                bars.push(<TopicBar
                    newTopicEnabled={newTopicEnabled}
                    newTopicSelected={newTopicSelected}
                    selectedGroupId={store.selected.groupId}
                    selectedTopicId={store.selected.topicId}
                    stateIsIntegration={this.stateIsIntegration()}
                    topics={store.topics}
                />);
            }
            bars.push(<MessageBar className={this.isTopicBarVisible() ? "narrow" : "wide"} />);
        }

        return (<div>{bars}</div>);
    }
});

export default App;