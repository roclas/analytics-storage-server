**What does this server do?**

It basically creates a pull of threads that is capable of receiving many concurrent http requests in JSON format, and store them (so that they can be processed and analyced).

It has been built for demo purposes, so it stores them in a file system. Each actor writes to a diferent file.
Akka HTTP is the perfect fit for this type of problem. It allows us to acomplish a very high throughput and a very high level of asynchrony very easily.

This server would be intended to be part of a mini omniture, piwik or google analytics system, where by adding javascript to our sites, many concurrent Ajax requests (with information about our clients' usage) will be sent against our server, that will be able to process and store them all, up to a very high number of requests per second.


**How to build it:**

sbt assembly



**How to run it:**

*java -jar akka-http-server-assembly-0.1.0-SNAPSHOT.jar*

The server will be running on port 9090 by default.

If you want to run it with a different configuration (copy the project's application.conf and use it like this):

*scala akka-http-server-assembly-0.1.0-SNAPSHOT.jar -Dconfig.file=application.conf*

or with sbt, symply by doing this:

*sbt run*

**How to test it**

With this project, there is a nodeJS client (that produces a stream of simulated javascript events that will go against our server). If you have nodeJS installed, you can run it like this:

*node client_node.js*

Otherwise, you can do something like this: 

curl -X PUT -H "Content-Type: application/json" -d '{"key1":"value"}' localhost:9090/analytics

**How it works**

The code is simple; it leverages on Akka Http and Scala, which make asynchrony very easy and natural.
Http requests will be routed by Akka Http and sent to an actor system which will fairly distribute the load of work among a pool of worker threads, obtaining a high throughput.

The two most important files that contain all the logic are:

*Server.scala*: which deals with concurrency and actors (you shouldn't need to modify anything in it)

*Writer.scala*: which is the worker class (actor) that actually writes the data. It saves it to the filesystem ( one actor per file to obtain maximum concurrency ) but it can be changed to write it to Cassandra, to Akka Streams, to Kafka, or to another Database (just to name ideas). It would also be left in the filesystem (so that it can be processed by Spark or Hadoop or similar in the future).

If you want to modify the configuration, just get the *application.conf* file, and modify the parameters in it. You can use an external application.conf file like this:

*scala akka-http-server-assembly-0.1.0-SNAPSHOT.jar -Dconfig.file=application.conf*
