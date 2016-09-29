api/app/actors/EmailActor.scala

  - use dependency injected config

java.lang.ExceptionInInitializerError: null
	at com.bryzek.dependency.actors.EmailActor$$anonfun$receive$1$$anonfun$applyOrElse$1.apply$mcV$sp(EmailActor.scala:53) ~[classes/:na]
	at com.bryzek.dependency.actors.EmailActor$$anonfun$receive$1$$anonfun$applyOrElse$1.apply(EmailActor.scala:50) ~[classes/:na]
	at com.bryzek.dependency.actors.EmailActor$$anonfun$receive$1$$anonfun$applyOrElse$1.apply(EmailActor.scala:50) ~[classes/:na]
	at com.bryzek.dependency.actors.Util$class.withErrorHandler(Util.scala:16) [classes/:na]
	at com.bryzek.dependency.actors.EmailActor.withErrorHandler(EmailActor.scala:29) [classes/:na]
	at com.bryzek.dependency.actors.Util$class.withVerboseErrorHandler(Util.scala:30) [classes/:na]
	at com.bryzek.dependency.actors.EmailActor.withVerboseErrorHandler(EmailActor.scala:29) [classes/:na]
	at com.bryzek.dependency.actors.EmailActor$$anonfun$receive$1.applyOrElse(EmailActor.scala:50) [classes/:na]
	at akka.actor.Actor$class.aroundReceive(Actor.scala:467) [akka-actor_2.11-2.3.13.jar:na]
	at com.bryzek.dependency.actors.EmailActor.aroundReceive(EmailActor.scala:29) [classes/:na]

scalatestplus vs scalatestplus_2.11

when adding a project from github, allow selection of owner
organization

Handle http 500 from github
