import React from 'react';
import Reflux from 'reflux';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';
import classNames from 'classnames';
import NewGroupButton from './new-group-button';

var GroupPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onGroupClick: function (group) {
        ChatActions.selectGroup(group.id);
    },

    onUserClick: function (user) {
        ChatActions.selectUser(user);
    },

    onIntegrationClick: function (integration) {
        ChatActions.selectIntegration(integration);
    },

    onIntegrationGroupClick: function (group) {
        ChatActions.selectIntegrationGroup(this.state.store.integrations.find(i => i.id == group.integrationId), group);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.store.groups.map(function (group) {
            var groupClass = classNames({
                    ['selected']: self.state.store.selected.groupId == group.id,
                    ['unread']: group.unreadCount > 0
                }
            );
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onGroupClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var userItems = self.state.store.users.map(function (user) {
            var userClass = classNames({
                    ['selected']: self.state.store.selectedUser && self.state.store.selectedUser.id == user.id,
                    ['unread']: user.unreadCount > 0
                }
            );
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
            var integrationGroupItems = integration.enabled ? group.data.map(group => {
                var groupClass = classNames({
                        ['selected']: self.state.store.selectedIntegrationGroup &&
                        self.state.store.selectedIntegrationGroup.integrationGroupId == group.integrationGroupId
                    }
                );

                return (
                    <li data-group={group.id} className={groupClass}
                        onClick={self.onIntegrationGroupClick.bind(self, group)} key={group.integrationGroupId}>
                        <span className="group-header">#</span>
                        <span>{group.name}</span>
                    </li>
                )
            }) : [];
            var integrationClass = (self.state.store.selectedIntegration &&
                !self.state.store.selectedIntegrationGroup &&
                self.state.store.selectedIntegration.id == group.key) ? "selected" : "";
            return integration.enabled ? (
                <ul className="integration-groups" key={integration.id}>
                    <li data-integration={integration.id} onClick={self.onIntegrationClick.bind(self, integration)} className={integrationClass}>
                        <span className="integration-name">{integration.name}</span></li>
                    {integrationGroupItems}
                </ul>
            ) : null;
        });
        var store = self.state.store;
        var allGroupsClass = classNames({ ['selected']: !self.state.store.selected.groupId &&
        !store.selectedUser &&
        !store.selectedIntegration &&
        !store.selectedIntegrationGroup &&
        store.selected.stateId !== store.SETTINGS });

        return (
            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onGroupClick.bind(self, undefined)}>
                    <span>My chats</span></li>
                {groupItems}
                <NewGroupButton/>
                {integrationItems}
                {userItems}
            </ul>
        );
    }
});

export default GroupPane;