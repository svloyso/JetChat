import React from 'react';
import Reflux from 'reflux';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';
import NewGroupButton from './new-group-button';
import SettingsButton from './settings-button'

var GroupPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onGroupClick: function (group) {
        ChatActions.selectGroup(group);
    },

    onUserClick: function (user) {
        ChatActions.selectUser(user);
    },

    onIntegrationClick: function (integration) {
        ChatActions.selectIntegration(integration);
    },

    onIntegrationGroupClick: function (group) {
        ChatActions.selectIntegrationGroup(group);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.store.groups.map(function (group) {
            var groupClass = (self.state.store.selectedGroup && self.state.store.selectedGroup.id == group.id) ? "selected" : "";
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onGroupClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var userItems = self.state.store.users.map(function (user) {
            var userClass = (self.state.store.selectedUser && self.state.store.selectedUser.id == user.id) ? "selected" : "";
            return (
                <li data-user={user.id} className={userClass}
                    onClick={self.onUserClick.bind(self, user)} key={user.id}>
                    <span>{user.name}</span>
                </li>
            );
        });

        var groupsByIntegrationId = self.state.store.integrationGroups.group(g => g.integrationId);
        var integrationItems = groupsByIntegrationId.map((group) => {
            var integration = self.state.store.integrations.find(i => i.id == group.key);
            var integrationGroupItems = group.data.map(group => {
                var groupClass = "";
                return (
                    <li data-group={group.id} className={groupClass}
                        onClick={self.onIntegrationGroupClick.bind(self, group)} key={group.integrationGroupId}>
                        <span className="group-header">#</span>
                        <span>{group.name}</span>
                    </li>
                )
            });
            return (
                <ul className="integration-groups" key={integration.id}>
                    <li data-integration={integration.id} onClick={self.onIntegrationClick.bind(self, integration)} >
                        <span className="integration-name">{integration.name}</span></li>
                    {integrationGroupItems}
                </ul>
            );
        });
        var allGroupsClass = (!self.state.store.selectedGroup && !self.state.store.selectedUser && !self.state.store.displaySettings) ? "selected" : "";

        return (
            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onGroupClick.bind(self, undefined)}>
                    <span>All groups</span></li>
                {groupItems}
                <NewGroupButton/>
                {integrationItems}
                <SettingsButton selected={self.state.store.displaySettings}/>
                {userItems}
            </ul>
        );
    }
});

export default GroupPane;