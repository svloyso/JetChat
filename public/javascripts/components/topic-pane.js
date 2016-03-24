import React from 'react';
import TopicItem from './topic-item';
import UserTopicItem from './user-topic-item';
import IntegrationTopicItem from './integration-topic-item';
import ChatStore from '../events/chat-store';

var TopicPane = React.createClass({
    integrationItem: function (t) {
        return (<IntegrationTopicItem
            text={t.topic.text}
            topicId={t.topic.id + this.props.separator + t.topic.group.id}
            selected={t.topic.id + this.props.separator + t.topic.group.id === this.props.selectedTopicId}
            groupName={this.props.selectedGroupId ? undefined : t.topic.group.name}
            updateDate={t.topic.date}
            userName={t.topic.integrationUser.name}
        />);
    },

    simpleItem: function (t) {
        return (<TopicItem
            count={t.count}
            groupName={this.props.selectedGroupId ? undefined : t.topic.group.name}
            key={t.topic.id}
            selected={t.topic.id === this.props.selectedTopicId}
            topic={t.topic}
            unread={t.unread}
            unreadCount={t.unreadCount}
            updateDate={t.updateDate}
        />);
    },

    userItem: function (t) {
        return (<UserTopicItem
            count={t.count}
            key={'u' + t.userTopic.id}
            selected={this.props.userPrefix + this.props.separator + t.userTopic.id === this.props.selectedTopicId}
            unread={t.unread}
            unreadCount={t.unreadCount}
            userTopic={t.userTopic}
            updateDate={t.updateDate}
        />);
    },

    simpleTopics: function () {
        return this.props.topics.map(t => t.topic ? this.simpleItem(t) : this.userItem(t));
    },

    integrationTopics: function () {
        return this.props.topics.map(t => this.integrationItem(t));
    },

    render: function () {
        return (
            <ul id="topic-pane">
                {this.props.stateIsIntegration ? this.integrationTopics() : this.simpleTopics()}
            </ul>
        );
    }
});

export default TopicPane;