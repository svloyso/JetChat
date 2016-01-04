import React from 'react';
import Reflux from 'reflux';
import TopicItem from './topic-item';
import ChatStore from '../events/chat-store';

var TopicPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function () {
        var self = this;
        var topicItems = self.state.store.topics.map(function (t) {
            return (
                <TopicItem topic={t.topic}
                           selected={self.state.store.selectedTopic &&
                           self.state.store.selectedTopic.id == t.topic.id}
                           showGroup={!self.state.store.selectedGroup}
                           key={t.topic.id}/>
            )
        });

        return (
            <ul id="topic-pane">
                {topicItems}
            </ul>
        );
    }
});

export default TopicPane;