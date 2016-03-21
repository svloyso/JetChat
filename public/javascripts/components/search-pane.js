import React from 'react';
import Reflux from 'reflux';
import ReactDOM from 'react-dom';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';

var SearchPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    getInitialState: function() {
        if (this.state) {
            return this.state;
        } else {
            return {
                value: '',
                timeoutRef: null
            };
        }
    },

    onChange: function (event) {
        var self = this;
        this.setState({value: event.target.value});
        if (this.state.timeoutRef) clearTimeout(this.state.timeoutRef);
        this.state.timeoutRef = setTimeout(
            function () { ChatActions.alertQuery(self.state.value); },
            1000
        );
    },

    onBlur: function () {
        if (this.state.timeoutRef) clearTimeout(this.state.timeoutRef);
        ChatActions.alertQuery(this.state.value);
    },

    onClearSearch: function () {
        if (this.state.timeoutRef) clearTimeout(this.state.timeoutRef);
        this.state.value = '';
        ChatActions.alertQuery(undefined);
    },

    render: function () {
        return (
            <div id="search-pane" className="form-inline">
                <div className="btn-group">
                    <input id="search" type="text" className="search-query"
                           placeholder="Search people, groups, topics, and messages"
                           autoComplete="off" value={this.state.value}
                           onChange={this.onChange} onBlur={this.onBlur} />
                    <span style={{visibility: this.state.value !== "" ? "visible" : "hidden"}}
                          id="clear-search" onClick={this.onClearSearch}/>
                </div>
            </div>
        );
    }
});

export default SearchPane;