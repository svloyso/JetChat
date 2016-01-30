import React from 'react';
import PrettyDate from 'pretty-date';
import ChatActions from '../events/chat-actions';
import VisibilitySensor from 'react-visibility-sensor';

var TopicItem = React.createClass({
    onClick: function (topic) {
        ChatActions.selectTopic(topic);
    },

    onChange: function (isVisible) {
        console.log('Topic %s is now %s', this.props.topic.text, isVisible ? 'visible' : 'hidden');
    },

    render: function () {
        var self = this;
        var topicClass = self.props.selected ? "selected" : "";
        var prettyDate = PrettyDate.format(new Date(self.props.updateDate));
        var groupRef, unreadLabel;
        if (self.props.showGroup) {
            // TODO: Cannot read property 'name' of undefined
            groupRef = <span>in #<span
                className="group">{self.props.topic.group.name}</span></span>
        }
        if (self.props.unreadCount > 0) {
            unreadLabel = <span className="unread">{self.props.count > 1 ? self.props.unreadCount : ''}</span>
        }
        return (
            <li data-topic={self.props.topic.id} className={topicClass}
                onClick={self.onClick.bind(self, self.props.topic)}>
                <VisibilitySensor onChange={self.onChange.bind(self)} />
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