Kemohno
=======
_Slack Emojification on the JVM._

Kemohno is a reimplementation of [jimdaguy/emohno](https://github.com/JimDaGuy/emojispell)
in Kotlin, taking advantage of its static typing and DSL-friendliness to make development 
a bit easier.

### Features

- Emojiposting attribution via user mimicking
- Duplicate emoji minimization
- special token passthrough
- Slack message action support

This repo is set up to be deployed on Heroku (Procfile + gradle jars) and can also work on OpenShift using Java S2I to
run fat jars courtesy of [johnrengelman/shadow](https://github.com/johnrengelman/shadow).

### Running locally

```shell
./gradlew stage
./build/install/kemohno/bin/kemohno
```

### Building and running a fat-jar (s2i)
```shell
./gradlew shadowJar
java -jar build/libs/kemohno-0.0.1-all.jar
```
