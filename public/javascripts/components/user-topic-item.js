import React from 'react';
import PrettyDate from 'pretty-date';
import ChatActions from '../events/chat-actions';
var $ = require('jquery');

var UserTopicItem = React.createClass({
    onClick: function (userTopic) {
        ChatActions.selectUserTopic(userTopic);
    },

    render: function () {
        var self = this;
        var topicClass = self.props.selected ? "selected" : "";
        var prettyDate = PrettyDate.format(new Date(self.props.updateDate));
        var unreadLabel = self.props.unreadCount ? <span className="unread"></span> : null;
        return (
            <li data-user-topic={self.props.userTopic.id} className={topicClass}
                onClick={self.onClick.bind(self, self.props.userTopic)}>
                <div className="text">{self.props.userTopic.text}</div>
                <div className="info">
                    <span className="author">{self.props.userTopic.name}</span>
                    &nbsp;&nbsp;
                        <span className="pretty date"
                              data-date={self.props.updateDate}>{prettyDate}</span>
                    {unreadLabel}
                </div>
            </li>
        );
    }
});

export default UserTopicItem;