import React from 'react';
import PrettyDate from 'pretty-date';
import ChatActions from '../events/chat-actions';
import VisibilitySensor from 'react-visibility-sensor';
var $ = require('jquery');

var TopicItem = React.createClass({
    onClick: function (topic) {
        ChatActions.selectTopic(topic.id);
    },

    onChange: function (isVisible) {
        if (isVisible && this.props.unread && $(document.body).hasClass("visible")) {
            ChatActions.markTopicAsRead(this.props.topic);
        }
    },

    render: function () {
        var self = this;
        var topicClass = self.props.selected ? "selected" : "";
        var prettyDate = PrettyDate.format(new Date(self.props.updateDate));
        var groupRef, unreadLabel;
        if (this.props.groupName) {
            groupRef = <span>in #<span className="group">{self.props.groupName}</span></span>
        }
        if (self.props.unread || self.props.unreadCount > 0) {
            var totalUnreadCount = (self.props.unread ? 1 : 0) + self.props.unreadCount;
            unreadLabel = <span className="unread">{totalUnreadCount > 1 ? totalUnreadCount : ''}</span>
        }
        return (
            <li data-topic={self.props.topic.id} className={topicClass}
                onClick={self.onClick.bind(self, self.props.topic)}>
                <VisibilitySensor onChange={self.onChange} />
                <div className="text">{self.props.topic.text}</div>
                <div className="info">
                    <span className="author">{self.props.topic.user.name}</span>
                    &nbsp;
                    {groupRef}
                    &nbsp;
                        <span className="pretty date"
                              data-date={self.props.updateDate}>{prettyDate}</span>
                    {unreadLabel}
                </div>
            </li>
        );
    }
});

export default TopicItem;