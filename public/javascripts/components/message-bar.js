import classNames from 'classnames';
import nanoScroller from 'nanoscroller';
import React from 'react';
import ReactDOM from 'react-dom';
import Reflux from 'reflux';

import ChatActions from '../events/chat-actions';
import ChatStore from '../events/chat-store';
import IntegrationMessageItem from './integration-message-item';
import MessageItem from './message-item';
import {deepEqual} from '../events/chat-store-utils';

var $ = require('jquery');

var MessageBar = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    shouldComponentUpdate: function(nextProps, nextState) {
        return !deepEqual(this.state.store, nextState.store, ["selectedUser.id", "displaySettings", "query", "selectedIntegrationTopic.id", "selectedUserTopic.id", "selectedTopic.id", "messages.id", "integrationMessages.id"]);
    },

    componentDidUpdate: function () {
        var roll = $("#message-roll");
        if (roll) {
            if (!this.state.store.query || this.state.store.query.length === 0) {
                var input = $("#input");
                if (input)
                    input.focus();
            }

            var contentHeight = 0;
            this.refs.mess$("#message-roll-content").children().each(function(){
                contentHeight = contentHeight + $(this).outerHeight(true);
            });
            if (contentHeight < $("#message-roll-content").height()) {
                $("#message-roll-content").removeClass("nano-content");
                $("#message-roll").removeClass("nano");
                roll.nanoScroller({destroy: true});
                $("#message-roll")[0].style.height = null
            } else {
                $("#message-roll-content").addClass("nano-content");
                $("#message-roll").addClass("nano");
                roll.nanoScroller();
                roll.nanoScroller({scroll: "bottom"});
            }
        }
    },

    componentWillUnmount: function () {
        $("#message-roll").nanoScroller({destroy: true});
    },

    onInputKeyPress: function (event) {
        var self = this;
        var input = ReactDOM.findDOMNode(self.refs.input);
        if (event.which == 13 && input.value.trim() && !event.shiftKey) {
            var selectedUser = self.state.store.selectedUser ? self.state.store.selectedUser : self.state.store.selectedUserTopic;
            if (selectedUser) {
                var toUser = self.state.store.users.filter(function (u) {
                    return u.id == selectedUser.id
                })[0];
                var newDirectMessage = {
                    "user": _global.user,
                    "toUser": toUser,
                    "date": new Date().getTime(),
                    "text": input.value
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
                    "group": { id: self.state.store.selectedTopic ? self.state.store.selectedTopic.group.id : self.state.store.selectedGroup.id },
                    "text": input.value
                };
                if (self.state.store.selectedTopic) {
                    newMessage.topicId = self.state.store.selectedTopic.id;
                }
                $.ajax({
                    type: "POST",
                    url: self.state.store.selectedTopic ? "/json/comment/add" : "/json/topic/add",
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
                    "text": input.value
                };

                if (self.state.store.selectedIntegrationTopic) {
                    newIntegrationMessage.integrationTopicId = self.state.store.selectedIntegrationTopic.integrationTopicId ?
                        self.state.store.selectedIntegrationTopic.integrationTopicId :
                        self.state.store.selectedIntegrationTopic.id
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
                    success: function (m) {
                        if (m.integrationTopicId) {
                            ChatActions.newIntegrationMessage(m);
                        } else {
                            //todo: onNewIntegrationTopic
                        }
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                });
            }
            input.value = "";
            event.preventDefault();
        }
    },

    render: function () {
        var self = this;
        var userId;
        var topic = self.state.store.selectedUser === undefined;
        var sameUser = false;
        var messages = self.state.store.messages ? self.state.store.messages : self.state.store.integrationMessages;
        var messageItems = messages.map(function (message, index) {
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
        var inputPlaceHolder = self.state.store.selectedIntegrationTopic || self.state.store.selectedTopic ?
            "Message..." : "Topic...";
        var userHeader;
        var selectedUser = self.state.store.selectedUser ? self.state.store.selectedUser : self.state.store.selectedUserTopic;
        if (selectedUser) {
            userHeader = <div id="message-roll-header">
                <li className="clearfix topic">
                    <img className="img avatar pull-left" src={selectedUser.avatar}/>
                    <div className="details">
                        <div className="info">
                            <span className="user">{selectedUser.name}</span>
                        </div>
                    </div>
                </li>
            </div>
        }
        var className = classNames({
                ['wide']: this.state.store.selectedUser,
                ['narrow']: !this.state.store.selectedUser,
                ['hidden']: this.state.store.displaySettings
            }
        );
        return (
            <div id="message-bar" className={className}>
                <div id="message-pane">
                    <div id="message-roll" className="nano" ref="messageRoll">
                        <div id="message-roll-content" className="nano-content">
                            {messageItems}
                        </div>
                    </div>
                    {userHeader}
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