import React from 'react';
import ReactDOM from 'react-dom';
import Reflux from 'reflux';
import MessageItem from './message-item';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';

var MessageBar = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    componentDidUpdate: function () {
        var messageRoll = $(ReactDOM.findDOMNode(this.refs.messageRoll));
        messageRoll.scrollTop(messageRoll[0].scrollHeight);
        ReactDOM.findDOMNode(this.refs.input).focus();
    },

    onInputKeyPress: function (event) {
        var self = this;
        var input = ReactDOM.findDOMNode(self.refs.input);
        if (event.which == 13 && input.value.trim()) {
            if (self.state.store.selectedUser) {
                var toUser = self.state.store.users.filter(function (u) {
                    return u.id == self.state.store.selectedUser.id
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
            } else {
                var newMessage = {
                    "user": _global.user,
                    "date": new Date().getTime(),
                    "groupId": self.state.store.selectedTopic ? self.state.store.selectedTopic.group.id : self.state.store.selectedGroup.id,
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
                            group: {id: newMessage.groupId},
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
        var messageItems = self.state.store.messages.map(function (message, index) {
            if (index == 0) {
                userId = message.user.id;
            } else {
                if (message.user.id != userId) {
                    sameUser = false;
                    topic = false;
                    userId = message.user.id;
                } else {
                    sameUser = true;
                }
            }
            var key = message.topicId ? message.topicId + "_" + message.id : message.id;
            return (
                <MessageItem message={message} topic={topic} sameUser={sameUser}
                             key={key}/>
            )
        });
        var inputPlaceHolder = self.state.store.selectedTopic ?
            "Message..." : "Topic...";
        var userHeader;
        if (self.state.store.selectedUser) {
            userHeader = <div id="message-roll-header">
                <li className="clearfix topic">
                    <img className="img avatar pull-left" src={self.state.store.selectedUser.avatar}/>
                    <div className="details">
                        <div className="info">
                            <span className="user">{self.state.store.selectedUser.name}</span>
                        </div>
                    </div>
                </li>
            </div>
        }
        return (
            // TODO: Replace logic with className
            <div id="message-bar" style={{left: this.state.store.selectedUser ? "200px" : "550px", display: this.state.store.displaySettings ? "none" : ""}}>
                <div id="message-pane">
                    <div id="message-roll" ref="messageRoll">
                        {messageItems}
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