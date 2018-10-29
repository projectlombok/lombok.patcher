#lombok.patcher

Lombok Patcher gives you the ability to live-rewrite classes as a JVM runs, either by loading as an agent during JVM bootup or by injecting the agent 'live' during execution.

To make this easier than fiddling with classes directly, Lombok Patcher offers a few 'patch scripts' to do common tasks, such as wrap your own code around any method call,
replace methods entirely with your own, or add fields.

lombok.patcher also includes support for getting around the Eclipse OSGi container's classloader separation.

An example can be found in [Project Lombok's eclipse agent code](https://github.com/rzwitserloot/lombok/tree/master/src/eclipseAgent/lombok/eclipse/agent/)