import React from 'react';
import ReactEmoji from 'react-emoji';
import ReactAutolink from 'react-autolink';
var prettydate = require("pretty-date");

var IntegrationMessageItem = React.createClass({
    mixins: [
        ReactEmoji, ReactAutolink
    ],

    render: function () {
        var self = this;
        var className = ("clearfix" + " " + (self.props.topic ? "topic" : "") + " " + (self.props.sameUser ? "same-user" : "")).trim();
        var avatar;
        var info;
        if (!self.props.sameUser) {
            avatar = <img className="img avatar pull-left" src={self.props.message.integrationUser.avatar}/>;
            var prettyDate = prettydate.format(new Date(self.props.message.date));
            info = <div className="info">
                <span className="author">{self.props.message.integrationUser.name}</span>
                &nbsp;
                <span className="pretty date"
                      data-date={self.props.message.date}>{prettyDate}</span>
            </div>;
        }
        return (
            <li className={className} data-user={self.props.message.integrationUser.id}>
                {avatar}
                <div className="details">
                    {info}
                    <div className="text">{this.autolink(self.props.message.text, { className: "imagify"}).map(function (el) {
                        if (typeof el === "string")
                            return self.emojify(el);
                        else
                            return el;
                    })}</div>
                </div>
            </li>
        );
    }
});

export default IntegrationMessageItem;