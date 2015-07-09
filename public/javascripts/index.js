$(document).ready(function () {
    if (window.WebSocket) {
        var webSocket = new WebSocket(webSocketUrl);
        webSocket.onmessage = function (message) {
            console.dir(message);
        };
        webSocket.onopen = function() {
            setInterval(function () {
                webSocket.send(JSON.stringify("Tick"));
            }, 10000);
        };
        webSocket.onclose = function(event) {
            console.error("Websocket is closed", event)
        };
        webSocket.onerror = function (error) {
            console.error("Websocket error", error);
        };
    }
    console.log("Document is ready!");
});