import React from 'react';
var prettydate = require("pretty-date");
import ChatActions from '../events/chat-actions';

var IntegrationTopicItem = React.createClass({
    onClick: function () {
        ChatActions.selectIntegrationTopic(this.props.topicId);
    },

    render: function () {
        var prettyDate = prettydate.format(new Date(this.props.updateDate));
        var groupRef;
        if (this.props.groupName) {
            groupRef = <span>in #<span className="group">{this.props.groupName}</span></span>
        }
        return (
            <li data-topic={this.props.topicId} className={this.props.selected ? "selected" : ""}
                onClick={this.onClick.bind(this)}>
                <div className="text">{this.props.text}</div>
                <div className="info">
                    <span className="author">{this.props.userName}</span>
                    &nbsp;
                    {groupRef}
                    &nbsp;
                    <span className="pretty date" data-date={this.props.updateDate}>{prettyDate}</span>
                </div>
            </li>
        );
    }
});

export default IntegrationTopicItem;