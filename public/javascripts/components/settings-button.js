import React from 'react';
import ChatActions from '../events/chat-actions';

var SettingsButton = React.createClass({
    onClick: function () {
        ChatActions.showIntegrations();
    },

    render: function () {
        var self = this;
        var className = self.props.selected ? "selected" : "";
        return (
            <li id="integrations-button" onClick={self.onClick.bind(self, undefined)} className={className}>
                Settings</li>
        );
    }
});

export default SettingsButton;