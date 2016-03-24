import React from 'react';
import ReactDOM from 'react-dom';
import Reflux from 'reflux';
import nanoScroller from 'nanoscroller';
import MessageItem from './message-item';
import IntegrationMessageItem from './integration-message-item';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';
import Loader from './loader'
var $ = require('jquery');

var MessageBar = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    componentDidUpdate: function() {
        var roll = $("#message-roll");
        roll.nanoScroller();
        roll.nanoScroller({scroll: "bottom"});
    },

    componentWillUnmount: function () {
        $("#message-roll").nanoScroller({destroy: true});
    },

    groupId: function (store) {
        if (!store.topicId)
            return store.groupId;

        var selectedTopic = store.topics.find(t => t.topic && t.topic.id === store.topicId);
        return selectedTopic && selectedTopic.topic && selectedTopic.topic.group ? selectedTopic.topic.group.id : undefined;
    },

    onInputKeyPress: function (event) {
        var self = this;
        var inputNode = ReactDOM.findDOMNode(self.refs.input);
        var text = inputNode.value.trim();
        if (event.which == 13 && input && !event.shiftKey) {
            var selectedUserId = self.state.store.userId;
            if (selectedUserId) {
                var toUser = self.state.store.users.find(u => u.id === selectedUserId);
                var newDirectMessage = {
                    "user": _global.user,
                    "toUser": toUser,
                    "date": new Date().getTime(),
                    "text": text
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
            } else if (self.state.store.messages) {
                var newMessage = {
                    "user": _global.user,
                    "date": new Date().getTime(),
                    "group": { id: this.groupId(self.state.store) },
                    "text": text
                };

                newMessage.topicId = self.state.store.topicId;

                $.ajax({
                    type: "POST",
                    url: self.state.store.topicId ? "/json/comment/add" : "/json/topic/add",
                    data: JSON.stringify(newMessage),
                    contentType: "application/json",
                    success: function (id) {
                        // TODO: Send full object from server
                        var m = {
                            id: id,
                            group: {id: newMessage.group.id},
                            text: newMessage.text,
                            date: new Date().getTime(),
                            user: _global.user
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
            } else { //integration messages
                //todo: remove duplicates with previous case?
                var newIntegrationMessage = {
                    "user": _global.user,
                    "date": new Date().getTime(),
                    "integrationGroupId": self.state.store.selectedIntegrationGroup.integrationGroupId,
                    "text": text
                };

                if (self.state.store.selectedIntegrationTopic) {
                    newIntegrationMessage.integrationTopicId = self.state.store.selectedIntegrationTopic.integrationTopicId ?
                        self.state.store.selectedIntegrationTopic.integrationTopicId :
                        self.state.store.selectedIntegrationTopic
                }

                //todo: add new topic case

                var url =
                    self.state.store.selectedIntegrationTopic ?
                        "/integration/" + self.state.store.selectedIntegration.id + "/comment/add" :
                        "/integration/" + self.state.store.selectedIntegration.id + "/topic/add";
                if (url) $.ajax({
                    type: "POST",
                    url: url,
                    data: JSON.stringify(newIntegrationMessage),
                    contentType: "application/json",
                    success: function (id) {
                        // TODO: Send full object from server
                        //todo: update integration messages, onNewIntegrationMessage
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                });
            }
            inputNode.value = "";
            event.preventDefault();
        }
    },

    userHeader: function () {
        var user = this.state.store.users.find(u => u.id === this.state.store.userId);
        if (!user)
            return undefined;

        return (
            <div id="message-roll-header" className={this.props.className}>
                <li className="clearfix topic">
                    <img className="img avatar pull-left" src={user.avatar}/>
                    <div className="details">
                        <div className="info">
                            <span className="user">{user.name}</span>
                        </div>
                    </div>
                </li>
            </div>
        );
    },

    render: function () {
        var self = this;
        var userId;
        var topic = self.state.store.userId === undefined;
        var sameUser = false;

        if (!self.state.store.messages)
            return (<Loader id="message-bar-loader" className={this.props.className} />);

        var messageItems = self.state.store.messages.map(function (message, index) {
            var user = message.user ? message.user : message.integrationUser;
            if (index == 0) {
                userId = user.id ? user.id : user.integrationUserId;
            } else {
                if (user.id != userId) {
                    sameUser = false;
                    topic = false;
                    userId = user.id ? user.id : user.integrationUserId;
                } else {
                    sameUser = true;
                }
            }
            if (message.integrationTopicId) {
                var key = message.integrationTopicId + (message.id ? "_" + message.id : "");
                return <IntegrationMessageItem message={message} topic={topic} sameUser={sameUser}
                             key={key}/>
            } else {
                var key = message.topicId ? message.topicId + "_" + message.id : message.id;
                return (
                    <MessageItem message={message} topic={topic} sameUser={sameUser}
                                 key={key}/>
                )
            }
        });

        var inputPlaceHolder = self.state.store.topicId ? "Message..." : "Topic...";

        return (
            <div id="message-bar" className={this.props.className}>
                <div id="message-pane">
                    {this.userHeader()}
                    <div className="nano" id="message-roll" ref="messageRoll">
                        <div className="nano-content">
                            {messageItems}
                        </div>
                    </div>
                </div>
                <textarea id="input" ref="input" autoComplete="off"
                          placeholder={inputPlaceHolder} className="enabled"
                          onKeyPress={self.onInputKeyPress}
                />
            </div>
        );
    }
});

export default MessageBar;