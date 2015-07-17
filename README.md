jetchat
=======

This repository SSH URL: ssh://git-hosting.labs.intellij.net/jetchat.git
This repository HTTPS URL: https://git-hosting.labs.intellij.net/jetchat.git

Develop with pleasure(R)

```bash
sbt clean

sbt docker:publishLocal

docker run -p 9000:9000 jetchat:1.0-SNAPSHOT -Dhub.secret=... -Dhub.clientId=... -Dhub.url=... -Ddb.default.url=... -Ddb.default.user=... -Ddb.default.password=... -DapplyEvolutions.default=true
```