import React from 'react';

const INITIAL_TICKS = 100;

function Particle(x, y, radius, color) {
    this.radius = radius;
    this.x = x;
    this.y = y;
    this.color = color;

    this.decay = 0.01;
    this.life = 1;

    var self = this;

    this.step = function() {
        self.life -= self.decay;
    };

    this.isAlive = function () {
        return self.life >= 0;
    };

    this.draw = function (ctx) {
        const alpha = self.life >= 0 ? self.life : 0;
        ctx.fillStyle = "rgba(" + self.color.r + ", " + self.color.g + ", " + self.color.b +", " + alpha +")";

        ctx.beginPath();
        ctx.arc(self.x + self.radius, self.y + self.radius, self.radius, 0, Math.PI * 2);
        ctx.fill();
    };

    return this;
}

function LoaderCore(containerNode, props) {
    this.isRunning = false;

    this.props = Object.assign({}, LoaderCore.defaultProps, props);

    var self = this;

    this.setCanvasSize = function () {
        const pixelRatio = LoaderCore.getPixelRatio();
        const canvasSize = self.props.size * pixelRatio;

        self.canvas.width = canvasSize;
        self.canvas.height = canvasSize;

        //Fixate canvas physical size to avoid real size scaling
        self.canvas.style.width = `${self.props.size}px`;
        self.canvas.style.height = `${self.props.size}px`;

        self.ctx = self.canvas.getContext('2d');

        //Scale on HDPI displays
        if (pixelRatio > 1) {
            self.ctx.scale(pixelRatio, pixelRatio);
        }
    };

    this.initializeLoader = function () {
        self.setCanvasSize();

        self.height = self.props.size;
        self.width = self.props.size;

        self.particles = [];

        //Configuration
        self.baseSpeed = 1.0;
        self.colorIndex = 0;
        self.maxRadius = 10;
        self.minRadius = 6;
        self.colorChangeTick = 40;

        //State
        self.x = 0;
        self.y = 0;
        self.radius = 8;
        self.hSpeed = 1.5;
        self.vSpeed = 0.5;
        self.radiusSpeed = 0.05;
        self.tick = 0;

        self.prepareInitialState(INITIAL_TICKS);
        self.isRunning = true;
        self.loop();
    };

    this.prepareInitialState = function (ticks) {
        for (let i = 0; i < ticks; i++) {
            self.step();
        }
    };

    this.handleLimits = function (coord, radius, speed, limit) {
        const randomizedSpeedChange = Math.random(self.baseSpeed) - self.baseSpeed / 2;

        if (coord + (radius * 2) + self.baseSpeed >= limit) {
            return -(self.baseSpeed + randomizedSpeedChange);
        } else if (coord <= self.baseSpeed) {
            return self.baseSpeed + randomizedSpeedChange;
        }
        return speed;
    };

    this.calculateNextCoordinates = function () {
        self.x += self.hSpeed;
        self.y += self.vSpeed;

        self.hSpeed = self.handleLimits(self.x, self.radius, self.hSpeed, self.width);
        self.vSpeed = self.handleLimits(self.y, self.radius, self.vSpeed, self.height);
    };

    this.calculateNextRadius = function () {
        self.radius += self.radiusSpeed;

        if (self.radius > self.maxRadius || self.radius < self.minRadius) {
            self.radiusSpeed = -self.radiusSpeed;
        }
    };

    this.getNextColor = function () {
        const colors = self.props.colors;

        const currentColor = colors[self.colorIndex];
        const nextColor = colors[self.colorIndex + 1] || colors[0];

        return LoaderCore.calculateGradient(currentColor, nextColor, self.tick / self.colorChangeTick);
    };

    this.nextTick = function () {
        self.tick++;

        if (self.tick > self.colorChangeTick) {
            self.tick = 0;
            self.colorIndex++;
            if (self.colorIndex > self.props.colors.length - 1) {
                self.colorIndex = 0;
            }
        }
    };

    this.step = function () {
        self.nextTick();
        self.calculateNextCoordinates();
        self.calculateNextRadius();
        self.particles.forEach(particle => particle.step());

        self.particles.push(new Particle(
            self.x,
            self.y,
            self.radius,
            self.getNextColor()
        ));
    };

    this.removeDeadParticles = function () {
        self.particles = self.particles.filter(function(it) { return it.isAlive(); });
    };

    this.draw = function () {
        self.ctx.clearRect(0, 0, self.width, self.height);
        self.removeDeadParticles();
        self.particles.forEach(function(particle) { return particle.draw(self.ctx); });
    };

    this.loop = function () {
        self.step();
        self.draw();
        if (self.isRunning) {
            window.requestAnimationFrame(function() { self.loop() });
        }
    };

    this.destroy = function () {
        self.isRunning = false;
    };

    this.renderInNodeAndStart = function (node) {
        self.canvas = document.createElement('canvas');
        self.canvas.classList.add('loader__canvas');

        const textNode = document.createElement('div');
        textNode.classList.add('loader__text');

        textNode.textContent = self.props.message ? self.props.message : '';

        node.appendChild(self.canvas);
        node.appendChild(textNode);

        self.initializeLoader();

        return node;
    };

    self.renderInNodeAndStart(containerNode);

    return this;
}

LoaderCore.getPixelRatio = function () {
    return 'devicePixelRatio' in window ? window.devicePixelRatio : 1;
};

LoaderCore.defaultProps = {
    size: 64,
    colors: [
        {r: 215, g: 60, b: 234},  //#D73CEA
        {r: 145, g: 53, b: 224},  //#9135E0
        {r: 88, g: 72, b: 224},   //#5848F4
        {r: 37, g: 183, b: 255},  //#25B7FF
        {r: 89, g: 189, b: 0},    //#59BD00
        {r: 251, g: 172, b: 2},   //#FBAC02
        {r: 227, g: 37, b: 129}   //#E32581
    ]
};

LoaderCore.calculateGradient = function (startColor, stopColor, position) {
    const calculateChannelValue = function (a, b) {
        return a + Math.round((b - a) * position)
    };

    return {
        r: calculateChannelValue(startColor.r, stopColor.r),
        g: calculateChannelValue(startColor.g, stopColor.g),
        b: calculateChannelValue(startColor.b, stopColor.b)
    };
};

// var loader = new LoaderCore(document.getElementById('loader'), { message: 'Loading...' });

var Loader = React.createClass({
    componentDidMount: function() {
        this.loader = new LoaderCore(this.refs.loaderContainer, this.props);
    },

    componentWillUnmount: function() {
        this.loader.destroy();
    },


    render: function() {
        return (
            <div id="loader" ref="loaderContainer">
            </div>
        );
    }
});

export default Loader;