# Production ready Kubectl plugins with GraalVM, Scala, and Fabric8 Kubernetes client

## Motivation

For a few months I started working in [Lightbend](https://www.lightbend.com/), and my first assignments have been around a little known yet super-powerful product called [Cloudflow](https://cloudflow.io/).

__TL; DR__ [Cloudflow](https://cloudflow.io/) brings, super quickly, you up-to-speed in developing streaming applications on Kubernetes.
Such applications are built on top of the most popular JVM's streaming engines (Akka, Spark, and Flink) and Cloudflow provides you a battery-included set of libraries, tools, and plugins for going from prototyping to production.

One of the strengths of [Cloudflow](https://cloudflow.io/) is its CLI that comes in the form of a `kubectl plugin`, with it, you can use the familiar `kubectl` CLI and the `kubectl cloudflow` command to administrate your Cloudflow applications in the cluster.

In the CLI we additionally perform an incredible amount of checks and validations that will prevent the user from deploying an application that's not configured properly, and we automate several actions that will require specific and deep knowledge to be performed manually.

At the time I joined [Lightbend](https://www.lightbend.com/), the CLI used to be a classic Go application, organically grown up to the point technical debt prevented the engineering team from easily adding functionalities and doing bug-fixes.
Additionally, the support for Cloudflow's configuration format [HOCON](https://github.com/lightbend/config) is pretty poor in Go, and it caused many open issues not easily fixable.

We, therefore, decided to consider a complete re-write of the CLI with these principles:

| Requirement | Solution |
| --- | ---|
| Programming language the team is comfortable with | Scala  |
| Native performance | GraalVM AOT |
| Industry-standard libraries | Fabric8 Kubernetes Client |
| Solid ecosystem/stack | HOCON/Scopt/Airframe logging/Pureconfig |

## Give me the code!

The demo project we refer to is directly extracted from the Cloudflow's CLI, adapted to the example in this great article: [Kubectl's plugins in Java](https://dev.to/ikwattro/write-a-kubectl-plugin-in-java-with-jbang-and-fabric8-566) that happened to be published during our re-write.

The code is available here:
https://github.com/andreaTP/blog-cloudflow-cli

For reference, the full code of the Cloudflow CLI is open source here:
https://github.com/lightbend/cloudflow/tree/master/tools

For the impatient, you can generate the binary (tested on MacOsX) by running:

```
sbt graalvm-native-image:packageBin
```

and use it:
```
export PATH=$PATH:$PWD/target/graalvm-native-image
kubectl lp --help
kubectl lp version
kubectl lp list
...
```

## The stack

In the example, we make use of a little set of great libraries that, among others, have greatly helped this project to be successful.

 - [Scopt](https://github.com/scopt/scopt) for command-line option parsing. It supports sub-commands well, which is important to give a more "kubernetes-like" experience and is a tiny lovely library.
 - [Airframe Log](https://github.com/wvlet/airframe/tree/master/airframe-log) is a super easily customizable logging library used for the "internal" logging.
 - [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client) is a rock-solid library that covers the entire Kubernetes API, without which nothing would be possible.

In the "real" Cloudflow CLI we make use of an additional library worth a mention.

 - [Pureconfig](https://pureconfig.github.io/) is used for the validation of the configurations, as well as, a typesafe interface to generate valid HOCON. The quality of the produced error messages is astonishing and gives the user a precise indication of what should be actioned.

## GraalVM

We reserve a special section to [GraalVM](https://github.com/oracle/graal), our biggest blessing and curse, and, for this post, we refer to it exclusively as the Ahead Of Time compiler.

GraalVM is the big enabler for this project letting us compile Scala code with both Scala and Java dependencies directly down to native binaries so that the user won't incur a long JVM start-up time.
It's an amazing project, but it comes with a few challenges that we faced and managed to overcome.

Unfortunately, GraalVM compilation should be [configured](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/) and this configuration can become costly and risky to maintain (especially when using "reflection heavy" libraries such as Jackson); luckily we completely own the boundaries of our CLI and we can automate the generation of such configuration by training it against a real cluster.
This boils down to run `sbt regenerateGraalVMConfig` every time we do a significant change to the CLI, this command will run an alternative `Main` of our application, intended to cover most of the possible code paths and invariants while recording the [Assisted configuration](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/#assisted-configuration-of-native-image-builds).

Another disadvantage of GraalVM is the long compilation time, currently, the CLI takes about ~5 minutes to build the native package (with the command: `sbt graalvm-native-image:packageBin`).
While this is a real time-sink in the very early days of development when you try out different configurations and dependencies, after you have a more stable build, you can still run the application on the JVM and, in particular, into the sbt interactive shell.
The ability to run, during development, on the JVM gives us the freedom to quickly try out Kubernetes commands against a cluster directly executing `sbt "run <command>"` and we can even use all the standard Java debugging tools.

The last issue we have had the pleasure to go beyond is the fact that GraalVM doesn't support cross-compilation.
In this case, we simply leverage the CI (GH Actions in this case) different runners to natively compile on the target architecture. We based our setup on this [precedent work](https://github.com/recursivecodes/simple-socket-fn-logger/blob/master/.github/workflows/simple-socket-fn-logger.yaml).

## Benefits

Not only challenges but delights have emerged from this experience as well.

Being able to directly use Scala and Java dependencies keep us in the comfort zone of a well-known ecosystem.

Having an "internal" logging system that kicks-in when necessary but normally stays hidden and gives the user the very same smooth experience as a "traditional" CLI is a big advantage.
In the case that something goes wrong we can expose all the internal stack-traces by simply adding the option `-v trace` at the end of our command.
This makes debugging of user's issues pretty straightforward by preserving the same ergonomics of the original CLI implemented in Go.

Try it with:
```
kubectl lp -v trace
```

A little nice feature is that [Jackson](https://github.com/FasterXML/jackson) provides out-of-the-box serializers for `case class`es to Json and Yaml. This way we can provide machine-readable output for any command with close to 0 effort.

Try it with:
```
kubectl lp -o json
kubectl lp -o yaml
```

Another very useful by-product is the fact that we can re-use the CLI directly from ScalaTest (importing it as a dependency) and we have full-Scala Integration tests without the need to run external processes with access to helpful error messages in case something fails.
We plan as well to port the Cloudflow operator to use the very same CRD/CR definition, immediately reducing to 0 the possibility to introduce discrepancy between the CLI and the operator itself.

Last but not least, we are especially proud of the new structure of the code.
There is a strong enough separation of concerns that makes everything easily understandable and testable in isolation without much boilerplate.
Implementing a new command is now a matter of separately implementing:

 - parsing the command from the command-line
 - defining what is going to be performed during its execution
 - rendering the result back to the user

## Conclusions

Rewriting a `kubectl` plugin from a standard Go stack to a more original approach has been challenging and rewarding.
We are really happy with the result, and we arguably improved it in several ways:

 - Improved the user experience concerning ergonomics, feedback, and validations
 - More features and functionalities have already been added easily
 - The code-base is by far more familiar and easy to grasp

The success has been reinforced by the fact that Cloudflow users migrated to the new CLI without noticing, but started to use and enjoy the new functionalities.

### References

 - kubectl plugins documentation : https://kubernetes.io/docs/tasks/extend-kubectl/kubectl-plugins/
 - fabric8 : https://github.com/fabric8io/kubernetes-client
 - graalVM : https://www.graalvm.org
