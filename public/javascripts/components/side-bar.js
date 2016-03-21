import React from 'react';
import GroupPane from './group-pane';
import {Dropdown, MenuItem} from 'react-bootstrap';
import ChatActions from '../events/chat-actions';

var SideBar = React.createClass({
    onHome: function(e) {
        ChatActions.selectGroup();
    },

    onPreferences: function(e) {
        ChatActions.showIntegrations();
    },

    onLogout: function(e) {
        window.location.replace("/logout")
    },

    render: function () {
        return (
            <div id="side-bar">
                <div id="header"><a href="#" className="title" onClick={this.onHome}>JetChat</a>
                    <Dropdown id="settings-dropdown">
                        <a href="#" bsRole="toggle">
                            <span id="gear" />
                        </a>
                        <Dropdown.Menu bsRole="menu">
                            <MenuItem eventKey="1" onSelect={this.onPreferences}>Preferences</MenuItem>
                            <MenuItem eventKey="2" onSelect={this.onLogout}>Sign out</MenuItem>
                        </Dropdown.Menu>
                    </Dropdown>
                </div>
                <GroupPane
                    groups={this.props.groups}
                    integrations={this.props.integrations}
                    integrationGroups={this.props.integrationGroups}
                    selectedGroupId={this.props.selectedGroupId}
                    selectedIntegration={this.props.selectedIntegration}
                    selectedMyChats={this.props.selectedMyChats}
                    users={this.props.users}
                />
            </div>
        );
    }
});

export default SideBar;