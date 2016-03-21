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

    isTopicBarVisible: function () {
        return !this.state.store.selected.userId;
    },

    render: function () {
        if (window.process && window.process.platform === 'darwin') {
            var app = window.require('remote').app;
            var unreadGroupCount = this.state.store.groups.length > 0 ? this.state.store.groups.map(g => g.unreadCount).reduce((a, b) => a + b) : 0;
            var unreadUserCount = this.state.store.users.length > 0 ? this.state.store.users.map(u => u.unreadCount).reduce((a, b) => a + b) : 0;
            var unreadCount = unreadGroupCount + unreadUserCount;
            app.dock.setBadge(unreadCount ? unreadCount.toString() : "");
        }

        console.log("re-rendering app");

        var mainBars = [];

        if (this.stateIsSettings()) {
            mainBars.push(<IntegrationsPane/>);
        } else {
            if (this.isTopicBarVisible()) {
                var newTopicEnabled = !!this.state.store.selected.groupId;
                var newTopicSelected = this.state.store.topics && this.state.store.topics.length === 0;

                mainBars.push(<TopicBar newTopicEnabled={newTopicEnabled} newTopicSelected={newTopicSelected} />);
            }
            mainBars.push(<MessageBar className={this.isTopicBarVisible() ? "narrow" : "wide"} />);
        }

        return (
            <div>
                <SideBar selected={this.state.store.selected} />
                {mainBars}
            </div>
        );
    }
});

export default App;