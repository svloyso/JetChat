import React from 'react';
var prettydate = require("pretty-date");
import ChatActions from '../events/chat-actions';

var TopicItem = React.createClass({
    onClick: function (topic) {
        ChatActions.selectTopic(topic);
    },

    render: function () {
        var self = this;
        var topicClass = self.props.selected ? "selected" : "";
        var prettyDate = prettydate.format(new Date(self.props.topic.date));
        var groupRef;
        if (self.props.showGroup) {
            // TODO: Cannot read property 'name' of undefined
            groupRef = <span>in #<span
                className="group">{self.props.topic.group.name}</span></span>
        }
        return (
            <li data-topic={self.props.topic.id} className={topicClass}
                onClick={self.onClick.bind(self, self.props.topic)}>
                <div className="text">{self.props.topic.text}</div>
                <div className="info">
                    <span className="author">{self.props.topic.user.name}</span>
                    &nbsp;
                    {groupRef}
                    &nbsp;
                        <span className="pretty date"
                              data-date={self.props.topic.date}>{prettyDate}</span>
                </div>
            </li>
        );
    }
});

export default TopicItem;