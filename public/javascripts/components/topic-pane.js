import React from 'react';
import Reflux from 'reflux';
import Loader from './loader';
import TopicItem from './topic-item';
import UserTopicItem from './user-topic-item';
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
            console.log("TopicPane: gonna draw " + self.state.store.topics.length + " topics");
            topicItems = self.state.store.topics.map(t =>
                t.topic ? (<TopicItem topic={t.topic} updateDate={t.updateDate}
                               unread={t.unread} unreadCount={t.unreadCount} count={t.count}
                               selected={self.state.store.selected.topicId == t.topic.id}
                               showGroup={!self.state.store.selected.groupId}
                               key={t.topic.id}/>
                ) : (<UserTopicItem userTopic={t.userTopic} updateDate={t.updateDate}
                               unread={t.unread} unreadCount={t.unreadCount} count={t.count}
                               selected={self.state.store.selected.userId === t.userTopic.id}
                               key={'u' + t.userTopic.id}/>
                )
            );
        }
        return (
            <ul id="topic-pane">
                {topicItems}
            </ul>
        );
    }
});

export default TopicPane;