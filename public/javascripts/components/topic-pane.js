import React from 'react';
import Reflux from 'reflux';
import TopicItem from './topic-item';
import IntegrationTopicItem from './integration-topic-item';
import ChatStore from '../events/chat-store';

var TopicPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function () {
        var self = this;
        var topicItems = [];
        if (self.state.store.integrationTopics) {
            var group = self.state.store.selectedIntegrationGroup ? self.state.store.selectedIntegrationGroup : undefined;
            topicItems = self.state.store.integrationTopics.map(function (t) {
                var integration = self.state.store.integrations.find(i => i.id == t.topic.integrationId);
                var selectedIntegrationTopicGroupId = self.state.store.selectedIntegrationTopic ? (self.state.store.selectedIntegrationTopic.integrationGroupId ? self.state.store.selectedIntegrationTopic.integrationGroupId : self.state.store.selectedIntegrationTopic.group.id) : undefined;
                var selectedIntegrationTopicId = self.state.store.selectedIntegrationTopic ? (self.state.store.selectedIntegrationTopic.integrationTopicId ? self.state.store.selectedIntegrationTopic.integrationTopicId : self.state.store.selectedIntegrationTopic.id) : undefined;
                return (
                    <IntegrationTopicItem integration={integration} group={group} topic={t.topic}
                               selected={self.state.store.selectedIntegrationTopic &&
                               selectedIntegrationTopicId == t.topic.id && selectedIntegrationTopicGroupId == t.topic.group.id }
                               showGroup={!self.state.store.selectedIntegrationGroup}
                               key={t.topic.id}/>
                )
            });
        } else if (self.state.store.topics) {
            topicItems = self.state.store.topics.map(function (t) {
                return (
                    <TopicItem topic={t.topic} unreadCount={t.unreadCount} count={t.count}
                               selected={self.state.store.selectedTopic &&
                           self.state.store.selectedTopic.id == t.topic.id}
                               showGroup={!self.state.store.selectedGroup}
                               key={t.topic.id}/>
                )
            });
        }
        return (
            <ul id="topic-pane">
                {topicItems}
            </ul>
        );
    }
});

export default TopicPane;