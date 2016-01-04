import React from 'react';
import Reflux from 'reflux';
import SearchPane from './search-pane';
import NewTopicPane from './new-topic-pane';
import TopicPane from './topic-pane';
import ChatStore from '../events/chat-store';

var TopicBar = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function () {
        return (
            <div id="topic-bar" style={{display: this.state.store.selectedUser || this.state.store.displaySettings ? "none" : ""}}>
                <SearchPane/>
                <NewTopicPane/>
                <TopicPane/>
            </div>
        );
    }
});

export default TopicBar;