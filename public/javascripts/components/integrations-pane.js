import React from 'react';
import Reflux from 'reflux';
import ChatStore from '../events/chat-store';

var IntegrationsPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function() {
        var integrationItems = this.state.store.integrations.map(function(integration) {
            var checked = integration.enabled ? 'true' : null;
            return (
                <div className="row" key={integration.id}>
                    <div className="col-md-8">{integration.name}</div>
                    <div className="col-md-4"><span className="pull-right">
                        <input type="checkbox" data-size="mini" defaultChecked={checked}
                               data-integration-id={integration.id}/>
                    </span></div>
                </div>

            );
        });
        return (
            <div id="integration-pane" style={{display: this.state.store.displaySettings ? "" : "none"}}>
                <h5>Integrations</h5>
                {integrationItems}
            </div>
        );
    }
});

window.setInterval(function () {
    $("input[type='checkbox']").bootstrapSwitch().off('switchChange.bootstrapSwitch')
        .on('switchChange.bootstrapSwitch', function (event, state) {
            var integrationId = $(this).attr("data-integration-id");
            if (state) {
                window.location.replace("/integration/" + integrationId + "/auth?id=" +
                    integrationId + "&redirectUrl=" + encodeURIComponent(document.location.href));
            } else {
                $.ajax({
                    context: this,
                    type: "GET",
                    url: "/integration/" + integrationId + "/disable",
                    success: function (message) {
                    },
                    fail: function (e) {
                        console.error(e);
                    }
                })
            }
        });
});

export default IntegrationsPane;