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

    render: function () {
        if (window.process) {
            var app = window.require('remote').app;
            var unreadGroupCount = this.state.store.groups.length > 0 ? this.state.store.groups.map(g => g.unreadCount).reduce((a, b) => a + b) : 0;
            var unreadUserCount = this.state.store.users.length > 0 ? this.state.store.users.map(u => u.unreadCount).reduce((a, b) => a + b) : 0;
            var unreadCount = unreadGroupCount + unreadUserCount;
            app.dock.setBadge(unreadCount ? unreadCount.toString() : "");
        }

        return (
            <div>
                <SideBar/>
                <IntegrationsPane/>
                <TopicBar/>
                <MessageBar/>
            </div>
        );
    }
});

export default App;