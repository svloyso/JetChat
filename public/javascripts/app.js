var GroupPane = React.createClass({
    getInitialState: function () {
        return {
            groups: global.groups,
            selectedGroup: undefined
        };
    },

    componentDidMount: function () {
        $(document).trigger("selectedGroup", this.state.selectedGroup);
    },

    onClick: function (group) {
        var selectedGroup = group ? group : null;
        this.setState({selectedGroup: selectedGroup});
        $(document).trigger("selectedGroup", selectedGroup);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.groups.map(function (group) {
            var groupClass = (self.state.selectedGroup && self.state.selectedGroup.id == group.id) ? "selected" : "";
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var allGroupsClass = !self.state.selectedGroup ? "selected" : "";

        return (
            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onClick.bind(self, undefined)}>
                    <span>All groups</span></li>
                {groupItems}
            </ul>
        );
    }
});

var SideBar = React.createClass({
    render: function () {
        return (
            <div id="side-bar">
                <div id="header">JetBrains</div>
                <GroupPane/>
            </div>
        );
    }
});

var SearchPane = React.createClass({
    render: function () {
        return (
            <div id="search-pane" className="form-inline">
                <div className="btn-group">
                    <input id="search" type="text" className="search-query"
                           placeholder="Search people, groups, topics, and messages"
                           autoComplete="off"/></div>
            </div>
        );
    }
});

var NewTopicPane = React.createClass({
    getInitialState: function () {
        return {
            enabled: false,
            selected: true
        };
    },

    onClick: function () {
        if (this.state.enabled) {
            $(document).trigger("selectedTopic", undefined);
        }
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedGroup", function (event, selectedGroup) {
            self.setState({enabled: selectedGroup !== undefined});
        });
        $(document).on("selectedTopic", function (event, selectedTopic) {
            self.setState({selected: self.state.enabled && selectedTopic === undefined});
        });
    },

    render: function () {
        var newTopicClass = ((this.state.enabled ? "enabled" : "") + " " + (this.state.selected ? "selected" : "")).trim();
        return (
            <div id="new-topic-pane">
                <a id="new-topic" className={newTopicClass}
                   onClick={this.onClick}>
                    <span id="plus"/>
                    <span>New topic</span>
                </a>
            </div>
        );
    }
});

var TopicItem = React.createClass({
    onClick: function (topic) {
        $(document).trigger("selectedTopic", topic);
    },

    render: function () {
        var self = this;
        var topicClass = self.props.selected ? "selected" : "";
        var prettyDate = $.format.prettyDate(new Date(self.props.topic.date));
        var groupRef;
        if (self.props.showGroup) {
            groupRef = <span>in #<span className="group">{self.props.topic.group.name}</span></span>
        }
        return (
            <li data-topic={self.props.topic.id} className={topicClass}
                onClick={self.onClick.bind(self, self.props.topic)}>
                <div className="text">{self.props.topic.text}</div>
                <div className="info">
                    <span className="author">{self.props.topic.user.name}</span>
                    &nbsp;
                    {groupRef}
                    &nbsp;
                        <span className="pretty date"
                              data-date={self.props.topic.date}>{prettyDate}</span>
                </div>
            </li>
        );
    }
});

var TopicPane = React.createClass({
    getInitialState: function () {
        return {
            topics: [],
            selectedTopic: undefined,
            selectedGroup: undefined
        };
    },

    componentWillMount: function () {
        var self = this;
        $(document).on("selectedGroup", function (event, selectedGroup) {
            self.setState({selectedGroup: selectedGroup});
            $.ajax({
                type: "GET",
                url: "/json/user/" + global.userId + "/topics" +
                (selectedGroup ? "/" + selectedGroup.id : ""),
                success: function (topics) {
                    self.setState({topics: topics});
                    if (topics.length > 0) {
                        $(document).trigger("selectedTopic", topics[0].topic);
                    } else {
                        $(document).trigger("selectedTopic", undefined);
                    }
                },
                fail: function (e) {
                    console.error(e);
                }
            })
        });
        $(document).on("selectedTopic", function (event, selectedTopic) {
            self.setState({selectedTopic: selectedTopic});
        });
    },

    render: function () {
        var self = this;
        var topicItems = self.state.topics.map(function (t) {
            return (
                <TopicItem topic={t.topic}
                           selected={self.state.selectedTopic &&
                           self.state.selectedTopic.id == t.topic.id}
                           showGroup={!self.state.selectedGroup}
                           key={t.topic.id}/>
            )
        });

        return (
            <ul id="topic-pane">
                {topicItems}
            </ul>
        );
    }
});

var TopicBar = React.createClass({
    render: function () {
        return (
            <div id="topic-bar">
                <SearchPane/>
                <NewTopicPane/>
                <TopicPane/>
            </div>
        );
    }
});

var MessageBar = React.createClass({
    render: function() {
        return (
            <div id="message-bar">

            </div>
        );
    }
});

var App = React.createClass({
    render: function () {
        return (
            <div>
                <SideBar/>
                <TopicBar/>
            </div>
        );
    }
});

React.render(<App/>, document.getElementById("app"));