import React from 'react';
import Reflux from 'reflux';
import ReactDOM from 'react-dom';
import ChatStore from '../events/chat-store';

var SearchPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    setQuery: function() {
        this.state.store.query = ReactDOM.findDOMNode(this.refs.searchQuery).value.trim();
    },

    onKeyPress: function (event) {
        if (event.which == 13) {
            this.setQuery();
        }
    },

    render: function () {
        return (
            <div id="search-pane" className="form-inline">
                <div className="btn-group">
                    <input ref="searchQuery" id="search" type="text" className="search-query"
                           placeholder="Search people, groups, topics, and messages"
                           autoComplete="off" onKeyPress={this.onKeyPress}/></div>
            </div>
        );
    }
});

export default SearchPane;