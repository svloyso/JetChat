import React from 'react';
import ReactEmoji from 'react-emoji';
import ReactAutolink from 'react-autolink';
import PrettyDate from 'pretty-date';
import ChatActions from '../events/chat-actions';
import VisibilitySensor from 'react-visibility-sensor';
var $ = require('jquery');

var MessageItem = React.createClass({
    mixins: [
        ReactEmoji, ReactAutolink
    ],

    onChange: function (isVisible) {
        if (isVisible && this.props.message.unread && $(document.body).hasClass("visible")) {
            if (this.props.message.topicId) {
                ChatActions.markMessageAsRead(this.props.message);
            } else if (this.props.message.toUser && this.props.message.user.id != _global.user.id) {
                ChatActions.markDirectMessageAsRead(this.props.message);
            }
        }
    },

    render: function () {
        var self = this;
        var className = ("clearfix" + " " + (self.props.topic ? "topic" : "") + " " + (self.props.sameUser ? "same-user" : "")).trim();
        var avatar;
        var info;
        if (!self.props.sameUser) {
            avatar = <img className="img avatar pull-left" src={self.props.message.user.avatar}/>;
            var prettyDate = PrettyDate.format(new Date(self.props.message.date));
            info = <div className="info">
                <span className="author">{self.props.message.user.name}</span>
                &nbsp;
                <span className="pretty date"
                      data-date={self.props.message.date}>{prettyDate}</span>
            </div>;
        }
        // TODO: Refactor me
        return (
            <li className={className} data-user={self.props.message.user.id}>
                <VisibilitySensor onChange={self.onChange} />
                {avatar}
                <div className="details">
                    {info}
                    { this.autolink(self.props.message.text, { target: "_blank", className: "imagify"}).map(function (el) {
                        if ((typeof el === "string") && el.trim().length) {
                            return self.emojify(el).map(function (el) {
                                    if (typeof el === "string") {
                                        var tokens = el.split("\n");
                                        return tokens.map(function(item) {
                                            return (
                                                <div className="text">
                                                    {item}
                                                </div>
                                            )
                                        })
                                    } else {
                                        return (
                                            <div className="text">
                                                {el}
                                            </div>
                                        );
                                    }
                                });
                        } else {
                            if ((typeof el !== "string") || el.trim().length) return (
                                <div className="text">
                                    {el}
                                </div>
                            ); else return null;
                        }
                    })}
                </div>
            </li>
        );
    }
});

export default MessageItem;