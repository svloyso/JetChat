import React from 'react';
import Reflux from 'reflux';
import ReactDOM from 'react-dom';
import ChatStore from '../events/chat-store';

var SearchPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onChange: function () {
        this.state.store.query = ReactDOM.findDOMNode(this.refs.searchQuery).value.trim();
        if (this.state.store.query !== "") {
            ReactDOM.findDOMNode(this.refs.searchQueryCleaner).style.visibility="visible";
            alert(this.state.store.query);
        }
    },

    onClearSearch: function() {
        this.state.store.query = "";
        ReactDOM.findDOMNode(this.refs.searchQuery).value = "";
        ReactDOM.findDOMNode(this.refs.searchQueryCleaner).style.visibility="hidden";
    },

    render: function () {
        return (
            <div id="search-pane" className="form-inline">
                <div className="btn-group">
                    <input ref="searchQuery" id="search" type="text" className="search-query"
                           placeholder="Search people, groups, topics, and messages"
                           autoComplete="off" onChange={this.onChange}/>
                    <span ref="searchQueryCleaner" id="clear-search" onClick={this.onClearSearch}></span>
                </div>
            </div>
        );
    }
});

export default SearchPane;