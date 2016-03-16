import React from 'react';
import Reflux from 'reflux';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';
var $ = require('jquery');
var Switch = require('react-bootstrap-switch');

var IntegrationsPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onChange: function(integrationId, state) {
        if (state && !this.state.store.integrations.find(i => i.id == integrationId).enabled) {
            ChatActions.enableIntegration(integrationId);
            window.location.replace("/integration/" + integrationId + "/enable?id=" +
                integrationId + "&redirectUrl=" + encodeURIComponent(document.location.href));
        } else if (!state && this.state.store.integrations.find(i => i.id == integrationId).enabled) {
            $.ajax({
                context: this,
                type: "GET",
                url: "/integration/" + integrationId + "/disable",
                success: function (message) {
                    ChatActions.disableIntegration(integrationId);
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        }
    },

    render: function() {
        var self = this;
        var integrationItems = this.state.store.integrations.map(function(integration) {
            var checked = integration.enabled ? 'true' : null;
            return (
                <div className="row" key={integration.id}>
                    <div className="col-xs-8">{integration.name}</div>
                    <div className="col-xs-4"><span className="pull-right">
                        <Switch size="mini" state={checked}
                                data-integration-id={integration.id} onChange={self.onChange.bind(self, integration.id)}/>
                    </span></div>
                </div>

            );
        });
        var visible = this.state.store.selected.stateId == this.state.store.SETTINGS;
        return (
            <div id="integration-pane" style={{display: visible ? "" : "none"}}>
                <h5>Integrations</h5>
                {integrationItems}
            </div>
        );
    }
});

export default IntegrationsPane;