import React from 'react';
import ReactEmoji from 'react-emoji';
import ReactAutolink from 'react-autolink';
import PrettyDate from 'pretty-date';
import ChatActions from '../events/chat-actions';
import VisibilitySensor from 'react-visibility-sensor';

var MessageItem = React.createClass({
    mixins: [
        ReactEmoji, ReactAutolink
    ],

    onChange: function (isVisible) {
        if (this.props.message.topicId && isVisible && this.props.message.unread) {
            ChatActions.markMessageAsRead(this.props.message);
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
        return (
            <li className={className} data-user={self.props.message.user.id}>
                <VisibilitySensor onChange={self.onChange} />
                {avatar}
                <div className="details">
                    {info}
                    <div className="text">{this.autolink(self.props.message.text, { className: "imagify"}).map(function (el) {
                        if (typeof el === "string")
                            return self.emojify(el);
                        else
                            return el;
                    })}</div>
                </div>
            </li>
        );
    }
});

export default MessageItem;