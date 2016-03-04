import React from 'react';

var SearchPane = React.createClass({
    onKeyPress: function (event) {
        if (event.which == 13) {
            alert("I see the end og typing.");
        }
    },

    render: function () {
        return (
            <div id="search-pane" className="form-inline">
                <div className="btn-group">
                    <input id="search" type="text" className="search-query"
                           placeholder="Search people, groups, topics, and messages"
                           autoComplete="off" onKeyPress={this.onKeyPress}/></div>
            </div>
        );
    }
});

export default SearchPane;