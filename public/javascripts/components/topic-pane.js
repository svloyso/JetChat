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
                return (
                    <IntegrationTopicItem integration={integration} group={group} topic={t.topic}
                               selected={self.state.store.selectedIntegrationTopic &&
                           self.state.store.selectedIntegrationTopic.id == t.topic.id}
                               showGroup={!self.state.store.selectedIntegrationGroup}
                               key={t.topic.id}/>
                )
            });
        } else if (self.state.store.topics) {
            topicItems = self.state.store.topics.map(function (t) {
                return (
                    <TopicItem topic={t.topic}
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